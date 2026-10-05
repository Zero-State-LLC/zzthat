#if canImport(Vision)
import CoreGraphics
import Foundation
import ImageIO
import Vision

/// The iOS reader (runtime.md, Read pipeline step 2): Vision text
/// recognition at the accurate level, with language correction off and the
/// en-US language. Each line keeps its top candidate's confidence.
public enum VisionReader {
    public enum ReadError: Error, Sendable {
        case unreadableImage
        case recognitionFailed(String)
    }

    public static func recognize(_ image: CGImage,
                                 orientation: CGImagePropertyOrientation = .up) throws(ReadError) -> [RecognizedLine] {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = false
        request.recognitionLanguages = ["en-US"]
        let handler = VNImageRequestHandler(cgImage: image, orientation: orientation, options: [:])
        do {
            try handler.perform([request])
        } catch {
            throw .recognitionFailed(String(describing: error))
        }
        return (request.results ?? []).compactMap(line(from:))
    }

    public static func recognize(imageData: Data) throws(ReadError) -> [RecognizedLine] {
        guard let source = CGImageSourceCreateWithData(imageData as CFData, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else { throw .unreadableImage }
        let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]
        let raw = properties?[kCGImagePropertyOrientation] as? UInt32
        let orientation = raw.flatMap(CGImagePropertyOrientation.init(rawValue:)) ?? .up
        return try recognize(image, orientation: orientation)
    }

    static func line(from observation: VNRecognizedTextObservation) -> RecognizedLine? {
        guard let top = observation.topCandidates(1).first else { return nil }
        let text = top.string
        var pieces: [RecognizedLine.Piece] = []
        let scalars = Array(text.unicodeScalars)
        var start = 0
        while start < scalars.count {
            if scalars[start] == " " { start += 1; continue }
            var end = start
            while end < scalars.count, scalars[end] != " " { end += 1 }
            if let range = stringRange(text, start, end),
               let box = try? top.boundingBox(for: range)?.boundingBox {
                pieces.append(.init(range: start..<end, box: topLeft(box)))
            }
            start = end
        }
        return RecognizedLine(text: text, confidence: Double(top.confidence),
                              box: topLeft(observation.boundingBox), pieces: pieces)
    }

    static func stringRange(_ text: String, _ start: Int, _ end: Int) -> Range<String.Index>? {
        let scalars = text.unicodeScalars
        guard let lower = scalars.index(scalars.startIndex, offsetBy: start, limitedBy: scalars.endIndex),
              let upper = scalars.index(scalars.startIndex, offsetBy: end, limitedBy: scalars.endIndex) else { return nil }
        return lower..<upper
    }

    /// Vision boxes have their origin at the bottom left.
    static func topLeft(_ box: CGRect) -> CGRect {
        CGRect(x: box.minX, y: 1 - box.maxY, width: box.width, height: box.height)
    }
}
#endif
