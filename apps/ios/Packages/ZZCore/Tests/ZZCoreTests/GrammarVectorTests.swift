import Testing
@testable import ZZCore

/// Every `grammar` row of contracts/vectors.json (Section 2.2a G9, SC-1).
/// zzThis spec 005 T035 moved the former `grammar_pending` rows into
/// `grammar` and deleted that section, so `grammar` is every grammar row.
@Suite("Grammar vectors")
struct GrammarVectorTests {
    static let rows = Row.list(Vectors.rows("grammar"))

    @Test func rowsAreLoaded() {
        #expect(!Self.rows.isEmpty)
        // A row parked outside `grammar` would not run here, so a section
        // like the old `grammar_pending` must not come back unnoticed.
        #expect(Vectors.root["grammar_pending"] == nil)
    }

    @Test(arguments: rows)
    func row(_ row: Row) throws {
        try Self.check(row)
    }

    static func check(_ row: Row) throws {
        let input = try #require(row.fields["input"] as? String)
        let expect = try #require(row.fields["expect"] as? [String: Any])
        let result = Grammar.parse(input)
        if let reason = expect["fail"] as? String {
            guard case .failure(let failure) = result else {
                Issue.record("expected \(reason), got \(result)")
                return
            }
            #expect(failure.rawValue == reason)
            return
        }
        guard case .success(let code) = result else {
            Issue.record("expected success, got \(result)")
            return
        }
        #expect(code.kind.rawValue == expect["kind"] as? String)
        #expect(code.canonical == expect["canonical"] as? String)
        if let handle = expect["handle"] as? String { #expect(code.handle == handle) }
        if let tag = expect["tag"] as? String { #expect(code.tag == tag) } else { #expect(code.tag == nil) }
        if let qualifiers = expect["qualifiers"] as? [String] { #expect(code.qualifiers == qualifiers) }
        // spec 003 US3 acceptance 3: the canonical form parses to itself.
        guard case .success(let again) = Grammar.parse(code.canonical) else {
            Issue.record("canonical \(code.canonical) does not parse")
            return
        }
        #expect(again.canonical == code.canonical)
        #expect(again.kind == code.kind)
    }

    @Test func longRandomInputIsGuarded() {
        let long = String(repeating: "zz-copper-", count: 1000)
        #expect(Grammar.parse(long) == .failure(.tooLong))
    }

    /// G2 step 1 lists the White_Space characters by hand so every platform
    /// agrees. The list is the Unicode property, over every scalar.
    @Test func theWhiteSpaceListIsTheUnicodeProperty() {
        let differ = (UInt32(0)...0x10FFFF).compactMap(Unicode.Scalar.init).filter {
            Scalars.isWhiteSpace($0) != $0.properties.isWhitespace
        }
        #expect(differ.isEmpty, "\(differ.map { "U+" + String($0.value, radix: 16, uppercase: true) })")
    }

    /// U+FEFF is not White_Space: it neither trims nor separates.
    @Test func aByteOrderMarkIsNotASpace() {
        #expect(Grammar.parse("\u{FEFF}") == .failure(.noMarker))
        #expect(Grammar.parse("\u{FEFF}zz-copper-zz") == .failure(.noMarker))
    }

    /// Fullwidth forms map before NFC, so a fullwidth letter with a
    /// combining mark composes into a non-ASCII letter.
    @Test func fullwidthMapsBeforeComposition() {
        #expect(Grammar.parse("zz-caf\u{FF45}\u{0301}-zz") == .failure(.unsupportedScript))
        #expect(Grammar.parse("\u{FF08}zz\u{FF09} camp bravo \u{FF08}ZZ\u{FF09}")
            .map(\.canonical) == .success("zz-camp-bravo-zz"))
    }
}
