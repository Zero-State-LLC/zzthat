import CoreGraphics
import Foundation
import Testing
@testable import ZZCore

/// An engine-neutral adapter with canned evidence. It records every payload
/// read, so a test can show which ROI was read and that unselected,
/// partial, bare, unsafe, and overflow cases were never read.
final class FixtureAdapter: RecognitionAdapter, @unchecked Sendable {
    let markers: [MarkerObservation]
    let hypotheses: [PayloadHypothesis]
    var failPrepare = false
    var failPayload = false
    private let lock = NSLock()
    private var reads: [ROIGeometry] = []

    static let width = 1000
    static let height = 500

    init(markers: [MarkerObservation], hypotheses: [PayloadHypothesis] = []) {
        self.markers = markers
        self.hypotheses = hypotheses
    }

    let engineId = "fixture"
    let engineVersion = "0"
    var payloadReads: [ROIGeometry] { lock.withLock { reads } }

    func prepare(imageData: Data) async throws -> CaptureImage {
        if failPrepare { throw RecognitionFailure.unavailable }
        return CaptureImage(pixelWidth: Self.width, pixelHeight: Self.height, backing: nil)
    }

    func detectMarkers(in image: CaptureImage) async throws -> [MarkerObservation] { markers }

    func readPayload(in image: CaptureImage, roi: ROIGeometry) async throws -> [PayloadHypothesis] {
        lock.withLock { reads.append(roi) }
        if failPayload { throw RecognitionFailure.failed }
        return hypotheses
    }

    /// A mark centered at (x, y) pixels, `size` pixels tall and 1.2 times as
    /// wide, turned `degrees` clockwise.
    static func mark(_ x: Double, _ y: Double, size: Double = 40, degrees: Double = 0, before: Bool = false,
                     after: Bool = false) -> MarkerObservation {
        let angle = degrees * .pi / 180
        let c = cos(angle), s = sin(angle)
        let w = size * 1.2 / 2, h = size / 2
        let corners = [(-w, -h), (w, -h), (w, h), (-w, h)].map { dx, dy in
            NormalizedPoint(x: (x + c * dx - s * dy) / Double(width), y: (y + s * dx + c * dy) / Double(height))
        }
        return MarkerObservation(quad: corners, baselineAngleDegrees: degrees, inkBefore: before, inkAfter: after)
    }

    static func line(_ text: String, _ confidence: Double?, y: Double = 0.2) -> PayloadLine {
        PayloadLine(text: text, confidence: confidence, cropBox: CGRect(x: 0.05, y: y, width: 0.9, height: 0.3))
    }
}

@Suite("Capture pipeline")
struct CapturePipelineTests {
    static func fixture7() throws -> WordList {
        let words = try #require(Vectors.section("check_word")["wordlist"] as? [String])
        return try WordList(version: "fixture-7", words: words)
    }

    static func pipeline(_ adapter: FixtureAdapter, _ configuration: CaptureConfiguration = .current) throws
        -> CapturePipeline {
        let list = try fixture7()
        return CapturePipeline(decoder: ReadPipeline(classifyList: list, checkList: list),
                               configuration: configuration, adapter: adapter)
    }

    static let pair = [FixtureAdapter.mark(100, 250, after: true), FixtureAdapter.mark(800, 250, before: true)]
    static func code(_ confidence: Double? = 0.95) -> [PayloadHypothesis] {
        [PayloadHypothesis(lines: [FixtureAdapter.line("copper lantern sky", confidence)])]
    }

    func read(_ adapter: FixtureAdapter, _ configuration: CaptureConfiguration = .current,
              select: Int? = nil) async throws -> (CaptureSession, CaptureOutcome?) {
        let pipeline = try Self.pipeline(adapter, configuration)
        let session = await pipeline.detect(imageData: Data(), imageId: "img")
        let chosen = select.map { session.result.visualCandidates[$0] } ?? session.automaticSelection
        guard let chosen else { return (session, nil) }
        return (session, await pipeline.process(session, candidateId: chosen.id))
    }

    // MARK: Pairing

    @Test func oneCompletePairIsReadWithoutATap() async throws {
        let adapter = FixtureAdapter(markers: Self.pair, hypotheses: Self.code())
        let (session, outcome) = try await read(adapter)
        let result = session.result
        #expect(result.fiducials.map(\.detectionId) == ["img.f1", "img.f2"])
        #expect(result.fiducials.map(\.role) == [.opening, .closing])
        #expect(result.fiducials.map(\.pairId) == ["img.p1", "img.p1"])
        #expect(result.fiducials.map(\.baselineId) == ["img.b1", "img.b1"])
        #expect(result.fiducials.allSatisfy { $0.score == nil })
        #expect(result.visualCandidates.map(\.kind) == [.complete(pairId: "img.p1")])
        let roi = try #require(result.visualCandidates.first?.roi)
        #expect(roi.openingDetectionId == "img.f1" && roi.closingDetectionId == "img.f2")
        // The ROI sits between the two marker patches, with the inset.
        let bounds = try #require(NormalizedBox.bounding(roi.polygon))
        #expect(bounds.x > result.fiducials[0].box.x + result.fiducials[0].box.width)
        #expect(bounds.x + bounds.width < result.fiducials[1].box.x)
        #expect(roi.transformIsCropAndRotationOnly)
        #expect(roi.rectificationTransform?.suffix(3) == [0, 0, 1])
        #expect(roi.rectificationSucceeded == nil)

        let read = try #require(outcome)
        #expect(adapter.payloadReads.count == 1)
        #expect(read.result.rois.first?.rectificationSucceeded == true)
        #expect(read.result.candidates.map(\.pairId) == ["img.p1"])
        #expect(read.result.candidates.map(\.candidateId) == ["img.p1.t1"])
        #expect(read.decoded.map(\.canonical) == ["zz-copper-lantern-sky-zz"])
    }

    @Test func theTransformMapsTheCropOntoThePolygon() throws {
        let roi = ROIGeometry(centerX: 400, centerY: 200, width: 300, height: 60, angleRadians: 0.2,
                              imageWidth: 1000, imageHeight: 500)
        let m = roi.transform
        for (corner, (u, v)) in zip(roi.polygon, [(0.0, 0.0), (1.0, 0.0), (1.0, 1.0), (0.0, 1.0)]) {
            #expect(abs(m[0] * u + m[1] * v + m[2] - corner.x) < 1e-9)
            #expect(abs(m[3] * u + m[4] * v + m[5] - corner.y) < 1e-9)
        }
        #expect(m[6...] == [0, 0, 1])
    }

    @Test(arguments: [
        (false, true, FiducialRole.opening, VisualCandidate.Kind.partial, "missing-closing-fiducial", Band.retry),
        (true, false, .closing, .partial, "missing-opening-fiducial", .retry),
        (true, true, .unknown, .partial, "partial-fiducial", .retry),
        (false, false, .unknown, .bare, "bare-mark", .abstain),
    ])
    func aLoneMarkIsPartialOrBareAndIsNeverRead(_ before: Bool, _ after: Bool, _ role: FiducialRole,
                                                _ kind: VisualCandidate.Kind, _ code: String, _ band: Band)
        async throws {
        let adapter = FixtureAdapter(markers: [FixtureAdapter.mark(300, 250, before: before, after: after)],
                                     hypotheses: Self.code())
        let (session, outcome) = try await read(adapter, select: 0)
        #expect(session.automaticSelection == nil)
        #expect(session.result.fiducials.map(\.role) == [role])
        #expect(session.result.visualCandidates.map(\.kind) == [kind])
        #expect(session.result.reasonCodes == [code])
        #expect(outcome?.disposition == BandDecision(band, code))
        #expect(outcome?.decoded.isEmpty == true)
        #expect(adapter.payloadReads.isEmpty)
    }

    /// Three marks on one baseline: two pair hypotheses that share the middle
    /// mark. Both stay visible, the person picks, and only that ROI is read.
    @Test func ambiguousPairingWaitsAndReadsOnlyTheSelection() async throws {
        let marks = [FixtureAdapter.mark(100, 250, after: true), FixtureAdapter.mark(500, 250, before: true, after: true),
                     FixtureAdapter.mark(900, 250, before: true)]
        let adapter = FixtureAdapter(markers: marks, hypotheses: Self.code())
        let pipeline = try Self.pipeline(adapter)
        let session = await pipeline.detect(imageData: Data(), imageId: "img")
        #expect(session.result.visualCandidates.map(\.pairId) == ["img.p1", "img.p2"])
        #expect(session.result.fiducials.map(\.role) == [.opening, .unknown, .closing])
        #expect(session.result.fiducials.map(\.pairId) == ["img.p1", nil, "img.p2"])
        #expect(session.automaticSelection == nil)
        #expect(adapter.payloadReads.isEmpty)
        let second = session.result.visualCandidates[1]
        let outcome = await pipeline.process(session, candidateId: second.id)
        #expect(adapter.payloadReads == [second.geometry!])
        #expect(outcome.result.rois.map(\.pairId) == ["img.p2"])
    }

    /// A complete pair beside a partial is still a choice for the person.
    @Test func aCompletePairBesideAPartialWaitsForASelection() async throws {
        let marks = Self.pair + [FixtureAdapter.mark(300, 420, after: true)]
        let adapter = FixtureAdapter(markers: marks, hypotheses: Self.code())
        let (session, outcome) = try await read(adapter)
        #expect(session.result.visualCandidates.map(\.kind) == [.complete(pairId: "img.p1"), .partial])
        #expect(outcome == nil)
        #expect(adapter.payloadReads.isEmpty)
    }

    @Test(arguments: [
        ("offset across the baseline", FixtureAdapter.mark(800, 275, before: true)),
        ("turned another way", FixtureAdapter.mark(800, 250, degrees: 12, before: true)),
        ("unequal size, as in perspective", FixtureAdapter.mark(800, 250, size: 70, before: true)),
    ])
    func marksOffTheBaselineDoNotPair(_ name: String, _ closing: MarkerObservation) async throws {
        let adapter = FixtureAdapter(markers: [FixtureAdapter.mark(100, 250, after: true), closing],
                                     hypotheses: Self.code())
        let (session, outcome) = try await read(adapter)
        #expect(session.result.visualCandidates.map(\.kind) == [.partial, .partial], "\(name)")
        #expect(Set(session.result.fiducials.compactMap(\.baselineId)).count == 2)
        #expect(outcome == nil)
        #expect(adapter.payloadReads.isEmpty)
    }

    /// Marks that chain across drifting baselines (a curved or wrapped
    /// surface) fail the frame closed rather than pick a baseline.
    @Test func aDriftingChainOfBaselinesFailsTheFrameClosed() async throws {
        let marks = [FixtureAdapter.mark(100, 250, degrees: 0, after: true),
                     FixtureAdapter.mark(400, 262, degrees: 5, before: true, after: true),
                     FixtureAdapter.mark(700, 297, degrees: 10, before: true)]
        let adapter = FixtureAdapter(markers: marks, hypotheses: Self.code())
        let (session, outcome) = try await read(adapter)
        #expect(session.result.frameReasonCode == "unsupported-geometry")
        #expect(session.result.visualCandidates.isEmpty)
        #expect(outcome == nil)
        #expect(adapter.payloadReads.isEmpty)
    }

    // MARK: Bounds and unsafe geometry

    @Test func tooManyMarksFailTheWholeFrameWithoutTruncating() async throws {
        let marks = (0..<9).map { FixtureAdapter.mark(60 + Double($0) * 110, 250, before: $0 > 0, after: $0 < 8) }
        let adapter = FixtureAdapter(markers: marks, hypotheses: Self.code())
        let pipeline = try Self.pipeline(adapter)
        let session = await pipeline.detect(imageData: Data(), imageId: "img")
        #expect(session.result.frameReasonCode == "marker-limit-exceeded")
        #expect(session.result.reasonCodes == ["marker-limit-exceeded"])
        // Every detection is kept as evidence; none is paired or selectable.
        #expect(session.result.fiducials.count == 9)
        #expect(session.result.fiducials.allSatisfy { $0.pairId == nil && $0.role == .unknown })
        #expect(session.result.visualCandidates.isEmpty)
        #expect(session.automaticSelection == nil)
        #expect(pipeline.frameDisposition(session.result) == BandDecision(.retry, "marker-limit-exceeded"))
        let outcome = await pipeline.process(session, candidateId: "img.c1")
        #expect(outcome.disposition?.band == .retry)
        #expect(adapter.payloadReads.isEmpty)
    }

    @Test func tooManyPairsFailTheFrame() async throws {
        var few = CaptureConfiguration.current
        few.limits.maxPairs = 1
        let three = [FixtureAdapter.mark(100, 250, after: true), FixtureAdapter.mark(500, 250, before: true, after: true),
                     FixtureAdapter.mark(900, 250, before: true)]
        let adapter = FixtureAdapter(markers: three, hypotheses: Self.code())
        let (pairs, _) = try await read(adapter, few)
        #expect(pairs.result.frameReasonCode == "pair-limit-exceeded")
        #expect(pairs.result.visualCandidates.isEmpty)
        #expect(pairs.result.fiducials.count == 3)
        #expect(adapter.payloadReads.isEmpty)
    }

    @Test func noMarkIsNoCandidate() async throws {
        let (session, _) = try await read(FixtureAdapter(markers: []))
        #expect(session.result.visualCandidates.isEmpty)
        #expect(session.result.reasonCodes == ["no-fiducial"])
        #expect(session.result.frameReasonCode == nil)
    }

    @Test func aMissingLimitFailsClosed() async throws {
        var missing = CaptureConfiguration.current
        missing.limits.maxMarkers = nil
        let adapter = FixtureAdapter(markers: Self.pair, hypotheses: Self.code())
        let pipeline = try Self.pipeline(adapter, missing)
        let session = await pipeline.detect(imageData: Data(), imageId: "img")
        #expect(session.result.frameReasonCode == "invalid-configuration")
        #expect(pipeline.frameDisposition(session.result)?.band == .abstain)
        var noInset = CaptureConfiguration.current
        noInset.geometry.roiInset = nil
        let (other, _) = try await read(FixtureAdapter(markers: Self.pair), noInset)
        #expect(other.result.frameReasonCode == "invalid-configuration")
        var inverted = CaptureConfiguration.current
        inverted.thresholds = .init(acceptMinConfidence: 0.5, retryBelowConfidence: 0.8)
        let (thresholds, _) = try await read(FixtureAdapter(markers: Self.pair), inverted)
        #expect(thresholds.result.frameReasonCode == "invalid-configuration")
        #expect(adapter.payloadReads.isEmpty)
    }

    @Test(arguments: [
        ("turned past the limit", [FixtureAdapter.mark(150, 150, degrees: 25, after: true),
                                   FixtureAdapter.mark(600, 360, degrees: 25, before: true)], "unsupported-geometry"),
        ("too narrow between the marks", [FixtureAdapter.mark(100, 250, after: true),
                                          FixtureAdapter.mark(160, 250, before: true)], "unsafe-roi"),
        ("leaves the image", [FixtureAdapter.mark(100, 470, size: 40, after: true),
                              FixtureAdapter.mark(800, 470, size: 40, before: true)], "unsafe-roi"),
    ])
    func unsafePairGeometryIsNeverRead(_ name: String, _ marks: [MarkerObservation], _ code: String) async throws {
        var tall = CaptureConfiguration.current
        tall.geometry.roiHalfHeight = 2
        let adapter = FixtureAdapter(markers: marks, hypotheses: Self.code())
        let (session, outcome) = try await read(adapter, name == "leaves the image" ? tall : .current)
        let only = try #require(session.result.visualCandidates.first, "\(name)")
        #expect(only.pairId != nil)
        #expect(only.reasonCode == code)
        #expect(only.roi == nil && !only.isReadable)
        #expect(outcome?.disposition == BandDecision(.retry, code))
        #expect(adapter.payloadReads.isEmpty)
    }

    @Test func coordinatesOutsideTheImageAreInvalidEvidence() async throws {
        let bad = MarkerObservation(quad: [NormalizedPoint(x: -0.1, y: 0.4), NormalizedPoint(x: 0.1, y: 0.4),
                                           NormalizedPoint(x: 0.1, y: 0.5), NormalizedPoint(x: -0.1, y: 0.5)],
                                    baselineAngleDegrees: 0, inkBefore: false, inkAfter: true)
        let (session, _) = try await read(FixtureAdapter(markers: [bad, Self.pair[1]]))
        #expect(session.result.frameReasonCode == "invalid-evidence")
    }

    /// v1 applies no perspective or projective transform: a pair whose ROI
    /// carries one is refused before any read.
    @Test func aProjectiveTransformIsRefused() async throws {
        let adapter = FixtureAdapter(markers: Self.pair, hypotheses: Self.code())
        let pipeline = try Self.pipeline(adapter)
        let session = await pipeline.detect(imageData: Data(), imageId: "img")
        let frame = session.result
        let candidate = try #require(frame.visualCandidates.first)
        let roi = try #require(candidate.roi)
        var matrix = try #require(roi.rectificationTransform)
        matrix[6] = 0.001
        let projective = ROIDetection(pairId: roi.pairId, openingDetectionId: roi.openingDetectionId,
                                      closingDetectionId: roi.closingDetectionId, baselineId: roi.baselineId,
                                      polygon: roi.polygon, orientationDegrees: 0, rectificationTransform: matrix,
                                      rectificationSucceeded: nil)
        #expect(!projective.transformIsCropAndRotationOnly)
        let tampered = VisualCandidate(id: candidate.id, kind: candidate.kind, detectionIds: candidate.detectionIds,
                                       boxes: candidate.boxes, roi: projective, geometry: candidate.geometry,
                                       reasonCode: nil)
        let result = RecognitionResult(imageId: frame.imageId, engineId: frame.engineId,
                                       engineVersion: frame.engineVersion,
                                       configurationVersion: frame.configurationVersion,
                                       runtimeOutcome: .completed, imageWidth: frame.imageWidth,
                                       imageHeight: frame.imageHeight, fiducials: frame.fiducials, rois: [],
                                       candidates: [], visualCandidates: [tampered], reasonCodes: [],
                                       frameReasonCode: nil)
        let outcome = await pipeline.process(CaptureSession(result: result, image: session.image),
                                             candidateId: candidate.id)
        #expect(outcome.disposition == BandDecision(.retry, "unsupported-geometry"))
        #expect(adapter.payloadReads.isEmpty)
    }

    // MARK: Hypotheses and evidence

    /// Step 7: hypotheses keep the engine's order, and more than one means
    /// Clarify. The first is invalid and the second would pass; neither is
    /// moved or dropped.
    @Test func severalHypothesesKeepTheEngineOrderAndClarify() async throws {
        let hypotheses = [
            PayloadHypothesis(lines: [FixtureAdapter.line("copper #tag sky", 0.40)]),
            PayloadHypothesis(lines: [FixtureAdapter.line("copper lantern sky", 0.99)]),
        ]
        let (_, outcome) = try await read(FixtureAdapter(markers: Self.pair, hypotheses: hypotheses))
        let read = try #require(outcome)
        #expect(read.result.candidates.map(\.rawText) == ["copper #tag sky", "copper lantern sky"])
        #expect(read.result.candidates.map(\.candidateId) == ["img.p1.t1", "img.p1.t2"])
        #expect(read.decoded.map(\.textCandidateId) == ["img.p1.t1", "img.p1.t2"])
        #expect(read.decoded.map(\.band) == [.clarify, .clarify])
        #expect(read.decoded.map(\.reason) == ["multiple-hypotheses", "multiple-hypotheses"])
        #expect(read.decoded[1].canonical == "zz-copper-lantern-sky-zz")
    }

    @Test func moreHypothesesThanTheLimitAbstain() async throws {
        let hypotheses = Array(repeating: Self.code()[0], count: 5)
        let (_, outcome) = try await read(FixtureAdapter(markers: Self.pair, hypotheses: hypotheses))
        #expect(outcome?.decoded.map(\.band) == Array(repeating: .abstain, count: 5))
        #expect(outcome?.decoded.first?.reason == "invalid-hypothesis-count")
        #expect(outcome?.result.reasonCodes == ["hypothesis-limit-exceeded"])
    }

    @Test func aValidROIWithNoTextRetries() async throws {
        let blank = [PayloadHypothesis(lines: [FixtureAdapter.line("  ", 0.9)])]
        let (_, outcome) = try await read(FixtureAdapter(markers: Self.pair, hypotheses: blank))
        #expect(outcome?.disposition == BandDecision(.retry, "no-payload-text"))
        #expect(outcome?.result.rois.first?.rectificationSucceeded == true)
        #expect(outcome?.result.candidates.isEmpty == true)
    }

    @Test func aRecognizerFailureNeverAccepts() async throws {
        let adapter = FixtureAdapter(markers: Self.pair, hypotheses: Self.code())
        adapter.failPayload = true
        let (_, outcome) = try await read(adapter)
        #expect(outcome?.disposition == BandDecision(.abstain, "recognizer-unavailable"))
        #expect(outcome?.result.runtimeOutcome == .exception)
        #expect(outcome?.decoded.isEmpty == true)

        let unavailable = FixtureAdapter(markers: Self.pair)
        unavailable.failPrepare = true
        let (session, _) = try await read(unavailable)
        #expect(session.result.frameReasonCode == "recognizer-unavailable")
        #expect(session.image == nil)
    }

    /// Missing confidence stays missing (FR-004): no score is made up, and
    /// the band is Clarify, never Accept.
    @Test func missingConfidenceIsExplicitAndClarifies() async throws {
        var open = CaptureConfiguration.current
        open.qualification = .passReceipt(sha256: String(repeating: "a", count: 64))
        open.confirmOnly = false
        let (_, outcome) = try await read(FixtureAdapter(markers: Self.pair, hypotheses: Self.code(nil)), open)
        let text = try #require(outcome?.result.candidates.first)
        #expect(text.confidenceStatus == .notAvailable)
        #expect(text.confidence == nil)
        #expect(outcome?.decoded.map(\.band) == [.clarify])
        #expect(outcome?.decoded.first?.reason == "confidence-unavailable")
    }

    /// T022: a word takes its line's confidence, and a candidate the lowest
    /// line among its words. A line with no part does not count.
    @Test func confidenceIsTheLowestLineThatHoldsAPart() async throws {
        let lines = [FixtureAdapter.line("copper lantern", 0.90, y: 0.1), FixtureAdapter.line("-", 0.10, y: 0.4),
                     FixtureAdapter.line("sky", 0.45, y: 0.7)]
        let (_, outcome) = try await read(FixtureAdapter(markers: Self.pair, hypotheses: [PayloadHypothesis(lines: lines)]))
        let text = try #require(outcome?.result.candidates.first)
        #expect(text.confidence == 0.45)
        #expect(outcome?.decoded.map(\.canonical) == ["zz-copper-lantern-sky-zz"])
        #expect(outcome?.decoded.map(\.band) == [.retry])
    }

    @Test func linesJoinInReadingOrderInsideTheCrop() async throws {
        let lines = [FixtureAdapter.line("sky", 0.9, y: 0.6), FixtureAdapter.line("copper lantern", 0.9, y: 0.1)]
        let (_, outcome) = try await read(FixtureAdapter(markers: Self.pair, hypotheses: [PayloadHypothesis(lines: lines)]))
        #expect(outcome?.result.candidates.first?.rawText == "copper lantern sky")
        #expect(outcome?.result.candidates.first?.regions.count == 2)
    }

    // MARK: Confirm-only (T102)

    @Test func confirmOnlyTurnsCameraAcceptIntoClarifyWithTheFullCode() async throws {
        let (_, outcome) = try await read(FixtureAdapter(markers: Self.pair, hypotheses: Self.code()))
        let decoded = try #require(outcome?.decoded.first)
        #expect(CaptureConfiguration.current.confirmOnly)
        #expect(!CaptureConfiguration.current.isQualified)
        #expect(decoded.baseBand == .accept)
        #expect(decoded.band == .clarify)
        #expect(decoded.reason == "confirm-only")
        #expect(decoded.canonical == "zz-copper-lantern-sky-zz")
    }

    @Test(arguments: [
        (false, true, Band.clarify), (false, false, .clarify), (true, true, .clarify), (true, false, .accept),
    ])
    func cameraAcceptNeedsAReceiptAndTheSettingOff(_ qualified: Bool, _ confirmOnly: Bool, _ expected: Band)
        async throws {
        var configuration = CaptureConfiguration.current
        configuration.qualification = qualified ? .passReceipt(sha256: String(repeating: "b", count: 64)) : .notQualified
        configuration.confirmOnly = confirmOnly
        #expect(configuration.cameraAcceptEnabled == (expected == .accept))
        let (_, outcome) = try await read(FixtureAdapter(markers: Self.pair, hypotheses: Self.code()), configuration)
        #expect(outcome?.decoded.map(\.band) == [expected])
    }

    /// Confirm-only changes only camera Accept: other camera bands, and
    /// every typed band, are as the table says.
    @Test func confirmOnlyLeavesEveryOtherBandAndTypedInputAlone() throws {
        for band in [Band.clarify, .retry, .abstain] {
            let decision = BandDecision(band, "x")
            #expect(Bands.applyConfirmOnly(decision, source: .camera) == decision)
        }
        #expect(Bands.applyConfirmOnly(BandDecision(.accept, "typed"), source: .typed).band == .accept)
        let list = try Self.fixture7()
        let typed = ReadPipeline(classifyList: list, checkList: list).readTyped("zz-copper-lantern-sky-zz")
        #expect(typed.map(\.band) == .success(.accept))
    }

    // MARK: The boundary

    /// The serialized shape matches the upstream adapter-result.schema.json
    /// at the pin, field for field, and carries no image data.
    @Test func theEvidenceEncodesWithTheUpstreamFieldNames() async throws {
        let (_, outcome) = try await read(FixtureAdapter(markers: Self.pair, hypotheses: Self.code()))
        let result = try #require(outcome?.result)
        let data = try JSONEncoder().encode(result)
        let object = try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        #expect(Set(object.keys) == ["runtime_outcome", "fiducials", "rois", "candidates", "reason_codes"])
        let fiducial = try #require((object["fiducials"] as? [[String: Any]])?.first)
        #expect(Set(fiducial.keys) == ["detection_id", "baseline_id", "role", "pair_id", "box"])
        let roi = try #require((object["rois"] as? [[String: Any]])?.first)
        #expect(Set(roi.keys) == ["pair_id", "opening_detection_id", "closing_detection_id", "baseline_id", "polygon",
                                  "orientation_degrees", "rectification_transform", "rectification_succeeded"])
        let candidate = try #require((object["candidates"] as? [[String: Any]])?.first)
        #expect(Set(candidate.keys) == ["candidate_id", "pair_id", "raw_text", "confidence_status", "confidence",
                                        "regions"])
        #expect(Set((fiducial["box"] as? [String: Any])?.keys ?? [:].keys) == ["x", "y", "width", "height"])
    }

    /// No evidence type holds image bytes, a crop, or a file location; only
    /// the attempt's CaptureSession holds the upright image, in memory.
    @Test func theEvidenceHoldsNoImageCropOrPath() async throws {
        let (session, outcome) = try await read(FixtureAdapter(markers: Self.pair, hypotheses: Self.code()))
        func check(_ value: Any, _ path: String) {
            // `is CGImage` matches any value (CF bridging), so the type is named.
            let type = String(describing: Swift.type(of: value))
            #expect(!(value is Data) && !(value is URL) && !type.contains("CGImage") && !type.contains("CaptureImage"),
                    "\(path) is \(type)")
            for child in Mirror(reflecting: value).children {
                check(child.value, path + "." + (child.label ?? "?"))
            }
        }
        check(session.result, "frame")
        check(try #require(outcome).result, "read")
        check(try #require(outcome).decoded, "decoded")
    }

    /// The `capture_band` camera rows of the band table, now through the
    /// evidence path: line confidences in a TextCandidate, one band out,
    /// before confirm-only mode, which the shipped setting then applies.
    @Test(arguments: BandTests.cameraTableRows)
    func captureBandRowsThroughTheEvidence(_ row: Row) async throws {
        let items = try #require(row.fields["rows"] as? [[String: Any]])
        let words = try items.map {
            (token: try #require($0["token"] as? String), line: try #require($0["line"] as? Int),
             confidence: try #require($0["confidence"] as? Double))
        }
        let byLine = Dictionary(grouping: words, by: \.line)
        let lines = byLine.keys.sorted().map { index in
            FixtureAdapter.line(byLine[index]!.map(\.token).joined(separator: " "),
                                byLine[index]!.map(\.confidence).min(), y: 0.1 + Double(index) * 0.4)
        }
        let band = try #require(row.fields["band"] as? String)
        let (_, outcome) = try await read(FixtureAdapter(markers: Self.pair, hypotheses: [PayloadHypothesis(lines: lines)]))
        let decoded = try #require(outcome?.decoded.first)
        #expect(decoded.canonical == row.fields["canonical"] as? String)
        #expect(decoded.baseBand.rawValue == band)
        #expect(decoded.band == (band == "accept" ? .clarify : decoded.baseBand))
    }
}
