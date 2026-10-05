// Word code, field code, or confirm: zzThis Section 2.2a G1 and spec 003
// FR-021. The classifier runs after the parser and takes the wordlist as input
// (spec 003 FR-015).

public enum CodeClass: Sendable, Hashable {
    /// Every part is a wordlist word.
    case word
    /// A plain code that is not a word code and has no near-word.
    case field
    /// A plain code with at least one near-word. Keys are 1-based part
    /// positions. Candidates are sorted by distance, then by list index.
    case confirm(nearWords: [Int: [String]])
    case handle
    case name
    case bare

    /// The class name the vectors use.
    public var name: String {
        switch self {
        case .word: "word"
        case .field: "field"
        case .confirm: "confirm"
        case .handle: "handle"
        case .name: "name"
        case .bare: "bare"
        }
    }

    public var nearWords: [Int: [String]] {
        if case .confirm(let nearWords) = self { return nearWords }
        return [:]
    }
}

public enum Classifier {
    /// G1: a part within this Levenshtein distance of a list word is a near-word.
    /// A parameter until the real-photo test set measures it (spec 004 Q37).
    public static let maxNearDistance = 2

    /// G1 and G10 step 2: number words count as parts with a digit.
    static let numberWords: [String: String] = [
        "zero": "0", "one": "1", "two": "2", "three": "3", "four": "4",
        "five": "5", "six": "6", "seven": "7", "eight": "8", "nine": "9",
    ]

    public static func classify(_ code: ParsedCode, wordlist: [String],
                                maxNearDistance: Int = maxNearDistance) -> CodeClass {
        switch code.kind {
        case .bare: return .bare
        case .handle: return .handle
        case .name: return .name
        case .plain: break
        }
        let index = Dictionary(wordlist.enumerated().map { ($0.element, $0.offset) },
                               uniquingKeysWith: { first, _ in first })
        var nearWords: [Int: [String]] = [:]
        var allOnList = true
        for (offset, part) in code.words.enumerated() {
            if index[part] != nil { continue }
            allOnList = false
            guard isLettersOnly(part), numberWords[part] == nil else { continue }
            let candidates = wordlist.enumerated()
                .map { (word: $0.element, listIndex: $0.offset, distance: Levenshtein.distance(part, $0.element)) }
                .filter { $0.distance <= maxNearDistance }
                .sorted { ($0.distance, $0.listIndex) < ($1.distance, $1.listIndex) }
                .map(\.word)
            if !candidates.isEmpty { nearWords[offset + 1] = candidates }
        }
        if !nearWords.isEmpty { return .confirm(nearWords: nearWords) }
        return allOnList ? .word : .field
    }

    static func isLettersOnly(_ part: String) -> Bool {
        !part.isEmpty && part.unicodeScalars.allSatisfy { ("a"..."z").contains($0) }
    }
}

/// Levenshtein distance over lowercase ASCII letters (spec 003 FR-003): insert,
/// delete, or substitute one letter, each costing 1.
public enum Levenshtein {
    public static func distance(_ a: String, _ b: String) -> Int {
        let x = Array(a.unicodeScalars)
        let y = Array(b.unicodeScalars)
        if x.isEmpty { return y.count }
        if y.isEmpty { return x.count }
        var previous = Array(0...y.count)
        var current = Array(repeating: 0, count: y.count + 1)
        for i in 1...x.count {
            current[0] = i
            for j in 1...y.count {
                let cost = x[i - 1] == y[j - 1] ? 0 : 1
                current[j] = min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            swap(&previous, &current)
        }
        return previous[y.count]
    }
}
