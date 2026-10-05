// The spec 003 check word (Prototype defaults, Q30): with word indexes d1 and
// d2 (0-based, in list order) and N the list length, which is prime, the check
// word is the word at index (d1 + 2 * d2) mod N. The client only verifies. The
// server chooses the words (ZQ11).

public enum CheckResult: Sendable, Hashable {
    case ok
    case checkMismatch
    /// A part that is not on the list, by 1-based position.
    case unknownWord(position: Int)
    case wrongLength

    /// The result name the vectors and the server use.
    public var name: String {
        switch self {
        case .ok: "ok"
        case .checkMismatch: "check-mismatch"
        case .unknownWord: "unknown-word"
        case .wrongLength: "wrong-length"
        }
    }
}

public enum CheckWord {
    /// Issued format (Q27): two data words and one check word.
    public static let dataWords = 2

    public static func checkIndex(_ d1: Int, _ d2: Int, listSize: Int) -> Int {
        (d1 + 2 * d2) % listSize
    }

    /// Verify a plain code against a list (spec 003 FR-018, Verify).
    public static func verify(_ code: ParsedCode, list: WordList) -> CheckResult {
        verify(parts: code.words, list: list)
    }

    public static func verify(parts: [String], list: WordList) -> CheckResult {
        var indexes: [Int] = []
        for (offset, part) in parts.enumerated() {
            guard let index = list.index(of: part) else { return .unknownWord(position: offset + 1) }
            indexes.append(index)
        }
        guard indexes.count == dataWords + 1 else { return .wrongLength }
        let expected = checkIndex(indexes[0], indexes[1], listSize: list.words.count)
        return indexes[2] == expected ? .ok : .checkMismatch
    }

    /// The issuer rule's allowed pairs, in list order. The server draws from
    /// these at random; tests use this to check the vectors' issuable codes.
    public static func issuableCodes(list: WordList) -> [String] {
        let n = list.words.count
        var codes: [String] = []
        for d1 in 0..<n {
            for d2 in 0..<n {
                let check = checkIndex(d1, d2, listSize: n)
                if d1 == d2 || d2 == 0 || (d1 + d2) % n == 0 || check == d1 || check == d2 { continue }
                codes.append(Grammar.format([list.words[d1], list.words[d2], list.words[check]]))
            }
        }
        return codes
    }
}
