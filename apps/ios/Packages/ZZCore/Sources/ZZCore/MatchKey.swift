// The field-code matching key, zzThis Section 2.2a G10 and spec 003 FR-024.
// The key covers the parts only (D-2026-10-04-09): number words become digits,
// then lookalikes fold, then runs of all-digit parts join.

public enum MatchKey {
    static let fold: [Unicode.Scalar: Unicode.Scalar] = [
        "o": "0", "i": "1", "l": "1", "s": "5", "z": "2", "b": "8",
    ]

    /// The key of a canonical code, or nil when the text is not a code with parts.
    public static func key(_ canonical: String) -> String? {
        guard case .success(let code) = Grammar.parse(canonical), !code.words.isEmpty else { return nil }
        return key(parts: code.words)
    }

    public static func key(parts: [String]) -> String {
        let folded = parts.map { part -> String in
            let digits = Classifier.numberWords[part] ?? part
            return Scalars.string(digits.unicodeScalars.map { fold[$0] ?? $0 })
        }
        var joined: [String] = []
        var run = ""
        for part in folded {
            if !part.isEmpty, part.unicodeScalars.allSatisfy({ ("0"..."9").contains($0) }) {
                run += part
                continue
            }
            if !run.isEmpty {
                joined.append(run)
                run = ""
            }
            joined.append(part)
        }
        if !run.isEmpty { joined.append(run) }
        return joined.joined(separator: "-")
    }
}
