import Foundation
import Testing
@testable import ZZCore

/// tasks.md T022 and T102: the zzThis spec 004 `capture_band` vector rows
/// at the pin, which Android's `:core-grammar` tests also run, then one test
/// per band trigger in zzThis spec 004 (Decision bands and Prototype band
/// values).
@Suite("Bands")
struct BandTests {
    static let captureBand = Row.list(Vectors.section("capture_band")["rows"] as? [[String: Any]] ?? [])

    /// The camera rows of the band table, without confirm-only mode.
    static var cameraTableRows: [Row] {
        captureBand.filter { $0.fields["source"] as? String == "camera" && $0.fields["confirm_only"] == nil }
    }

    static func fixture7() throws -> WordList {
        let words = try #require(Vectors.section("check_word")["wordlist"] as? [String])
        return try WordList(version: "fixture-7", words: words)
    }

    @Test func captureBandRowsAreLoaded() {
        #expect(Self.captureBand.count >= 24)
        #expect(Self.captureBand.contains { $0.fields["confirm_only"] as? Bool == true })
    }

    /// A row without `confirm_only` runs the band table; a `confirm_only:
    /// true` row also runs confirm-only mode (spec 004 FR-023).
    @Test(arguments: captureBand)
    func captureBandRow(_ row: Row) throws {
        let list = try Self.fixture7()
        let confirmOnly = row.fields["confirm_only"] as? Bool ?? false
        var configuration = CaptureConfiguration.current
        configuration.confirmOnly = confirmOnly
        let source = try #require(row.fields["source"] as? String)
        let decision: BandDecision
        if source == "typed" {
            let input = try #require(row.fields["input"] as? String)
            let band: Band
            switch ReadPipeline(classifyList: list, checkList: list).readTyped(input) {
            case .success(let candidate): band = candidate.band
            case .failure: band = .abstain
            }
            decision = BandDecision(band, "typed")
        } else {
            let canonical = try #require(row.fields["canonical"] as? String)
            guard case .success(let code) = Grammar.parse(canonical) else {
                Issue.record("\(canonical) does not parse")
                return
            }
            let codeClass = Classifier.classify(code, wordlist: list.words)
            let check = CheckWord.verify(code, list: list)
            let items = try #require(row.fields["rows"] as? [[String: Any]])
            let rows = try items.map {
                BandRow(token: try #require($0["token"] as? String), lineIndex: try #require($0["line"] as? Int),
                        lineConfidence: try #require($0["confidence"] as? Double))
            }
            let band = Bands.band(candidate: .code(code), codeClass: codeClass, check: check, rows: rows, source: .camera)
            decision = BandDecision(band, "table")
        }
        let result = confirmOnly
            ? Bands.applyConfirmOnly(decision, source: source == "typed" ? .typed : .camera, configuration: configuration)
            : decision
        #expect(result.band.rawValue == row.fields["band"] as? String, "\(row.fields["name"] ?? "")")
    }

    // MARK: Camera triggers, through the whole pipeline

    func camera(_ lines: [(String, Double)], list: WordList? = nil) throws -> [ReadCandidate] {
        let list = try list ?? Self.fixture7()
        let pipeline = ReadPipeline(classifyList: list, checkList: list)
        let recognized = lines.enumerated().map { offset, line in
            RecognizedLine(text: line.0, confidence: line.1,
                           box: CGRect(x: 0.1, y: 0.1 + Double(offset) * 0.2, width: 0.8, height: 0.1))
        }
        return pipeline.read(lines: recognized).candidates
    }

    @Test func cameraAcceptVerifiedWordCode() throws {
        let found = try camera([("zz-copper-lantern-sky-zz", 0.95)])
        #expect(found.map(\.band) == [.accept])
        #expect(found.first?.check == .ok)
    }

    @Test func cameraClarifyWordBetweenThresholds() throws {
        #expect(try camera([("zz-copper-lantern-sky-zz", 0.6)]).map(\.band) == [.clarify])
    }

    @Test func cameraClarifyNearWord() throws {
        let found = try camera([("zz-coper-lantern-sky-zz", 0.95)])
        #expect(found.map(\.band) == [.clarify])
        #expect(found.first?.codeClass == .confirm(nearWords: [1: ["copper"]]))
    }

    @Test func cameraClarifyCheckWordMismatch() throws {
        let found = try camera([("zz-copper-maple-sky-zz", 0.95)])
        #expect(found.map(\.band) == [.clarify])
        #expect(found.first?.check == .checkMismatch)
    }

    @Test(arguments: ["zz-b2-smith-1-zz", "zz-@agentsmith-zz", "zz-vitalik.eth-zz"])
    func cameraClarifyFieldHandleOrName(_ text: String) throws {
        #expect(try camera([(text, 0.99)]).map(\.band) == [.clarify])
    }

    @Test func cameraRetryPartial() throws {
        #expect(try camera([("label zz-copper-lantern", 0.95)]).map(\.band) == [.retry])
    }

    @Test func cameraRetryNoCandidate() {
        #expect(Bands.band(candidate: nil, codeClass: nil, check: nil, rows: [], source: .camera) == .retry)
    }

    @Test func cameraRetryWordBelowHalf() throws {
        let found = try camera([("zz-copper-lantern", 0.9), ("sky-zz", 0.45)])
        #expect(found.map(\.canonical) == ["zz-copper-lantern-sky-zz"])
        #expect(found.map(\.band) == [.retry])
    }

    @Test func cameraAbstainInvalid() throws {
        #expect(try camera([("zz-#tag-zz", 0.95)]).map(\.band) == [.abstain])
    }

    @Test func cameraAbstainWrongLength() throws {
        let found = try camera([("zz-copper-lantern-zz", 0.95)])
        #expect(found.map(\.band) == [.abstain])
        #expect(found.first?.check == .wrongLength)
    }

    @Test func cameraAbstainBare() throws {
        #expect(try camera([("zz", 0.95)]).map(\.band) == [.abstain])
    }

    /// FR-044 and A2 ([DANNY 2026-10-05 #43]): with only fixture-7 bundled
    /// and the server on proto-v0, no list classifies or checks the code, so
    /// a person confirms it.
    @Test func cameraWithoutAMatchingListSkipsTheLocalCheck() throws {
        let list = try Self.fixture7()
        let pipeline = ReadPipeline(bundled: [list], serverVersion: "proto-v0")
        let line = RecognizedLine(text: "zz-copper-maple-sky-zz", confidence: 0.95, box: .zero)
        let found = pipeline.read(lines: [line]).candidates
        #expect(found.first?.check == nil)
        #expect(found.first?.codeClass == .field)
        #expect(found.map(\.band) == [.clarify])
    }

    // MARK: Typed triggers

    func typed(_ text: String) throws -> Result<ReadCandidate, ParseFailure> {
        let list = try Self.fixture7()
        return ReadPipeline(classifyList: list, checkList: list).readTyped(text)
    }

    @Test(arguments: ["zz-copper-lantern-sky-zz", "zz-b2-smith-1-zz", "zz-@agentsmith-zz", "zz-vitalik.eth-zz"])
    func typedAccept(_ text: String) throws {
        #expect(try typed(text).map(\.band) == .success(.accept))
    }

    @Test(arguments: ["zz-coper-lantern-sky-zz", "zz-copper-maple-sky-zz"])
    func typedClarifyNearWordOrMismatch(_ text: String) throws {
        #expect(try typed(text).map(\.band) == .success(.clarify))
    }

    @Test func typedAbstainParserFailure() throws {
        #expect(try typed("zz-#tag-zz").map(\.band) == .failure(.reservedSymbol))
    }

    @Test(arguments: ["zz-copper-lantern-zz", "zz", "(zz)"])
    func typedAbstainWrongLengthOrBare(_ text: String) throws {
        #expect(try typed(text).map(\.band) == .success(.abstain))
    }

    @Test func thresholdsAreThePrototypeValues() {
        #expect(BandThresholds.accept == 0.80)
        #expect(BandThresholds.retryBelow == 0.50)
    }
}
