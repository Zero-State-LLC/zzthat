import Foundation
import Testing
@testable import ZZAPI

/// tasks.md T095, zzThis RM-075 (D-2026-10-10-12): the generated client is
/// a tolerant reader, so an additive, optional field such as the planned
/// Resolve `creator` (RM-112) does not break a shipped app. An unknown enum
/// value is not tolerated yet: issue #54.
@Suite("Tolerant reader")
struct TolerantReaderTests {
    static func make(body: String) async throws -> ZZService {
        try await service(StubTransport { _ in StubTransport.Reply(200, nil).withBody(body) })
    }

    @Test func ignoresUnknownKeys() async throws {
        let body = """
        {"view":"public","canonical":"zz-kathy-lost-cat-zz","creator":"c-1","later":{"a":[1,2]},\
        "record":{"title":"Lost cat","body":"Answers on this page.","updated_at":"2026-10-04T00:00:00.123Z","extra":true},\
        "share":{"text":"zz-kathy-lost-cat-zz","url":null,"extra":"x"}}
        """
        let response = try await Self.make(body: body).resolve("zz-kathy-lost-cat-zz").get()
        #expect(response.record.record.title == "Lost cat")
        #expect(response.record.share.text == "zz-kathy-lost-cat-zz")
    }
}
