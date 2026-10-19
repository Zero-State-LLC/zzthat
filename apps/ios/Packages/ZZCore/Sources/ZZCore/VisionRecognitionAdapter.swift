#if canImport(Vision)
import CoreGraphics
import CoreImage
import Foundation
import ImageIO
import Vision

/// The iOS baseline adapter behind `RecognitionAdapter` (tasks.md T024;
/// runtime.md, Read pipeline step 5): Vision `VNRecognizeTextRequest` at the
/// accurate level, language correction off, language en-US, on the device.
/// It sends nothing and writes nothing: the upright image and the crop live
/// in memory for the current attempt only. Vision objects stop here.
///
/// Vision has no zz glyph detector, so marker detection runs the same text
/// request over the upright image, with character boxes from
/// `VNDetectTextRectanglesRequest`, and keeps only the terminal zz marks:
/// their quads, their line's baseline angle, and whether other marks sit next
/// to them. Every other string from that pass is dropped inside
/// `detectMarkers` and never reaches pairing, the decoder, or the result.
/// Payload text comes only from the second request, on the selected pair's
/// interior crop. A mark Vision cannot place fails the frame closed.
public struct VisionRecognitionAdapter: RecognitionAdapter {
    public let engineId = "apple-vision-recognize-text"
    public var engineVersion: String { "revision-\(VNRecognizeTextRequest.currentRevision)-accurate-en-US" }

    public init() {}

    public func prepare(imageData: Data) async throws -> CaptureImage {
        try await Self.run { try Self.prepareNow(imageData) }
    }

    public func detectMarkers(in image: CaptureImage) async throws -> [MarkerObservation] {
        try await Self.run { try Self.detectNow(image) }
    }

    public func readPayload(in image: CaptureImage, roi: ROIGeometry) async throws -> [PayloadHypothesis] {
        try await Self.run { try Self.readNow(image, roi: roi) }
    }

    /// Vision blocks its calling thread while it works. It runs on one
    /// serial queue of its own, never on the Swift concurrency pool, where
    /// several blocked requests can starve the pool and deadlock.
    private static let queue = DispatchQueue(label: "com.zer0state.zzthat.vision-adapter", qos: .userInitiated)

    static func run<T: Sendable>(_ work: @escaping @Sendable () throws -> T) async throws -> T {
        try Task.checkCancellation()
        return try await withCheckedThrowingContinuation { continuation in
            queue.async { continuation.resume(with: Result { try work() }) }
        }
    }

    static func prepareNow(_ imageData: Data) throws -> CaptureImage {
        guard let source = CGImageSourceCreateWithData(imageData as CFData, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
            throw RecognitionFailure.unreadableImage
        }
        let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]
        let raw = properties?[kCGImagePropertyOrientation] as? UInt32
        let orientation = raw.flatMap(CGImagePropertyOrientation.init(rawValue:)) ?? .up
        let upright = try Self.upright(image, orientation: orientation)
        return CaptureImage(pixelWidth: upright.width, pixelHeight: upright.height, backing: upright)
    }

    static func detectNow(_ image: CaptureImage) throws -> [MarkerObservation] {
        let cgImage = try Self.cgImage(image)
        let (observations, rectangles) = try Self.recognizeWithCharacters(cgImage)
        let width = Double(image.pixelWidth), height = Double(image.pixelHeight)
        var markers: [MarkerObservation] = []
        for observation in observations {
            guard let top = observation.topCandidates(1).first else { continue }
            let scalars = Array(top.string.unicodeScalars).map(Scanner.rule1)
            let tokens = Scanner.tokenize(scalars).filter { !$0.isEmpty }
            guard tokens.contains(where: \.isMarker) else { continue }
            let characters = Self.characterBoxes(for: observation, scalars: scalars, rectangles: rectangles)
            let angle = Self.angleDegrees(observation, characters: characters, width: width, height: height)
            for (index, token) in tokens.enumerated() where token.isMarker {
                // The zz of a zz@ opener is the mark; what follows the @ is payload.
                let markEnd = token.isOpener ? token.coreStart + 2 : token.coreEnd
                guard let quad = Self.quad(token.coreStart..<markEnd, scalars: scalars, characters: characters,
                                           text: top) else {
                    // A mark the engine cannot place could hide a competing
                    // pair, so the whole frame fails closed.
                    throw RecognitionFailure.unlocalizedMarker
                }
                markers.append(MarkerObservation(quad: quad, baselineAngleDegrees: angle,
                                                 inkBefore: index > 0,
                                                 inkAfter: index < tokens.count - 1 || token.hasContentAfterAt))
            }
        }
        return markers
    }

    static func readNow(_ image: CaptureImage, roi: ROIGeometry) throws -> [PayloadHypothesis] {
        let crop = try Self.crop(try Self.cgImage(image), roi: roi)
        let lines = try Self.recognize(crop).compactMap { observation -> PayloadLine? in
            guard let top = observation.topCandidates(1).first else { return nil }
            return PayloadLine(text: top.string, confidence: Double(top.confidence),
                               cropBox: VisionReader.topLeft(observation.boundingBox))
        }
        // Vision gives one top reading per line, so one ROI gives one hypothesis.
        return lines.isEmpty ? [] : [PayloadHypothesis(lines: lines)]
    }

    /// The payload scalars strictly between the two `zz` marks that bound the
    /// ROI. One box per non-space scalar is required. A letter-count match is
    /// not this case, and a missing mark is not inferred.
    static func payloadSpan(scalars: [Unicode.Scalar], centers: [CGPoint], roi: ROIGeometry) -> String? {
        let nonSpace = scalars.indices.filter { scalars[$0] != " " }
        guard centers.count == nonSpace.count, !centers.isEmpty else { return nil }
        let tokens = Scanner.tokenize(scalars).filter(\.isMarker)
        guard tokens.count >= 2 else { return nil }
        let axis = CGPoint(x: cos(roi.angleRadians), y: sin(roi.angleRadians))
        let normal = CGPoint(x: -axis.y, y: axis.x)
        func project(_ point: CGPoint) -> Double { point.x * axis.x + point.y * axis.y }
        func across(_ point: CGPoint) -> Double {
            (point.x - roi.centerX) * normal.x + (point.y - roi.centerY) * normal.y
        }
        let roiStart = project(CGPoint(x: roi.centerX, y: roi.centerY)) - roi.width / 2
        func center(_ token: Scanner.Token) -> CGPoint? {
            let ordinals = (token.coreStart..<token.coreEnd).compactMap { nonSpace.firstIndex(of: $0) }
            guard ordinals.count == token.coreEnd - token.coreStart else { return nil }
            let points = ordinals.map { centers[$0] }
            return CGPoint(x: points.map(\.x).reduce(0, +) / Double(points.count),
                           y: points.map(\.y).reduce(0, +) / Double(points.count))
        }
        let placed = tokens.compactMap { token -> (Scanner.Token, Double)? in
            guard let point = center(token), abs(across(point)) <= roi.height else { return nil }
            return (token, project(point))
        }
        let roiEnd = roiStart + roi.width
        guard let opening = placed.filter({ $0.1 < roiStart }).max(by: { $0.1 < $1.1 }),
              let closing = placed.filter({ $0.1 > roiEnd }).min(by: { $0.1 < $1.1 }),
              opening.0.coreEnd <= closing.0.coreStart else { return nil }
        let span = Array(scalars[opening.0.coreEnd..<closing.0.coreStart])
        guard span.contains(where: { $0 != " " && !Scanner.isHyphen($0) }) else { return nil }
        return String(String.UnicodeScalarView(span))
    }

    static func lineSpanNow(_ image: CaptureImage, roi: ROIGeometry) throws -> PayloadHypothesis? {
        let cgImage = try Self.cgImage(image)
        let (observations, rectangles) = try Self.recognizeWithCharacters(cgImage)
        let width = Double(image.pixelWidth), height = Double(image.pixelHeight)
        for observation in observations {
            guard let top = observation.topCandidates(1).first else { continue }
            let scalars = Array(top.string.unicodeScalars).map(Scanner.rule1)
            let nonSpace = scalars.indices.filter { scalars[$0] != " " }
            let characters = Self.characterBoxes(for: observation, scalars: scalars, rectangles: rectangles)
            guard let characters, let indexes = Self.boxedScalarIndexes(scalars, boxCount: characters.count),
                  indexes == nonSpace else { continue }
            let centers = indexes.map { ordinal -> CGPoint in
                let box = characters[nonSpace.firstIndex(of: ordinal)!].boundingBox
                return CGPoint(x: box.midX * width, y: (1 - box.midY) * height)
            }
            guard let text = Self.payloadSpan(scalars: scalars, centers: centers, roi: roi) else { continue }
            return PayloadHypothesis(lines: [PayloadLine(text: text, confidence: Double(top.confidence),
                                                          cropBox: CGRect(x: 0, y: 0, width: 1, height: 1))])
        }
        return nil
    }

    public func lineSpan(in image: CaptureImage, roi: ROIGeometry) async throws -> PayloadHypothesis? {
        try await Self.run { try Self.lineSpanNow(image, roi: roi) }
    }

    // MARK: Vision

    /// The text request plus character boxes for the same image. Vision
    /// places a substring only to its whole word, and a hyphenated code is
    /// one word, so marks inside it are placed by character boxes.
    static func recognizeWithCharacters(_ image: CGImage) throws
        -> ([VNRecognizedTextObservation], [VNTextObservation]) {
        let text = VNRecognizeTextRequest()
        text.recognitionLevel = .accurate
        text.usesLanguageCorrection = false
        text.recognitionLanguages = ["en-US"]
        let rectangles = VNDetectTextRectanglesRequest()
        rectangles.reportCharacterBoxes = true
        let handler = VNImageRequestHandler(cgImage: image, orientation: .up, options: [:])
        do {
            try handler.perform([text, rectangles])
        } catch {
            throw RecognitionFailure.failed
        }
        return (text.results ?? [], rectangles.results ?? [])
    }

    /// One character box per boxed scalar of the line, in reading order, or
    /// nil when the detected characters do not line up. A hyphen is a
    /// separator, not marker ink, and Vision often returns no box for one.
    /// A count that matches neither the non-space scalars nor the letters
    /// is unaligned, and the frame fails closed.
    static func characterBoxes(for line: VNRecognizedTextObservation, scalars: [Unicode.Scalar],
                               rectangles: [VNTextObservation]) -> [VNRectangleObservation]? {
        let lineBox = line.boundingBox
        let boxes = rectangles.filter { rectangle in
            let overlap = rectangle.boundingBox.intersection(lineBox)
            return !overlap.isNull && overlap.width * overlap.height >= 0.5 * rectangle.boundingBox.width * rectangle.boundingBox.height
        }.flatMap { $0.characterBoxes ?? [] }
        let dx = line.bottomRight.x - line.bottomLeft.x, dy = line.bottomRight.y - line.bottomLeft.y
        let sorted = boxes.sorted {
            ($0.boundingBox.midX * dx + $0.boundingBox.midY * dy) < ($1.boundingBox.midX * dx + $1.boundingBox.midY * dy)
        }
        return boxedScalarIndexes(scalars, boxCount: sorted.count) == nil ? nil : sorted
    }

    /// Scalar indexes that own a character box, in reading order. The first
    /// match is one box per non-space scalar. The second is one box per
    /// letter, which is the measured case of a hyphenated line whose hyphens
    /// have no boxes. Any other count is unaligned.
    static func boxedScalarIndexes(_ scalars: [Unicode.Scalar], boxCount: Int) -> [Int]? {
        let nonSpace = scalars.indices.filter { scalars[$0] != " " }
        if boxCount == nonSpace.count { return Array(nonSpace) }
        let letters = nonSpace.filter { !Scanner.isHyphen(scalars[$0]) }
        if boxCount == letters.count { return Array(letters) }
        return nil
    }

    /// The mark's quad, top left first, normalized over the upright image
    /// with the origin at the top left.
    static func quad(_ range: Range<Int>, scalars: [Unicode.Scalar], characters: [VNRectangleObservation]?,
                     text: VNRecognizedText) -> [NormalizedPoint]? {
        func point(_ p: CGPoint) -> NormalizedPoint {
            NormalizedPoint(x: min(max(Double(p.x), 0), 1), y: min(max(1 - Double(p.y), 0), 1))
        }
        if let characters {
            guard let indexes = Self.boxedScalarIndexes(scalars, boxCount: characters.count) else { return nil }
            let ordinals = range.compactMap { indexes.firstIndex(of: $0) }
            guard ordinals.count == range.count, let first = ordinals.first, let last = ordinals.last else { return nil }
            let a = characters[first], b = characters[last]
            return [a.topLeft, b.topRight, b.bottomRight, a.bottomLeft].map(point)
        }
        // Without character boxes, only a mark that is its own word can be
        // placed: its word box is exactly the mark.
        let before = range.lowerBound == 0 || scalars[range.lowerBound - 1] == " "
        let after = range.upperBound == scalars.count || scalars[range.upperBound] == " "
        guard before, after, let stringRange = VisionReader.stringRange(text.string, range.lowerBound, range.upperBound),
              let box = try? text.boundingBox(for: stringRange) else { return nil }
        return [box.topLeft, box.topRight, box.bottomRight, box.bottomLeft].map(point)
    }

    static func recognize(_ image: CGImage) throws -> [VNRecognizedTextObservation] {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = false
        request.recognitionLanguages = ["en-US"]
        let handler = VNImageRequestHandler(cgImage: image, orientation: .up, options: [:])
        do {
            try handler.perform([request])
        } catch {
            throw RecognitionFailure.failed
        }
        return request.results ?? []
    }

    /// The reading direction of a line in upright pixels with y down: from
    /// the bottom edges of its first and last character boxes when they line
    /// up, else from the line's own bottom edge.
    static func angleDegrees(_ observation: VNRecognizedTextObservation, characters: [VNRectangleObservation]?,
                             width: Double, height: Double) -> Double {
        var left = observation.bottomLeft, right = observation.bottomRight
        if let first = characters?.first, let last = characters?.last, characters!.count > 1 {
            left = first.bottomLeft
            right = last.bottomRight
        }
        let dx = (right.x - left.x) * width
        let dy = ((1 - right.y) - (1 - left.y)) * height
        return atan2(dy, dx) * 180 / .pi
    }

    // MARK: Images, in memory only

    static func cgImage(_ image: CaptureImage) throws -> CGImage {
        guard let backing = image.backing, CFGetTypeID(backing) == CGImage.typeID else {
            throw RecognitionFailure.unavailable
        }
        return backing as! CGImage
    }

    /// Step 1: the EXIF orientation applied once, so every coordinate is
    /// over the upright image.
    static func upright(_ image: CGImage, orientation: CGImagePropertyOrientation) throws -> CGImage {
        if orientation == .up { return image }
        let oriented = CIImage(cgImage: image).oriented(orientation)
        let context = CIContext(options: [.cacheIntermediates: false])
        guard let upright = context.createCGImage(oriented, from: oriented.extent) else {
            throw RecognitionFailure.unreadableImage
        }
        return upright
    }

    /// Step 4: crop the rotated ROI rectangle into an upright bitmap. Crop
    /// and rotation only; no perspective or projective transform.
    static func crop(_ image: CGImage, roi: ROIGeometry) throws -> CGImage {
        let width = roi.cropPixelWidth, height = roi.cropPixelHeight
        guard let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                                      space: CGColorSpace(name: CGColorSpace.sRGB)!,
                                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
            throw RecognitionFailure.failed
        }
        context.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        context.interpolationQuality = .high
        // Core Graphics has its origin at the bottom left, so the ROI center
        // and angle flip: y becomes H - y, and clockwise becomes counterclockwise.
        let imageHeight = Double(image.height)
        context.translateBy(x: Double(width) / 2, y: Double(height) / 2)
        context.rotate(by: roi.angleRadians)
        context.translateBy(x: -roi.centerX, y: -(imageHeight - roi.centerY))
        context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        guard let crop = context.makeImage() else { throw RecognitionFailure.failed }
        return crop
    }
}
#endif
