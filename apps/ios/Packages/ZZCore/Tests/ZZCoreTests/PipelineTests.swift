import CoreGraphics
import Foundation
import Testing
@testable import ZZCore

@Suite("Read pipeline and list loader")
struct PipelineTests {
    static func fixture7() throws -> WordList {
        let words = try #require(Vectors.section("check_word")["wordlist"] as? [String])
        return try WordList(version: "fixture-7", words: words)
    }

    @Test func readingOrderIsTopToBottomThenLeftToRight() {
        let lines = [
            RecognizedLine(text: "sky-zz", confidence: 1, box: CGRect(x: 0.1, y: 0.5, width: 0.3, height: 0.1)),
            RecognizedLine(text: "lantern", confidence: 1, box: CGRect(x: 0.5, y: 0.21, width: 0.3, height: 0.1)),
            RecognizedLine(text: "zz-copper", confidence: 1, box: CGRect(x: 0.1, y: 0.2, width: 0.3, height: 0.1)),
        ]
        #expect(ReadPipeline.readingOrder(lines).map(\.text) == ["zz-copper", "lantern", "sky-zz"])
        let result = ReadPipeline(classifyList: nil, checkList: nil).read(lines: lines)
        #expect(result.text == "zz-copper lantern sky-zz")
        #expect(result.candidates.map(\.canonical) == ["zz-copper-lantern-sky-zz"])
    }

    @Test func aCandidateTakesTheLowestLine() throws {
        let list = try Self.fixture7()
        let lines = [
            RecognizedLine(text: "zz-copper-lantern", confidence: 0.9, box: CGRect(x: 0, y: 0, width: 1, height: 0.1)),
            RecognizedLine(text: "sky-zz", confidence: 0.7, box: CGRect(x: 0, y: 0.2, width: 1, height: 0.1)),
        ]
        let result = ReadPipeline(classifyList: list, checkList: list).read(lines: lines)
        let candidate = try #require(result.candidates.first)
        #expect(candidate.rows.map(\.token) == ["copper", "lantern", "sky"])
        #expect(candidate.rows.map(\.lineIndex) == [0, 0, 1])
        #expect(Bands.candidateConfidence(candidate.rows) == 0.7)
        #expect(candidate.boxes.count == 2)
    }

    @Test func severalCandidatesNeverAutoOpen() throws {
        let list = try Self.fixture7()
        let line = RecognizedLine(text: "zz-copper-lantern-sky-zz and zz-copper-sky-river-zz", confidence: 1,
                                  box: CGRect(x: 0, y: 0, width: 1, height: 0.1))
        let result = ReadPipeline(classifyList: list, checkList: list).read(lines: [line])
        #expect(result.candidates.count == 2)
        #expect(result.autoOpen == nil)
        let single = ReadPipeline(classifyList: list, checkList: list)
            .read(lines: [RecognizedLine(text: "zz-copper-lantern-sky-zz", confidence: 1, box: .zero)])
        #expect(single.autoOpen?.canonical == "zz-copper-lantern-sky-zz")
    }

    @Test func loaderReadsVersionedListsAndChecksThem() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let words = try Self.fixture7().words
        try (words.joined(separator: "\n") + "\n").write(to: directory.appendingPathComponent("fixture-7.txt"),
                                                        atomically: true, encoding: .utf8)
        try "# report".write(to: directory.appendingPathComponent("proto-v0.report.md"), atomically: true, encoding: .utf8)
        let lists = try WordListLoader.load(directory: directory)
        #expect(lists.map(\.version) == ["fixture-7"])
        #expect(lists.first?.words == words)
        #expect(WordListLoader.matching(lists, serverVersion: "fixture-7")?.version == "fixture-7")
        #expect(WordListLoader.matching(lists, serverVersion: "proto-v0") == nil)
        #expect(WordListLoader.matching(lists, serverVersion: nil) == nil)
    }

    @Test func aMissingFolderLoadsNoList() throws {
        let missing = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        #expect(try WordListLoader.load(directory: missing).isEmpty)
    }

    @Test(arguments: [
        ("copper\nCopper\n", WordListError.invalidWord("t", line: 2)),
        ("copper\nzz\n", WordListError.reservedWord("t", line: 2)),
        ("copper\ncopper\n", WordListError.duplicate("t", line: 2)),
        ("", WordListError.empty("t")),
    ])
    func loaderRefusesABadList(_ text: String, _ error: WordListError) {
        #expect(throws: error) { try WordListLoader.parse(version: "t", text: text) }
    }
}
