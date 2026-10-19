import CryptoKit
import Foundation

/// Identity of one list (D-2026-10-19-13). The digest is SHA-256 of the
/// UTF-8 bytes of the words joined by newlines, with a trailing newline.
public struct ListIdentity: Sendable, Hashable {
    public let version: String
    public let sha256: String

    public init(version: String, sha256: String) {
        self.version = version
        self.sha256 = sha256
    }
}

extension WordList {
    public var identity: ListIdentity { ListIdentity(version: version, sha256: sha256) }

    public var sha256: String {
        let body = (words.joined(separator: "\n") + "\n").data(using: .utf8) ?? Data()
        return SHA256.hash(data: body).map { String(format: "%02x", $0) }.joined()
    }
}

/// One whole-code offer (zzThis spec 004 FR-026). It is not a replacement
/// of the engine hypothesis, and it is never Accept.
public struct DecoderOffer: Sendable, Hashable {
    public let words: [String]
    public let canonical: String
    public let cost: Int
    public let band: Band

    public init(words: [String], cost: Int) {
        self.words = words
        self.canonical = "zz-" + words.joined(separator: "-") + "-zz"
        self.cost = cost
        self.band = .clarify
    }
}

/// Joint decoder (D-2026-10-19-11, D-2026-10-19-16). Off unless the caller
/// turns it on. Release leaves it off until the bench passes. No part is
/// filled from the check equation. More than `maxOffers` yields none.
public enum JointDecoder {
    public static let minimumListSize = 1_000
    public static let floor = 0.34
    public static let marginEdits = 2
    public static let maxOffers = 3

    public static func offers(hypotheses: [[String]], list: WordList, allowlist: [ListIdentity],
                              enabled: Bool) -> [DecoderOffer] {
        guard enabled, list.words.count >= minimumListSize, allowlist.contains(list.identity) else { return [] }
        let parts = hypotheses.filter { $0.count == 3 }
        guard !parts.isEmpty else { return [] }
        let lengths = list.words.map(\.count)
        let shortest = lengths.min() ?? 0
        let longest = lengths.max() ?? 0
        let admissible = (0..<3).map { position in
            list.words.filter { word in
                parts.contains { hypothesis in
                    let part = hypothesis[position]
                    if part.isEmpty || part.count < shortest - 1 || part.count > longest + 1 { return false }
                    let distance = levenshtein(part, word)
                    return Double(distance) / Double(max(word.count, 1)) <= floor
                }
            }
        }
        guard admissible.allSatisfy({ !$0.isEmpty }) else { return [] }
        var found: [DecoderOffer] = []
        for a in admissible[0] {
            for b in admissible[1] {
                for c in admissible[2] {
                    guard CheckWord.verify(parts: [a, b, c], list: list) == .ok else { continue }
                    let cost = (0..<3).map { position in
                        parts.map { levenshtein($0[position], [a, b, c][position]) }.min() ?? Int.max
                    }.reduce(0, +)
                    found.append(DecoderOffer(words: [a, b, c], cost: cost))
                }
            }
        }
        guard let lowest = found.map(\.cost).min() else { return [] }
        let kept = found.filter { $0.cost <= lowest + marginEdits }
        guard kept.count <= maxOffers else { return [] }
        return kept.sorted { lhs, rhs in
            if lhs.cost != rhs.cost { return lhs.cost < rhs.cost }
            return lhs.words.lexicographicallyPrecedes(rhs.words)
        }
    }

    static func levenshtein(_ left: String, _ right: String) -> Int {
        let a = Array(left), b = Array(right)
        if a.isEmpty { return b.count }
        if b.isEmpty { return a.count }
        var previous = Array(0...b.count)
        for (i, ca) in a.enumerated() {
            var current = [i + 1]
            for (j, cb) in b.enumerated() {
                let replace = previous[j] + (ca == cb ? 0 : 1)
                current.append(min(current[j] + 1, previous[j + 1] + 1, replace))
            }
            previous = current
        }
        return previous[b.count]
    }
}
