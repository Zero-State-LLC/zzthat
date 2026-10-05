import Testing
@testable import ZZCore

/// Every `scanner` row of contracts/vectors.json (spec 004 Scanner rules).
@Suite("Scanner vectors")
struct ScannerVectorTests {
    static let rows = Row.list(Vectors.rows("scanner"))

    @Test func rowsAreLoaded() {
        #expect(!Self.rows.isEmpty)
    }

    @Test(arguments: rows)
    func row(_ row: Row) throws {
        let input = try #require(row.fields["input"] as? String)
        let expected = try #require(row.fields["candidates"] as? [[String: Any]])
        let actual = Scanner.scan(input).map(Self.describe)
        #expect(actual == expected.map(Self.describe))
    }

    static func describe(_ candidate: ScanCandidate) -> String {
        switch candidate.kind {
        case .code(let code): "code \(code.canonical) \(code.kind.rawValue)"
        case .bare: "bare"
        case .partial(let text): "partial \(text.debugDescription)"
        case .invalid(let reason, let text): "invalid \(reason.rawValue) \(text.debugDescription)"
        }
    }

    static func describe(_ row: [String: Any]) -> String {
        switch row["kind"] as? String {
        case "code": "code \(row["canonical"] as? String ?? "?") \(row["code_kind"] as? String ?? "?")"
        case "bare": "bare"
        case "partial": "partial \((row["text"] as? String ?? "").debugDescription)"
        case "invalid": "invalid \(row["reason"] as? String ?? "?") \((row["text"] as? String ?? "").debugDescription)"
        default: "unknown \(row)"
        }
    }
}
