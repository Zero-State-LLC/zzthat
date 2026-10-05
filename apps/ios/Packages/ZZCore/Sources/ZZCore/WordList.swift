import Foundation

/// One versioned wordlist (spec 003 FR-016, FR-017). Line order is the index.
public struct WordList: Sendable, Hashable {
    public let version: String
    public let words: [String]
    private let indexes: [String: Int]

    public init(version: String, words: [String]) throws(WordListError) {
        guard !words.isEmpty else { throw .empty(version) }
        var indexes: [String: Int] = [:]
        for (offset, word) in words.enumerated() {
            guard !word.isEmpty, word.unicodeScalars.allSatisfy({ ("a"..."z").contains($0) }) else {
                throw .invalidWord(version, line: offset + 1)
            }
            guard !["zz", "fn", "run"].contains(word) else { throw .reservedWord(version, line: offset + 1) }
            guard indexes[word] == nil else { throw .duplicate(version, line: offset + 1) }
            indexes[word] = offset
        }
        self.version = version
        self.words = words
        self.indexes = indexes
    }

    public func index(of word: String) -> Int? { indexes[word] }

    public static func == (lhs: WordList, rhs: WordList) -> Bool {
        lhs.version == rhs.version && lhs.words == rhs.words
    }

    public func hash(into hasher: inout Hasher) {
        hasher.combine(version)
        hasher.combine(words)
    }
}

public enum WordListError: Error, Sendable, Equatable {
    case empty(String)
    case invalidWord(String, line: Int)
    case reservedWord(String, line: Int)
    case duplicate(String, line: Int)
    case unreadable(String)
}

/// Loads the bundled lists from contracts/wordlists/ (spec 003 Version id:
/// `<version>.txt`, one word per line). Reports and other files are skipped.
public enum WordListLoader {
    public static func load(directory: URL) throws(WordListError) -> [WordList] {
        let names: [String]
        do {
            names = try FileManager.default.contentsOfDirectory(atPath: directory.path)
        } catch {
            return []
        }
        var lists: [WordList] = []
        for name in names.sorted() where name.hasSuffix(".txt") && !name.contains(".report.") {
            let version = String(name.dropLast(4))
            let url = directory.appendingPathComponent(name)
            guard let data = FileManager.default.contents(atPath: url.path),
                  let text = String(data: data, encoding: .utf8) else { throw .unreadable(version) }
            lists.append(try parse(version: version, text: text))
        }
        return lists
    }

    public static func parse(version: String, text: String) throws(WordListError) -> WordList {
        var lines = text.split(separator: "\n", omittingEmptySubsequences: false)
            .map { $0.hasSuffix("\r") ? String($0.dropLast()) : String($0) }
        if lines.last == "" { lines.removeLast() }
        return try WordList(version: version, words: lines)
    }

    /// FR-044: the list for a local check word, only when its version equals
    /// the server's `wordlist_version`.
    public static func matching(_ lists: [WordList], serverVersion: String?) -> WordList? {
        guard let serverVersion else { return nil }
        return lists.first { $0.version == serverVersion }
    }
}
