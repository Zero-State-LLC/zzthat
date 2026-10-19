#if canImport(Vision)
import CoreGraphics
import CoreText
import Foundation
import ImageIO
import Testing
import UniformTypeIdentifiers
@testable import ZZCore

/// Counts the adapter's calls, so a test can show payload OCR ran only on
/// the selected pair, and not at all for a frame that failed closed.
final class CountingAdapter: RecognitionAdapter, @unchecked Sendable {
    let base: any RecognitionAdapter
    private let lock = NSLock()
    private var reads: [ROIGeometry] = []

    init(_ base: any RecognitionAdapter) {
        self.base = base
    }

    var engineId: String { base.engineId }
    var engineVersion: String { base.engineVersion }
    var payloadReads: [ROIGeometry] { lock.withLock { reads } }

    func prepare(imageData: Data) async throws -> CaptureImage { try await base.prepare(imageData: imageData) }

    func detectMarkers(in image: CaptureImage) async throws -> [MarkerObservation] {
        try await base.detectMarkers(in: image)
    }

    func readPayload(in image: CaptureImage, roi: ROIGeometry) async throws -> [PayloadHypothesis] {
        lock.withLock { reads.append(roi) }
        return try await base.readPayload(in: image, roi: roi)
    }
}

/// tasks.md T024: the Apple Vision adapter behind RecognitionResult, on
/// rendered images (runtime.md, Testing: a smoke test, not qualification
/// evidence). Black text on white in Helvetica unless named.
@Suite("Vision adapter")
struct VisionAdapterTests {
    struct Text {
        let string: String
        let size: Double
        /// The text origin, from the top left, in pixels.
        let x: Double
        let y: Double
    }

    static func draw(_ texts: [Text], width: Int = 1800, height: Int = 600, degrees: Double = 0,
                     font: String = "Helvetica") throws -> CGImage {
        let context = try #require(CGContext(
            data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        // Turn the page clockwise by `degrees` about its center.
        context.translateBy(x: Double(width) / 2, y: Double(height) / 2)
        context.rotate(by: -degrees * .pi / 180)
        context.translateBy(x: -Double(width) / 2, y: -Double(height) / 2)
        for text in texts {
            let attributes: [NSAttributedString.Key: Any] = [
                NSAttributedString.Key(kCTFontAttributeName as String): CTFontCreateWithName(font as CFString, text.size, nil),
                NSAttributedString.Key(kCTForegroundColorAttributeName as String): CGColor(red: 0, green: 0, blue: 0, alpha: 1),
            ]
            let line = CTLineCreateWithAttributedString(NSAttributedString(string: text.string, attributes: attributes))
            context.textPosition = CGPoint(x: text.x, y: Double(height) - text.y)
            CTLineDraw(line, context)
        }
        return try #require(context.makeImage())
    }

    static func png(_ image: CGImage, orientation: CGImagePropertyOrientation = .up) throws -> Data {
        let data = NSMutableData()
        let destination = try #require(CGImageDestinationCreateWithData(data, UTType.png.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(destination, image,
                                   [kCGImagePropertyOrientation: orientation.rawValue] as CFDictionary)
        #expect(CGImageDestinationFinalize(destination))
        return data as Data
    }

    static func line(_ string: String, size: Double = 100) -> [Text] {
        [Text(string: string, size: size, x: 60, y: 350)]
    }

    static func pipeline(_ adapter: any RecognitionAdapter,
                         configuration: CaptureConfiguration = .current) throws -> CapturePipeline {
        let list = try VisionPipelineTests.fixture7()
        return CapturePipeline(decoder: ReadPipeline(classifyList: list, checkList: list),
                               configuration: configuration, adapter: adapter)
    }

    @Test func aRenderedCodeIsPairedThenReadInsideItsROIOnly() async throws {
        let adapter = CountingAdapter(VisionRecognitionAdapter())
        let pipeline = try Self.pipeline(adapter)
        let session = await pipeline.detect(imageData: try Self.png(try Self.draw(Self.line("zz-copper-lantern-sky-zz"))),
                                            imageId: "img")
        let result = session.result
        #expect(result.engineId == "apple-vision-recognize-text")
        #expect(!result.engineVersion.isEmpty)
        #expect(result.frameReasonCode == nil)
        #expect(result.fiducials.map(\.detectionId) == ["img.f1", "img.f2"])
        #expect(result.fiducials.map(\.role) == [.opening, .closing])
        #expect(result.fiducials.map(\.pairId) == ["img.p1", "img.p1"])
        #expect(Set(result.fiducials.compactMap(\.baselineId)) == ["img.b1"])
        // No payload text before a pair is chosen.
        #expect(result.candidates.isEmpty && result.rois.isEmpty)
        #expect(adapter.payloadReads.isEmpty)

        let only = try #require(session.automaticSelection)
        let outcome = await pipeline.process(session, candidateId: only.id)
        #expect(adapter.payloadReads.count == 1)
        let roi = try #require(outcome.result.rois.first)
        #expect(roi.pairId == "img.p1")
        #expect(roi.rectificationSucceeded == true)
        #expect(roi.transformIsCropAndRotationOnly)
        let text = try #require(outcome.result.candidates.first)
        #expect(outcome.result.candidates.count == 1)
        #expect(text.pairId == "img.p1")
        // The adapter reports payload only: the markers are not in it.
        #expect(!text.rawText.lowercased().contains("zz"), "read: \(text.rawText)")
        // The reading stays inside the ROI, which excludes both marker boxes.
        let bounds = try #require(NormalizedBox.bounding(roi.polygon)).rect.insetBy(dx: -0.01, dy: -0.01)
        #expect(text.regions.allSatisfy { bounds.contains($0.rect) })
        for marker in result.fiducials {
            #expect(!marker.box.rect.insetBy(dx: 0.002, dy: 0.002).intersects(
                NormalizedBox.bounding(roi.polygon)!.rect))
        }
        let decoded = try #require(outcome.decoded.first)
        #expect(decoded.canonical == "zz-copper-lantern-sky-zz", "read: \(text.rawText)")
        #expect(decoded.check == .ok)
        // The spec 004 table says Accept; confirm-only (T102) makes it Clarify.
        #expect(decoded.baseBand == .accept)
        #expect(decoded.band == .clarify)
        #expect(decoded.reason == "confirm-only")
        #expect(decoded.pairId == "img.p1")
    }

    @Test func spacedMarkersPairTheSameWay() async throws {
        let pipeline = try Self.pipeline(VisionRecognitionAdapter())
        let session = await pipeline.detect(imageData: try Self.png(try Self.draw(Self.line("zz copper lantern sky zz"))))
        let only = try #require(session.automaticSelection)
        let outcome = await pipeline.process(session, candidateId: only.id)
        #expect(outcome.decoded.map(\.canonical) == ["zz-copper-lantern-sky-zz"])
    }

    /// Two codes: both are boxed, nothing is read until the person picks,
    /// and then only the picked pair is read.
    @Test func twoCodesWaitForASelectionAndReadOnlyThatPair() async throws {
        let adapter = CountingAdapter(VisionRecognitionAdapter())
        let pipeline = try Self.pipeline(adapter)
        let image = try Self.draw([Text(string: "zz-river-falcon-sky-zz", size: 100, x: 60, y: 200),
                                   Text(string: "zz-b2-4-zz", size: 100, x: 60, y: 450)])
        let session = await pipeline.detect(imageData: try Self.png(image), imageId: "img")
        #expect(session.result.visualCandidates.map(\.pairId) == ["img.p1", "img.p2"])
        #expect(session.result.visualCandidates.allSatisfy { $0.isReadable })
        #expect(session.automaticSelection == nil)
        #expect(adapter.payloadReads.isEmpty)
        let outcome = await pipeline.process(session, candidateId: session.result.visualCandidates[1].id)
        #expect(adapter.payloadReads.count == 1)
        #expect(outcome.result.rois.map(\.pairId) == ["img.p2"])
        #expect(outcome.decoded.map(\.canonical) == ["zz-b2-4-zz"])
        #expect(outcome.decoded.map(\.band) == [.clarify])
    }

    /// FR-052 and spec 004 FR-009: a code wrapped onto a second line puts
    /// its markers on two baselines. v1 pairs same-baseline markers only, so
    /// this frame is two partial candidates, and nothing is read.
    @Test func aCodeWrappedOntoASecondLineIsTwoPartialsWithNoOCR() async throws {
        let adapter = CountingAdapter(VisionRecognitionAdapter())
        let pipeline = try Self.pipeline(adapter)
        let image = try Self.draw([Text(string: "zz-copper-lantern", size: 100, x: 60, y: 200),
                                   Text(string: "sky-zz", size: 100, x: 60, y: 450)])
        let session = await pipeline.detect(imageData: try Self.png(image))
        #expect(session.result.visualCandidates.map(\.kind) == [.partial, .partial])
        #expect(session.result.visualCandidates.map(\.reasonCode) == ["missing-closing-fiducial",
                                                                      "missing-opening-fiducial"])
        #expect(session.automaticSelection == nil)
        for candidate in session.result.visualCandidates {
            let outcome = await pipeline.process(session, candidateId: candidate.id)
            #expect(outcome.disposition?.band == .retry)
            #expect(outcome.decoded.isEmpty)
        }
        #expect(adapter.payloadReads.isEmpty)
    }

    /// A flat two-line payload between same-baseline markers is one code
    /// (recognition-adapter.md, Runtime sequence step 4).
    @Test func aFlatTwoLinePayloadBetweenSameBaselineMarkersIsOneCode() async throws {
        let pipeline = try Self.pipeline(VisionRecognitionAdapter())
        let image = try Self.draw([Text(string: "zz", size: 120, x: 60, y: 350),
                                   Text(string: "copper lantern", size: 48, x: 270, y: 300),
                                   Text(string: "sky", size: 48, x: 270, y: 352),
                                   Text(string: "zz", size: 120, x: 720, y: 350)])
        let session = await pipeline.detect(imageData: try Self.png(image))
        let only = try #require(session.automaticSelection)
        let outcome = await pipeline.process(session, candidateId: only.id)
        let text = try #require(outcome.result.candidates.first)
        #expect(text.lines.count == 2, "lines: \(text.lines.map(\.text))")
        #expect(outcome.decoded.map(\.canonical) == ["zz-copper-lantern-sky-zz"], "read: \(text.rawText)")
    }

    /// Crop and rotation normalization: a page turned 8 degrees reads, and
    /// the ROI records the turn and an affine-only transform.
    @Test func aTurnedCodeIsCroppedAndRotated() async throws {
        let pipeline = try Self.pipeline(VisionRecognitionAdapter())
        let image = try Self.draw([Text(string: "zz-copper-lantern-sky-zz", size: 100, x: 200, y: 330)], degrees: 8)
        let session = await pipeline.detect(imageData: try Self.png(image))
        let only = try #require(session.automaticSelection)
        let outcome = await pipeline.process(session, candidateId: only.id)
        let roi = try #require(outcome.result.rois.first)
        #expect(abs((roi.orientationDegrees ?? 0) - 8) < 2)
        #expect(roi.transformIsCropAndRotationOnly)
        #expect(outcome.decoded.map(\.canonical) == ["zz-copper-lantern-sky-zz"])
    }

    /// Past the supported turn, nothing is read and nothing is accepted.
    @Test func aSteepTurnFailsClosedWithNoOCR() async throws {
        let adapter = CountingAdapter(VisionRecognitionAdapter())
        let pipeline = try Self.pipeline(adapter)
        let image = try Self.draw([Text(string: "zz-copper-lantern-sky-zz", size: 100, x: 200, y: 330)], degrees: 35)
        let session = await pipeline.detect(imageData: try Self.png(image))
        for candidate in session.result.visualCandidates {
            let outcome = await pipeline.process(session, candidateId: candidate.id)
            #expect(outcome.decoded.allSatisfy { $0.band != .accept })
        }
        #expect(session.result.visualCandidates.allSatisfy { !$0.isReadable })
        #expect(adapter.payloadReads.isEmpty)
    }

    /// Step 1: the EXIF orientation is applied once, so the evidence is
    /// over the upright image.
    @Test func thePhotoIsNormalizedUprightOnce() async throws {
        let upright = try Self.draw(Self.line("zz-copper-lantern-sky-zz"))
        // Store it turned a quarter counterclockwise, tagged to display turned back.
        let context = try #require(CGContext(
            data: nil, width: upright.height, height: upright.width, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.translateBy(x: Double(upright.height), y: 0)
        context.rotate(by: .pi / 2)
        context.draw(upright, in: CGRect(x: 0, y: 0, width: upright.width, height: upright.height))
        let stored = try #require(context.makeImage())
        let adapter = VisionRecognitionAdapter()
        let prepared = try await adapter.prepare(imageData: try Self.png(stored, orientation: .right))
        #expect(prepared.pixelWidth == upright.width)
        #expect(prepared.pixelHeight == upright.height)
        let pipeline = try Self.pipeline(adapter)
        let session = await pipeline.detect(imageData: try Self.png(stored, orientation: .right))
        let only = try #require(session.automaticSelection)
        #expect(session.result.imageWidth == upright.width)
        let outcome = await pipeline.process(session, candidateId: only.id)
        #expect(outcome.decoded.map(\.canonical) == ["zz-copper-lantern-sky-zz"])
        #expect(abs(outcome.result.rois.first?.orientationDegrees ?? 99) < 2)
    }

    /// A third mark after a code makes two pair hypotheses; the person picks.
    @Test func aStrayMarkAfterACodeNeedsASelection() async throws {
        let pipeline = try Self.pipeline(VisionRecognitionAdapter())
        let session = await pipeline.detect(imageData: try Self.png(try Self.draw(Self.line("zz-copper-lantern-sky-zz zz",
                                                                                          size: 90))))
        #expect(session.result.visualCandidates.count == 2)
        #expect(session.automaticSelection == nil)
        // The middle mark belongs to two hypotheses, so it has no single role.
        #expect(session.result.fiducials.map(\.role) == [.opening, .unknown, .closing])
        #expect(session.result.fiducials[1].pairId == nil)
    }

    /// A hyphen is a separator. Vision often returns no box for one. Eighteen
    /// boxes on `ZZ-RIVER-FALCON-SKY-ZZ` (22 non-space scalars, 18 letters)
    /// line up with the letters. Seventeen matches neither count and stays
    /// unaligned, so the frame still fails closed.
    @Test func aHyphenatedLinePlacesMarksWhenHyphensHaveNoBoxes() {
        let scalars = Array("ZZ-RIVER-FALCON-SKY-ZZ".unicodeScalars)
        let letters = VisionRecognitionAdapter.boxedScalarIndexes(scalars, boxCount: 18)
        #expect(letters?.count == 18)
        #expect(letters?.prefix(2) == [0, 1])
        let closing = scalars.index(scalars.endIndex, offsetBy: -2)
        #expect(Array(letters?.suffix(2) ?? []) == [closing, scalars.index(before: scalars.endIndex)])
        #expect(VisionRecognitionAdapter.boxedScalarIndexes(scalars, boxCount: 22)?.count == 22)
        #expect(VisionRecognitionAdapter.boxedScalarIndexes(scalars, boxCount: 17) == nil)
    }

    @Test func anUnreadablePhotoFailsTheFrameClosed() async throws {
        let pipeline = try Self.pipeline(VisionRecognitionAdapter())
        let session = await pipeline.detect(imageData: Data("not an image".utf8))
        #expect(session.result.frameReasonCode == "recognizer-unavailable")
        #expect(session.result.runtimeOutcome == .exception)
        #expect(pipeline.frameDisposition(session.result)?.band == .abstain)
        #expect(session.result.visualCandidates.isEmpty)
    }
}
#endif
