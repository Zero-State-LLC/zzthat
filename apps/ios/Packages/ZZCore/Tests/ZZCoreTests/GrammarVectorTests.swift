import Testing
@testable import ZZCore

/// Every `grammar` row of contracts/vectors.json (Section 2.2a G9, SC-1).
/// The `grammar_pending` rows wait for zzThis spec 005 T035 and are not run on
/// any platform yet (the vectors file says so).
@Suite("Grammar vectors")
struct GrammarVectorTests {
    static let rows = Row.list(Vectors.rows("grammar"))

    @Test func rowsAreLoaded() {
        #expect(!Self.rows.isEmpty)
    }

    @Test(arguments: rows)
    func row(_ row: Row) throws {
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
}
