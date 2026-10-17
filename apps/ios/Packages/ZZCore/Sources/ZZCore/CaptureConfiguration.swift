// The one constants file for camera capture (spec.md FR-025, tasks.md T022
// and T102, recognition-adapter.md, Scope decisions and open gates).
//
// NOT QUALIFIED. Every value below is a prototype or conservative default,
// not a measured result. ZZ-OCR-QUAL-001 freezes detector limits, baseline
// tolerance, the interior ROI inset, the reason-code mapping, and the
// confidence mapping in versioned qualification configuration before final
// evaluation. Until a signed PASS receipt exists, camera capture is
// confirm-only: no camera candidate reaches Accept (zzThis D-2026-10-13-05).
//
// A value set to nil is a missing limit. The pipeline fails closed on a
// missing limit; it never infers one.

import Foundation

public struct CaptureConfiguration: Sendable, Hashable {
    /// Whether a signed ZZ-OCR-QUAL-001 PASS receipt covers this adapter and
    /// configuration. The receipt is identified by its SHA-256.
    public enum Qualification: Sendable, Hashable {
        case notQualified
        case passReceipt(sha256: String)
    }

    /// The spec 004 band thresholds (Q37 prototype values; the gate config's
    /// `accept_min_confidence` and `retry_below_confidence`).
    public struct Thresholds: Sendable, Hashable {
        public var acceptMinConfidence: Double
        public var retryBelowConfidence: Double

        public init(acceptMinConfidence: Double, retryBelowConfidence: Double) {
            self.acceptMinConfidence = acceptMinConfidence
            self.retryBelowConfidence = retryBelowConfidence
        }

        /// The upstream `validThresholds` rule: finite, in [0, 1], retry
        /// strictly below accept.
        public var isValid: Bool {
            acceptMinConfidence.isFinite && retryBelowConfidence.isFinite
                && (0...1).contains(acceptMinConfidence) && (0...1).contains(retryBelowConfidence)
                && retryBelowConfidence < acceptMinConfidence
        }
    }

    /// Hard limits for marker and pair candidates (FR-052, spec 004 FR-020).
    /// Exceeding one fails the whole frame closed, with no truncation and no
    /// payload OCR.
    public struct DetectorLimits: Sendable, Hashable {
        public var maxMarkers: Int?
        public var maxPairs: Int?
        /// Text hypotheses from one selected ROI. More is an invalid count.
        public var maxHypotheses: Int?

        public init(maxMarkers: Int?, maxPairs: Int?, maxHypotheses: Int?) {
            self.maxMarkers = maxMarkers
            self.maxPairs = maxPairs
            self.maxHypotheses = maxHypotheses
        }

        public var isComplete: Bool {
            [maxMarkers, maxPairs, maxHypotheses].allSatisfy { ($0 ?? 0) >= 1 }
        }
    }

    /// Same-baseline pairing and the interior ROI. Lengths are multiples of
    /// the marker height, measured in upright image pixels.
    public struct Geometry: Sendable, Hashable {
        /// Two markers share a baseline only when their baseline angles differ
        /// by at most this many degrees.
        public var baselineAngleToleranceDegrees: Double?
        /// ...and the second marker's center lies at most this far from the
        /// first one's baseline, across the reading direction.
        public var baselineOffsetTolerance: Double?
        /// ...and the taller marker is at most this many times the shorter.
        /// Unequal sizes beyond it read as perspective and do not pair.
        public var maxMarkerHeightRatio: Double?
        /// A pair whose baseline turns more than this from horizontal is
        /// unsupported geometry. Crop and rotation are allowed below it.
        public var maxRotationDegrees: Double?
        /// The gap kept between each marker patch and the interior ROI.
        public var roiInset: Double?
        /// Half the ROI height, across the reading direction.
        public var roiHalfHeight: Double?
        /// The narrowest ROI that is safe to read.
        public var minimumROIWidth: Double?

        public init(baselineAngleToleranceDegrees: Double?, baselineOffsetTolerance: Double?,
                    maxMarkerHeightRatio: Double?, maxRotationDegrees: Double?, roiInset: Double?,
                    roiHalfHeight: Double?, minimumROIWidth: Double?) {
            self.baselineAngleToleranceDegrees = baselineAngleToleranceDegrees
            self.baselineOffsetTolerance = baselineOffsetTolerance
            self.maxMarkerHeightRatio = maxMarkerHeightRatio
            self.maxRotationDegrees = maxRotationDegrees
            self.roiInset = roiInset
            self.roiHalfHeight = roiHalfHeight
            self.minimumROIWidth = minimumROIWidth
        }

        public var isComplete: Bool {
            let values = [baselineAngleToleranceDegrees, baselineOffsetTolerance, maxMarkerHeightRatio,
                          maxRotationDegrees, roiInset, roiHalfHeight, minimumROIWidth]
            guard values.allSatisfy({ $0.map { $0.isFinite && $0 >= 0 } ?? false }) else { return false }
            return (maxMarkerHeightRatio ?? 0) >= 1 && (roiHalfHeight ?? 0) > 0 && (minimumROIWidth ?? 0) > 0
        }
    }

    /// How a reason code in the evidence becomes a band. Codes that name a
    /// capture issue use the upstream decision-band-mapping.json
    /// `capture_issues`; the rest are frame or ROI dispositions. A code that
    /// is in neither table abstains.
    public struct ReasonCodeMapping: Sendable, Hashable {
        public var captureIssues: [String: CaptureIssue]
        public var dispositions: [String: Band]

        public init(captureIssues: [String: CaptureIssue], dispositions: [String: Band]) {
            self.captureIssues = captureIssues
            self.dispositions = dispositions
        }

        public func band(for reasonCode: String) -> Band {
            if let issue = captureIssues[reasonCode] { return issue.band }
            return dispositions[reasonCode] ?? .abstain
        }
    }

    /// How an engine's native score becomes candidate confidence.
    public enum ConfidenceMapping: String, Sendable, Hashable {
        /// The engine's 0-to-1 line score, used as is. Not yet shown to be
        /// meaningful by qualification.
        case engineNative = "engine-native-unqualified"
        /// Every line is reported without confidence.
        case unavailable
    }

    /// Names this set of values in evidence and logs.
    public var version: String
    public var qualification: Qualification
    /// T102: the one confirm-only setting. On by default.
    public var confirmOnly: Bool
    public var thresholds: Thresholds
    public var limits: DetectorLimits
    public var geometry: Geometry
    public var reasonCodes: ReasonCodeMapping
    public var confidenceMapping: ConfidenceMapping

    public init(version: String, qualification: Qualification, confirmOnly: Bool, thresholds: Thresholds,
                limits: DetectorLimits, geometry: Geometry, reasonCodes: ReasonCodeMapping,
                confidenceMapping: ConfidenceMapping) {
        self.version = version
        self.qualification = qualification
        self.confirmOnly = confirmOnly
        self.thresholds = thresholds
        self.limits = limits
        self.geometry = geometry
        self.reasonCodes = reasonCodes
        self.confidenceMapping = confidenceMapping
    }

    public var isQualified: Bool {
        if case .passReceipt = qualification { return true }
        return false
    }

    /// Camera Accept needs both a PASS receipt and the confirm-only setting
    /// turned off (FR-025). Either one missing keeps capture confirm-only.
    public var cameraAcceptEnabled: Bool { isQualified && !confirmOnly }

    /// The values the app ships with until qualification freezes its own.
    public static let current = CaptureConfiguration(
        version: "ios-capture-0-unqualified",
        qualification: .notQualified,
        confirmOnly: true,
        thresholds: Thresholds(acceptMinConfidence: 0.80, retryBelowConfidence: 0.50),
        limits: DetectorLimits(maxMarkers: 8, maxPairs: 4, maxHypotheses: 4),
        geometry: Geometry(baselineAngleToleranceDegrees: 6, baselineOffsetTolerance: 0.35,
                           maxMarkerHeightRatio: 1.5, maxRotationDegrees: 10, roiInset: 0.1,
                           roiHalfHeight: 0.9, minimumROIWidth: 0.5),
        reasonCodes: ReasonCodeMapping(
            captureIssues: [
                ReasonCode.missingOpeningFiducial.rawValue: .missingMarker,
                ReasonCode.missingClosingFiducial.rawValue: .missingMarker,
                ReasonCode.partialFiducial.rawValue: .missingMarker,
                ReasonCode.bareMark.rawValue: .bareMark,
            ],
            // recognition.md, Error mapping: unusable geometry and an empty
            // ROI retry; recognizer failure and unsafe evidence abstain.
            dispositions: [
                ReasonCode.noFiducial.rawValue: .retry,
                ReasonCode.markerLimitExceeded.rawValue: .retry,
                ReasonCode.pairLimitExceeded.rawValue: .retry,
                ReasonCode.unlocalizedMarker.rawValue: .retry,
                ReasonCode.unsupportedGeometry.rawValue: .retry,
                ReasonCode.unsafeROI.rawValue: .retry,
                ReasonCode.noPayloadText.rawValue: .retry,
                ReasonCode.hypothesisLimitExceeded.rawValue: .abstain,
                ReasonCode.recognizerUnavailable.rawValue: .abstain,
                ReasonCode.invalidConfiguration.rawValue: .abstain,
                ReasonCode.invalidEvidence.rawValue: .abstain,
            ]),
        confidenceMapping: .engineNative)
}

/// The upstream capture issues (zzThis packages/zz-core decisionBand.ts and
/// decision-band-mapping.json `capture_issues`).
public enum CaptureIssue: String, Sendable, Hashable {
    case missingMarker = "missing-marker"
    case cutoffMarker = "cutoff-marker"
    case blur
    case glare
    case bareMark = "bare-mark"

    public var band: Band { self == .bareMark ? .abstain : .retry }
}

/// Reason codes the pipeline writes into `RecognitionResult.reasonCodes`.
/// `missing-opening-fiducial`, `missing-closing-fiducial`, `partial-fiducial`,
/// and `bare-mark` are the names the upstream decoder replay reads
/// (fixture-adapter-results.json, ocr-qualification-decoder-replay.mjs). The
/// rest are app dispositions, named as the Android adapter names them (#85),
/// and mapped to bands by `CaptureConfiguration.ReasonCodeMapping`.
public enum ReasonCode: String, Error, Sendable, Hashable, CaseIterable {
    case missingOpeningFiducial = "missing-opening-fiducial"
    case missingClosingFiducial = "missing-closing-fiducial"
    /// A lone mark with other marks on both sides of it on its line.
    case partialFiducial = "partial-fiducial"
    case bareMark = "bare-mark"
    /// The photo holds no zz mark.
    case noFiducial = "no-fiducial"
    case markerLimitExceeded = "marker-limit-exceeded"
    case pairLimitExceeded = "pair-limit-exceeded"
    /// More text hypotheses from one ROI than the limit.
    case hypothesisLimitExceeded = "hypothesis-limit-exceeded"
    /// The engine found a mark it cannot place, which could hide a pair.
    case unlocalizedMarker = "unlocalized-marker"
    /// A turn past the limit, marks chained across baselines that do not all
    /// agree (a curved or wrapped surface), or any transform other than crop
    /// and rotation.
    case unsupportedGeometry = "unsupported-geometry"
    /// The interior ROI is too narrow, or leaves the image.
    case unsafeROI = "unsafe-roi"
    /// A valid ROI with no payload text.
    case noPayloadText = "no-payload-text"
    /// The engine failed to start, could not decode the photo, or failed on it.
    case recognizerUnavailable = "recognizer-unavailable"
    /// A limit or geometry value is missing or out of range.
    case invalidConfiguration = "invalid-configuration"
    /// Coordinates outside [0, 1], or ids that do not link pair, ROI, and marks.
    case invalidEvidence = "invalid-evidence"
}

/// The band thresholds by their earlier names. They read the one
/// configuration above.
public enum BandThresholds {
    /// Accept needs every word at this confidence or more.
    public static var accept: Double { CaptureConfiguration.current.thresholds.acceptMinConfidence }
    /// A word below this sends the candidate to Retry.
    public static var retryBelow: Double { CaptureConfiguration.current.thresholds.retryBelowConfidence }
}
