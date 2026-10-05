import Foundation
import Testing
@testable import ZZAPI

/// tasks.md T011: success fixtures carry a timestamp with milliseconds
/// (`.123Z`) and one without (`Z`), and both decode (runtime.md, API client
/// step 9).
@Suite("Decoding")
struct DecodingTests {
    enum Form: String, CaseIterable, Sendable {
        case milliseconds = "2026-10-04T00:00:00.123Z"
        case wholeSeconds = "2026-10-04T00:00:00Z"
    }

    static func transport(_ fixture: String, status: Int, form: Form,
                          headers: [String: String] = [:]) throws -> StubTransport {
        let text = try Fixture.text(fixture).replacingOccurrences(of: Form.milliseconds.rawValue, with: form.rawValue)
        return StubTransport { _ in StubTransport.Reply(status, nil, headers: headers).withBody(text) }
    }

    static let expected = Date(timeIntervalSince1970: 1_791_072_000)

    func near(_ date: Date, _ form: Form) -> Bool {
        abs(date.timeIntervalSince(Self.expected) - (form == .milliseconds ? 0.123 : 0)) < 0.0005
    }

    @Test(arguments: Form.allCases)
    func code(_ form: Form) async throws {
        let service = try await service(Self.transport("code", status: 201, form: form), accessToken: "a")
        let code = try await service.mint(title: "t", body: "").get()
        #expect(near(code.createdAt, form))
        #expect(code.checkWord == "sky")
    }

    @Test(arguments: Form.allCases)
    func ownedCode(_ form: Form) async throws {
        let service = try await service(Self.transport("code-page", status: 200, form: form), accessToken: "a")
        let page = try await service.myCodes().get()
        #expect(page.codes.first.map { near($0.createdAt, form) } == true)
        #expect(page.nextCursor == nil)
    }

    @Test(arguments: Form.allCases)
    func resolve(_ form: Form) async throws {
        let service = try await service(Self.transport("resolve", status: 200, form: form))
        let response = try await service.resolve("zz-kathy-lost-cat-zz").get()
        #expect(near(response.record.record.updatedAt, form))
        #expect(response.record.share.url == nil)
        #expect(response.record.share.text == "zz-kathy-lost-cat-zz")
    }

    @Test(arguments: Form.allCases)
    func account(_ form: Form) async throws {
        let service = try await service(Self.transport("account", status: 200, form: form), accessToken: "a")
        let account = try await service.me().get()
        #expect(near(account.createdAt, form))
        #expect(account.providers == [.dev])
    }

    @Test(arguments: Form.allCases)
    func ownerRecord(_ form: Form) async throws {
        let service = try await service(Self.transport("owner-record", status: 200, form: form), accessToken: "a")
        let record = try await service.record(id: "record-1").get()
        #expect(near(record.updatedAt, form))
        #expect(record.version == 2)
    }

    @Test func theTranscoderWritesFractionalSeconds() throws {
        let transcoder = LenientISO8601Transcoder()
        #expect(try transcoder.encode(Self.expected) == "2026-10-04T00:00:00.000Z")
        #expect(try transcoder.decode("2026-10-04T00:00:00Z") == Self.expected)
        #expect(throws: (any Error).self) { try transcoder.decode("4 October 2026") }
    }

    @Test func aBodyWithAnotherFieldIsRefused() async throws {
        let transport = StubTransport { _ in
            StubTransport.Reply(404, nil).withBody(#"{"error":"not-found","hint":"zz-copper-lantern-sky-zz"}"#)
        }
        let result = await try service(transport).resolve("zz-copper-lantern-sky-zz")
        #expect(result.failure == .unavailable)
    }
}
