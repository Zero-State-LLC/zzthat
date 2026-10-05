// The spec 004 decision bands (Decision bands and Prototype band values).

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
/// step 1). The band function takes these rows, not recognizer objects.
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

public enum Bands {
    /// A candidate takes the lowest confidence among the lines its words came from.
    public static func candidateConfidence(_ rows: [BandRow]) -> Double? {
        var byLine: [Int: Double] = [:]
        for row in rows { byLine[row.lineIndex] = min(byLine[row.lineIndex] ?? row.lineConfidence, row.lineConfidence) }
        return byLine.values.min()
    }

    /// - Parameters:
    ///   - candidate: the scanner candidate, or nil when the photo held none.
    ///   - codeClass: the classifier result for a code candidate.
    ///   - check: the local check word result, or nil when step 4 did not run
    ///     (no matching list version, FR-044).
    ///   - rows: the candidate's words with their line confidences. Typed
    ///     input passes none.
    public static func band(candidate: ScanCandidateKind?, codeClass: CodeClass?, check: CheckResult?,
                            rows: [BandRow], source: ReadSource) -> Band {
        guard let candidate else { return source == .camera ? .retry : .abstain }
        switch candidate {
        case .invalid, .bare: return .abstain
        case .partial: return source == .camera ? .retry : .abstain
        case .code: break
        }
        if check == .wrongLength { return .abstain }
        let codeClass = codeClass ?? .field
        switch source {
        case .typed:
            if case .confirm = codeClass { return .clarify }
            if check == .checkMismatch { return .clarify }
            return .accept
        case .camera:
            let confidence = candidateConfidence(rows) ?? 0
            if confidence < BandThresholds.retryBelow { return .retry }
            if case .confirm = codeClass { return .clarify }
            if check == .checkMismatch { return .clarify }
            if codeClass != .word { return .clarify }
            if confidence < BandThresholds.accept { return .clarify }
            return .accept
        }
    }
}
