// Write check (zzThis spec 004 FR-017, D-2026-10-19-15). A tolerant pair
// never passes. The rewrite sentence lives in copy.json; this verdict only
// refuses the pass.

public enum CreationCheck {
    public enum Verdict: String, Sendable, Hashable {
        case match
        case mismatch
        case rewriteMarks
    }

    public static func verdict(read: String?, minted: String, tolerantPair: Bool) -> Verdict {
        if tolerantPair { return .rewriteMarks }
        return read == minted ? .match : .mismatch
    }
}
