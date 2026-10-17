// The spec 004 decision bands (Decision bands, Prototype band values, and
// Confirm-only mode), tasks.md T022 and T102. The camera precedence is the
// upstream ZZ-OCR-QUAL-001 decision-band-mapping.json `precedence` list, as
// zzThis packages/zz-core decisionBand.ts implements it, so the app bands a
// reading the way qualification scores it. The inputs are normalized
// evidence plus grammar and check-word results, never recognizer objects.
// The values live in CaptureConfiguration.swift.

public enum Band: String, Sendable, Hashable {
    case accept
    case clarify
    case retry
    case abstain
}

public enum ReadSource: Sendable, Hashable {
    case camera
    case typed
}

/// One word of a candidate with the line it came from (Client read pipeline
/// step 2). The band function takes these rows, not recognizer objects.
public struct BandRow: Sendable, Hashable {
    public let token: String
    public let lineIndex: Int
    public let lineConfidence: Double

    public init(token: String, lineIndex: Int, lineConfidence: Double) {
        self.token = token
        self.lineIndex = lineIndex
        self.lineConfidence = lineConfidence
    }
}

/// Candidate confidence as the evidence reports it. Missing stays missing.
public enum ConfidenceEvidence: Sendable, Hashable {
    case available(Double)
    case notAvailable
}

/// Everything the band function reads for one candidate.
public struct BandEvidence: Sendable, Hashable {
    public var source: ReadSource
    /// A capture issue the reason-code mapping found in the evidence.
    public var captureIssue: CaptureIssue?
    /// The decoded candidate, or nil when there is none.
    public var kind: ScanCandidateKind?
    /// How many text hypotheses the selected ROI returned.
    public var hypothesisCount: Int
    public var codeClass: CodeClass?
    /// The local check word, or nil when it did not run (FR-044).
    public var check: CheckResult?
    public var confidence: ConfidenceEvidence

    public init(source: ReadSource, captureIssue: CaptureIssue? = nil, kind: ScanCandidateKind?,
                hypothesisCount: Int = 1, codeClass: CodeClass?, check: CheckResult?,
                confidence: ConfidenceEvidence) {
        self.source = source
        self.captureIssue = captureIssue
        self.kind = kind
        self.hypothesisCount = hypothesisCount
        self.codeClass = codeClass
        self.check = check
        self.confidence = confidence
    }
}

/// A band and the rule that chose it, in the upstream reason vocabulary.
public struct BandDecision: Sendable, Hashable {
    public let band: Band
    public let reason: String

    public init(_ band: Band, _ reason: String) {
        self.band = band
        self.reason = reason
    }
}

public enum Bands {
    /// A candidate takes the lowest confidence among the lines its words came from.
    public static func candidateConfidence(_ rows: [BandRow]) -> Double? {
        var byLine: [Int: Double] = [:]
        for row in rows { byLine[row.lineIndex] = min(byLine[row.lineIndex] ?? row.lineConfidence, row.lineConfidence) }
        return byLine.values.min()
    }

    /// The band for one candidate under the spec 004 table, before
    /// confirm-only mode.
    public static func decide(_ evidence: BandEvidence,
                              configuration: CaptureConfiguration = .current) -> BandDecision {
        switch evidence.source {
        case .typed: typed(evidence)
        case .camera: camera(evidence, configuration: configuration)
        }
    }

    /// FR-025 and zzThis spec 004 FR-023: without a PASS receipt, or with
    /// the confirm-only setting on, a camera candidate that would be Accept
    /// is Clarify. Every other band, and every typed band, is unchanged.
    public static func applyConfirmOnly(_ decision: BandDecision, source: ReadSource,
                                        configuration: CaptureConfiguration = .current) -> BandDecision {
        guard source == .camera, decision.band == .accept, !configuration.cameraAcceptEnabled else { return decision }
        return BandDecision(.clarify, "confirm-only")
    }

    /// The decision-band-mapping.json precedence, top to bottom.
    static func camera(_ e: BandEvidence, configuration: CaptureConfiguration) -> BandDecision {
        // bare-mark-abstain, explicit-unreadable-capture-issue-retry
        if let issue = e.captureIssue { return BandDecision(issue.band, issue.rawValue) }
        switch e.kind {
        case .bare: return BandDecision(.abstain, CaptureIssue.bareMark.rawValue)
        case .partial: return BandDecision(.retry, CaptureIssue.missingMarker.rawValue)
        // no-candidate-retry
        case nil: return BandDecision(.retry, "no-candidate")
        case .code, .invalid: break
        }
        // invalid-thresholds-abstain
        let thresholds = configuration.thresholds
        guard thresholds.isValid else { return BandDecision(.abstain, "invalid-thresholds") }
        // invalid-hypothesis-count-abstain: below one, or over the configured
        // limit, or no limit configured.
        guard let maxHypotheses = configuration.limits.maxHypotheses,
              (1...max(maxHypotheses, 1)).contains(e.hypothesisCount) else {
            return BandDecision(.abstain, "invalid-hypothesis-count")
        }
        // multiple-hypotheses-clarify
        if e.hypothesisCount > 1 { return BandDecision(.clarify, "multiple-hypotheses") }
        // malformed-payload-grammar-abstain
        guard case .code(let code) = e.kind else {
            if case .invalid(let reason, _) = e.kind { return BandDecision(.abstain, "grammar:\(reason.rawValue)") }
            return BandDecision(.abstain, "grammar")
        }
        // handle-name-field-or-near-word-clarify (FR-010, FR-015)
        if code.kind == .handle || code.kind == .name {
            return BandDecision(.clarify, "manual-confirmation:\(code.kind.rawValue)")
        }
        let codeClass = e.codeClass ?? .field
        if codeClass != .word { return BandDecision(.clarify, "manual-confirmation:\(codeClass.name)") }
        // wrong-check-word-length-abstain, check-word-mismatch-or-unknown-word-clarify.
        // No local check (the list versions differ, FR-044) skips both.
        switch e.check {
        case .wrongLength: return BandDecision(.abstain, "checkword:wrong-length")
        case .checkMismatch, .unknownWord: return BandDecision(.clarify, "checkword:\(e.check!.name)")
        case .ok, nil: break
        }
        // missing-confidence-clarify, then the two thresholds, then accept.
        switch e.confidence {
        case .notAvailable:
            return BandDecision(.clarify, "confidence-unavailable")
        case .available(let confidence):
            guard confidence.isFinite, (0...1).contains(confidence) else {
                return BandDecision(.abstain, "invalid-confidence")
            }
            if confidence < thresholds.retryBelowConfidence {
                return BandDecision(.retry, "confidence-below-retry-threshold")
            }
            if confidence < thresholds.acceptMinConfidence {
                return BandDecision(.clarify, "confidence-below-accept-threshold")
            }
            return BandDecision(.accept, "word-code-passed")
        }
    }

    /// The typed column: no confidence, and field codes, handles, and names
    /// open as typed.
    static func typed(_ e: BandEvidence) -> BandDecision {
        switch e.kind {
        case nil: return BandDecision(.abstain, "no-candidate")
        case .bare: return BandDecision(.abstain, CaptureIssue.bareMark.rawValue)
        case .partial: return BandDecision(.abstain, "partial")
        case .invalid(let reason, _): return BandDecision(.abstain, "grammar:\(reason.rawValue)")
        case .code: break
        }
        if e.check == .wrongLength { return BandDecision(.abstain, "checkword:wrong-length") }
        if case .confirm = e.codeClass { return BandDecision(.clarify, "manual-confirmation:confirm") }
        if e.check == .checkMismatch { return BandDecision(.clarify, "checkword:check-mismatch") }
        return BandDecision(.accept, "typed")
    }

    /// The band from word rows, under the spec 004 table and before
    /// confirm-only mode. A camera candidate with no rows has no confidence.
    /// - Parameters:
    ///   - candidate: the scanner candidate, or nil when the photo held none.
    ///   - codeClass: the classifier result for a code candidate.
    ///   - check: the local check word result, or nil when step 5 did not run
    ///     (no matching list version, FR-044).
    ///   - rows: the candidate's words with their line confidences. Typed
    ///     input passes none.
    public static func band(candidate: ScanCandidateKind?, codeClass: CodeClass?, check: CheckResult?,
                            rows: [BandRow], source: ReadSource,
                            configuration: CaptureConfiguration = .current) -> Band {
        let confidence = candidateConfidence(rows).map(ConfidenceEvidence.available) ?? .notAvailable
        let evidence = BandEvidence(source: source, kind: candidate, codeClass: codeClass, check: check,
                                    confidence: confidence)
        return decide(evidence, configuration: configuration).band
    }
}
