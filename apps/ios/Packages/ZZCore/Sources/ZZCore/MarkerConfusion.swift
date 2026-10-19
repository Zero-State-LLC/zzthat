// Camera marker lookalikes (zzThis spec 004 FR-024, D-2026-10-19-10).
// Detection only. The grammar, typed input, and voice input do not use this set.
// A tolerant token is never written into the code.

public enum MarkerGlyph: String, Sendable, Hashable, Encodable {
    case exact
    case tolerant
}

public enum MarkerConfusion {
    public static let version = "marker-confusion-v1"
    /// Whole tokens only. `ll`, `(22)`, `2z@`, and anything longer are not in it.
    public static let tolerant: Set<String> = ["z2", "2z", "22"]

    public static func isTolerantWholeToken(_ folded: String) -> Bool {
        folded.count == 2 && tolerant.contains(folded)
    }
}
