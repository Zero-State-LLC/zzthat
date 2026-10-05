import Testing
@testable import ZZCore

/// The classifier (G1a), matching key (G10), and check word (spec 003) rows of
/// contracts/vectors.json (SC-1).
@Suite("Classifier, matching key, and check word vectors")
struct CoreVectorTests {
    nonisolated(unsafe) static let classifier = Vectors.section("classifier")
    static let classifierRows = Row.list(classifier["rows"] as? [[String: Any]] ?? [])
    static let matchRows = Row.list(Vectors.rows("match_key"))
    nonisolated(unsafe) static let checkSection = Vectors.section("check_word")
    static let verifyRows = Row.list(checkSection["verify"] as? [[String: Any]] ?? [])

    static func fixtureList() throws -> WordList {
        let words = try #require(checkSection["wordlist"] as? [String])
        return try WordList(version: "fixture-7", words: words)
    }

    @Test func rowsAreLoaded() {
        #expect(!Self.classifierRows.isEmpty)
        #expect(!Self.matchRows.isEmpty)
        #expect(!Self.verifyRows.isEmpty)
    }

    @Test(arguments: classifierRows)
    func classifierRow(_ row: Row) throws {
        let wordlist = try #require(Self.classifier["wordlist"] as? [String])
        let maxDistance = try #require(Self.classifier["max_near_distance"] as? Int)
        #expect(Self.classifier["distance"] as? String == "levenshtein")
        #expect(maxDistance == Classifier.maxNearDistance)
        let input = try #require(row.fields["input"] as? String)
        guard case .success(let code) = Grammar.parse(input) else {
            Issue.record("\(input) does not parse")
            return
        }
        let result = Classifier.classify(code, wordlist: wordlist, maxNearDistance: maxDistance)
        #expect(result.name == row.fields["class"] as? String)
        let expected = try #require(row.fields["near_words"] as? [String: [String]])
        let actual = Dictionary(uniqueKeysWithValues: result.nearWords.map { (String($0.key), $0.value) })
        #expect(actual == expected)
    }

    @Test(arguments: matchRows)
    func matchKeyRow(_ row: Row) throws {
        let a = try #require(row.fields["a"] as? String)
        let b = try #require(row.fields["b"] as? String)
        let same = try #require(row.fields["same_key"] as? Bool)
        let keyA = try #require(MatchKey.key(a))
        let keyB = try #require(MatchKey.key(b))
        #expect((keyA == keyB) == same, "\(keyA) vs \(keyB)")
    }

    @Test(arguments: verifyRows)
    func verifyRow(_ row: Row) throws {
        let list = try Self.fixtureList()
        #expect(Self.checkSection["data_words"] as? Int == CheckWord.dataWords)
        let input = try #require(row.fields["input"] as? String)
        guard case .success(let code) = Grammar.parse(input) else {
            Issue.record("\(input) does not parse")
            return
        }
        #expect(CheckWord.verify(code, list: list).name == row.fields["result"] as? String)
    }

    @Test func issuableCodesVerifyAndMatchTheIssuerRule() throws {
        let list = try Self.fixtureList()
        let issuable = try #require(Self.checkSection["issuable_codes"] as? [String])
        #expect(CheckWord.issuableCodes(list: list) == issuable)
        for canonical in issuable {
            guard case .success(let code) = Grammar.parse(canonical) else {
                Issue.record("\(canonical) does not parse")
                continue
            }
            #expect(CheckWord.verify(code, list: list) == .ok, "\(canonical)")
        }
    }

    /// spec 003 FR-020 (a), exhaustive on the fixture list: every single wrong
    /// word in every position, and every swap of two words, is detected.
    @Test func everySubstitutionAndSwapIsDetected() throws {
        let list = try Self.fixtureList()
        for canonical in CheckWord.issuableCodes(list: list) {
            guard case .success(let code) = Grammar.parse(canonical) else { continue }
            for position in 0..<3 {
                for word in list.words where word != code.words[position] {
                    var parts = code.words
                    parts[position] = word
                    #expect(CheckWord.verify(parts: parts, list: list) == .checkMismatch, "\(parts)")
                }
            }
            for (i, j) in [(0, 1), (0, 2), (1, 2)] {
                var parts = code.words
                parts.swapAt(i, j)
                #expect(CheckWord.verify(parts: parts, list: list) == .checkMismatch, "\(parts)")
            }
        }
    }

    /// FR-020 (a), the per-position property: with the other words fixed, the
    /// check index is one-to-one in each data word, for any prime list size.
    @Test(arguments: [7, 2663])
    func checkIndexIsOneToOnePerPosition(_ n: Int) {
        for fixed in [0, 1, n / 2, n - 1] {
            #expect(Set((0..<n).map { CheckWord.checkIndex($0, fixed, listSize: n) }).count == n)
            #expect(Set((0..<n).map { CheckWord.checkIndex(fixed, $0, listSize: n) }).count == n)
        }
    }

    @Test func unknownWordNamesThePosition() throws {
        let list = try Self.fixtureList()
        #expect(CheckWord.verify(parts: ["copper", "hello", "sky"], list: list) == .unknownWord(position: 2))
    }
}
