// The camera read pipeline behind the recognition boundary (runtime.md, Read
// pipeline; recognition-adapter.md, Runtime sequence; tasks.md T023):
//
// 1. the adapter normalizes the photo upright once;
// 2. it detects terminal zz marks, and MarkerPairing pairs them by
//    geometry and reading order only;
// 3. every candidate is shown; payload OCR waits for a selection unless one
//    complete pair is the only selectable candidate;
// 4. only a selected complete pair with a safe ROI is cropped and rotated;
// 5. the adapter reads only that crop;
// 6. the literal markers are put back around the payload and the shared
//    grammar, classifier, check word, and bands decide;
// 7. several hypotheses from one ROI keep the engine's order and Clarify.
//
// Nothing here sends a request, queries a resolver, or writes a file.

import CoreGraphics
import Foundation

/// One attempt: the frame's evidence, and the upright image in memory until
/// the attempt ends.
public struct CaptureSession: Sendable {
    public let result: RecognitionResult
    let image: CaptureImage?

    public init(result: RecognitionResult, image: CaptureImage?) {
        self.result = result
        self.image = image
    }

    /// The same attempt with the evidence a read added.
    public func with(_ result: RecognitionResult) -> CaptureSession {
        CaptureSession(result: result, image: image)
    }

    /// Step 3: the candidate to read without a tap, which exists only when a
    /// complete pair is the only selectable candidate.
    public var automaticSelection: VisualCandidate? {
        guard result.visualCandidates.count == 1, let only = result.visualCandidates.first,
              only.pairId != nil else { return nil }
        return only
    }
}

/// What processing one selected candidate produced.
public struct CaptureOutcome: Sendable {
    /// The frame's evidence, with the read pair's ROI and hypotheses added.
    public let result: RecognitionResult
    /// The decoded hypotheses, in the engine's order.
    public let decoded: [ReadCandidate]
    /// Set when there is nothing to decode: the candidate cannot be read
    /// (partial, bare, unsafe geometry), the ROI held no text, or the
    /// recognizer failed. Its band is the outcome.
    public let disposition: BandDecision?
    /// The reason code behind the disposition.
    public let reasonCode: String?
}

public struct CapturePipeline: Sendable {
    public let decoder: ReadPipeline
    public let configuration: CaptureConfiguration
    public let adapter: any RecognitionAdapter

    public init(decoder: ReadPipeline, configuration: CaptureConfiguration = .current,
                adapter: any RecognitionAdapter) {
        self.decoder = decoder
        self.configuration = configuration
        self.adapter = adapter
    }

    /// Steps 1 and 2. A recognizer failure fails the frame closed.
    public func detect(imageData: Data, imageId: String = UUID().uuidString) async -> CaptureSession {
        let image: CaptureImage
        do {
            image = try await adapter.prepare(imageData: imageData)
        } catch {
            return CaptureSession(result: failed(imageId, error), image: nil)
        }
        let markers: [MarkerObservation]
        do {
            markers = try await adapter.detectMarkers(in: image)
        } catch {
            return CaptureSession(result: failed(imageId, error, image: image), image: nil)
        }
        let result = MarkerPairing.frame(imageId: imageId, markers: markers, imageWidth: image.pixelWidth,
                                         imageHeight: image.pixelHeight, engineId: adapter.engineId,
                                         engineVersion: adapter.engineVersion, configuration: configuration)
        // A frame that failed closed keeps no image: nothing more can be read.
        return CaptureSession(result: result, image: result.frameReasonCode == nil ? image : nil)
    }

    /// The band for a whole frame that failed closed.
    public func frameDisposition(_ result: RecognitionResult) -> BandDecision? {
        result.frameReasonCode.map { BandDecision(configuration.reasonCodes.band(for: $0), $0) }
    }

    /// Steps 4 to 7 for the candidate the person selected, or the automatic one.
    public func process(_ session: CaptureSession, candidateId: String) async -> CaptureOutcome {
        switch check(session.result, candidateId: candidateId) {
        case .stop(let outcome):
            return outcome
        case .read(let pairId, let roi, let geometry):
            guard let image = session.image else {
                return stop(session.result, ReasonCode.recognizerUnavailable.rawValue)
            }
            let hypotheses: [PayloadHypothesis]
            do {
                hypotheses = try await adapter.readPayload(in: image, roi: geometry)
            } catch {
                let code = ReasonCode.recognizerUnavailable.rawValue
                return stop(session.result, code,
                            result: session.result.with(roi: roi.with(rectificationSucceeded: false), candidates: [],
                                                        reasonCodes: [code], runtimeOutcome: .exception))
            }
            return finish(session.result, pairId: pairId, roi: roi, geometry: geometry, hypotheses: hypotheses)
        }
    }

    /// Steps 4 to 7 with payload hypotheses already in hand, as a replay of
    /// recorded evidence runs them: the same checks, and no adapter call.
    public func process(_ result: RecognitionResult, candidateId: String,
                        hypotheses: [PayloadHypothesis]) -> CaptureOutcome {
        switch check(result, candidateId: candidateId) {
        case .stop(let outcome): outcome
        case .read(let pairId, let roi, let geometry):
            finish(result, pairId: pairId, roi: roi, geometry: geometry, hypotheses: hypotheses)
        }
    }

    enum Check {
        case stop(CaptureOutcome)
        case read(pairId: String, roi: ROIDetection, geometry: ROIGeometry)
    }

    func stop(_ frame: RecognitionResult, _ code: String, result: RecognitionResult? = nil) -> CaptureOutcome {
        CaptureOutcome(result: result ?? frame, decoded: [],
                       disposition: BandDecision(configuration.reasonCodes.band(for: code), code), reasonCode: code)
    }

    /// Whether the candidate may reach payload OCR, and on what ROI.
    func check(_ frame: RecognitionResult, candidateId: String) -> Check {
        if let frameCode = frame.frameReasonCode { return .stop(stop(frame, frameCode)) }
        guard let candidate = frame.visualCandidates.first(where: { $0.id == candidateId }) else {
            return .stop(stop(frame, ReasonCode.invalidEvidence.rawValue))
        }
        // A partial, a bare mark, or unsafe geometry: no payload OCR.
        if let code = candidate.reasonCode { return .stop(stop(frame, code)) }
        guard candidate.isReadable, let pairId = candidate.pairId, let roi = candidate.roi,
              let geometry = candidate.geometry else { return .stop(stop(frame, ReasonCode.invalidEvidence.rawValue)) }
        // The pair is eligible only when both ids name exact detections on the
        // ROI's own non-null baseline (ZZ-OCR-QUAL-001 Recognition stage).
        let byId = Dictionary(frame.fiducials.map { ($0.detectionId, $0) }, uniquingKeysWith: { first, _ in first })
        guard byId.count == frame.fiducials.count, roi.pairId == pairId,
              candidate.detectionIds == [roi.openingDetectionId, roi.closingDetectionId],
              let opening = byId[roi.openingDetectionId], let closing = byId[roi.closingDetectionId],
              opening.baselineId == roi.baselineId, closing.baselineId == roi.baselineId,
              roi.polygon.allSatisfy(\.isValid) else { return .stop(stop(frame, ReasonCode.invalidEvidence.rawValue)) }
        guard roi.transformIsCropAndRotationOnly else {
            return .stop(stop(frame, ReasonCode.unsupportedGeometry.rawValue))
        }
        return .read(pairId: pairId, roi: roi, geometry: geometry)
    }

    func finish(_ frame: RecognitionResult, pairId: String, roi: ROIDetection, geometry: ROIGeometry,
                hypotheses: [PayloadHypothesis]) -> CaptureOutcome {
        let read = roi.with(rectificationSucceeded: true)
        let candidates = hypotheses.enumerated().compactMap { index, hypothesis in
            Self.textCandidate(id: "\(pairId).t\(index + 1)", pairId: pairId, hypothesis: hypothesis,
                               geometry: geometry, mapping: configuration.confidenceMapping)
        }
        guard !candidates.isEmpty else {
            let code = ReasonCode.noPayloadText.rawValue
            return stop(frame, code, result: frame.with(roi: read, candidates: [], reasonCodes: [code]))
        }
        let overLimit = candidates.count > (configuration.limits.maxHypotheses ?? 0)
        let result = frame.with(roi: read, candidates: candidates,
                                reasonCodes: overLimit ? [ReasonCode.hypothesisLimitExceeded.rawValue] : [])
        return CaptureOutcome(result: result, decoded: decoder.decode(result, configuration: configuration),
                              disposition: nil, reasonCode: nil)
    }

    func failed(_ imageId: String, _ error: Error, image: CaptureImage? = nil) -> RecognitionResult {
        // A mark the engine found but cannot place is evidence, not a crash.
        let unlocalized = (error as? RecognitionFailure) == .unlocalizedMarker
        return .failed(imageId: imageId, engineId: adapter.engineId, engineVersion: adapter.engineVersion,
                       configurationVersion: configuration.version,
                       reasonCode: unlocalized ? .unlocalizedMarker : .recognizerUnavailable,
                       runtimeOutcome: unlocalized ? .completed : .exception,
                       imageWidth: image?.pixelWidth ?? 0, imageHeight: image?.pixelHeight ?? 0)
    }

    /// One payload hypothesis as evidence: its lines in reading order inside
    /// the crop, joined with spaces, without the joining hyphens at either
    /// end; the lowest line confidence among lines
    /// that hold a payload part; and the line regions in source coordinates.
    /// A hypothesis with no text is not a candidate.
    static func textCandidate(id: String, pairId: String, hypothesis: PayloadHypothesis, geometry: ROIGeometry,
                              mapping: CaptureConfiguration.ConfidenceMapping) -> TextCandidate? {
        let ordered = ReadPipeline.readingOrder(boxes: hypothesis.lines.map(\.cropBox)).map { hypothesis.lines[$0] }
        let lines = ordered.filter { !Scalars.trimmed(Scalars.of($0.text)).isEmpty }
        guard !lines.isEmpty else { return nil }
        let provenance = lines.map { line in
            LineProvenance(text: line.text, confidence: mapping == .engineNative ? line.confidence : nil,
                           region: geometry.sourceBox(line.cropBox))
        }
        // Every part between the markers counts, the check word included; a
        // line with no part (only separators) does not.
        let counted = provenance.filter { !Scalars.split(Scalars.of($0.text), where: Scalars.isSeparator).isEmpty }
        let scores = counted.map(\.confidence)
        let confidence: Double? = scores.isEmpty || scores.contains(where: { $0 == nil }) ? nil : scores.compactMap { $0 }.min()
        // `raw_text` is payload text only (zzThis qualification/scoring.md
        // row 3): the hyphens that join the payload to the marks are not part
        // of it, so they are trimmed at both ends, as on Android.
        let joined = Scalars.of(lines.map(\.text).joined(separator: " "))
        let isJoin = { (scalar: Unicode.Scalar) in Scalars.isSeparator(scalar) || Scalars.isDash(scalar) }
        let rawText = joined.firstIndex(where: { !isJoin($0) }).flatMap { start in
            joined.lastIndex(where: { !isJoin($0) }).map { Scalars.string(Array(joined[start...$0])) }
        } ?? ""
        return TextCandidate(candidateId: id, pairId: pairId, rawText: rawText,
                             confidenceStatus: confidence == nil ? .notAvailable : .available,
                             confidence: confidence, regions: provenance.compactMap(\.region), lines: provenance)
    }
}

extension ReadPipeline {
    /// Steps 6 and 7 on the evidence of one read pair: each hypothesis, in
    /// the engine's order, becomes the literal code `zz-<payload>-zz` for the
    /// shared grammar, classifier, check word, and bands. Nothing is
    /// reordered or dropped by confidence, validity, or wordlist distance.
    public func decode(_ result: RecognitionResult,
                       configuration: CaptureConfiguration = .current) -> [ReadCandidate] {
        let count = result.candidates.count
        // The box drawn for a reading covers the whole code: both marks and the ROI.
        let pairBox: CGRect? = result.rois.first.flatMap { roi in
            guard var box = NormalizedBox.bounding(roi.polygon)?.rect else { return nil }
            for marker in result.fiducials
            where [roi.openingDetectionId, roi.closingDetectionId].contains(marker.detectionId) {
                box = box.union(marker.box.rect)
            }
            return box
        }
        return result.candidates.enumerated().map { index, text in
            let literal = "zz-" + text.rawText + "-zz"
            let kind: ScanCandidateKind
            switch Grammar.parse(literal) {
            case .success(let code) where code.kind != .bare: kind = .code(code)
            case .success: kind = .invalid(reason: .noContent, text: literal)
            case .failure(let reason): kind = .invalid(reason: reason, text: literal)
            }
            let (codeClass, check) = classifyAndCheck(kind)
            let confidence: ConfidenceEvidence = text.confidenceStatus == .available
                ? text.confidence.map(ConfidenceEvidence.available) ?? .notAvailable : .notAvailable
            let evidence = BandEvidence(source: .camera, kind: kind, hypothesisCount: count, codeClass: codeClass,
                                        check: check, confidence: confidence)
            let base = Bands.decide(evidence, configuration: configuration)
            let final = Bands.applyConfirmOnly(base, source: .camera, configuration: configuration)
            let rows: [BandRow] = text.lines.enumerated().flatMap { lineIndex, line -> [BandRow] in
                guard let score = line.confidence else { return [] }
                return Scalars.split(Scalars.of(line.text), where: Scalars.isSeparator).map {
                    BandRow(token: Scalars.string($0), lineIndex: lineIndex, lineConfidence: score)
                }
            }
            let boxes = pairBox.map { [$0] } ?? text.regions.map(\.rect)
            var candidate = ReadCandidate(id: index, kind: kind, codeClass: codeClass, check: check,
                                          band: final.band, rows: rows, boxes: boxes)
            candidate.baseBand = base.band
            candidate.reason = final.reason
            candidate.pairId = text.pairId
            candidate.textCandidateId = text.candidateId
            return candidate
        }
    }
}
