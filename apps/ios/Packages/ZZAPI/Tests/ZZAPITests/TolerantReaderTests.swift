import Foundation
import Testing
@testable import ZZAPI

/// tasks.md T095, zzThis RM-075 (D-2026-10-10-12): the generated client is
/// a tolerant reader, so an additive, optional field such as the planned
/// Resolve `creator` (RM-112) does not break a shipped app. A value a
/// response enum does not list yet decodes to a fallback case (T106, #54,
/// D-2026-10-13-09; `EnumToleranceMiddleware`).
@Suite("Tolerant reader")
struct TolerantReaderTests {
    static func make(body: String, status: Int = 200, headers: [String: String] = [:]) async throws -> ZZService {
        try await service(StubTransport { _ in StubTransport.Reply(status, nil, headers: headers).withBody(body) },
                          accessToken: "a")
    }

    static func fixture(_ name: String, _ replacements: [String: String]) throws -> String {
        var text = try Fixture.text(name)
        for (old, new) in replacements {
            #expect(text.contains(old), "\(name) has \(old)")
            text = text.replacingOccurrences(of: old, with: new)
        }
        return text
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

    @Test func resolveWithAnUnknownViewShowsTheRecord() async throws {
        let body = """
        {"view":"auditor","canonical":"zz-kathy-lost-cat-zz",\
        "record":{"title":"Lost cat","body":"Answers on this page.","updated_at":"2026-10-04T00:00:00.123Z"},\
        "share":{"text":"zz-kathy-lost-cat-zz","url":null}}
        """
        let response = try await Self.make(body: body).resolve("zz-kathy-lost-cat-zz").get()
        #expect(response.record.view == ._public)
        #expect(response.record.record.title == "Lost cat")
        #expect(response.record.record.body == "Answers on this page.")
        #expect(response.record.share.text == "zz-kathy-lost-cat-zz")
    }

    @Test func anUnknownCacheControlIsNotKept() async throws {
        let body = try Fixture.text("resolve")
        let service = try await Self.make(body: body, headers: ["Cache-Control": "public, max-age=120"])
        let response = try await service.resolve("zz-kathy-lost-cat-zz").get()
        #expect(response.cacheable == false)
    }

    @Test func codesWithAnUnknownStatusAndScopeReadAsExpired() async throws {
        let body = try Self.fixture("code-page", [#""active""#: #""paused""#, #""free_public""#: #""galaxy""#])
        let page = try await Self.make(body: body).myCodes().get()
        #expect(page.codes.count == 1)
        #expect(page.codes[0].status == .expired)
        #expect(page.codes[0].scope == .freePublic)
        #expect(page.codes[0].title == "Lost cat")
    }

    @Test func aMintedCodeWithAnUnknownStatusDecodes() async throws {
        let body = try Self.fixture("code", [#""active""#: #""paused""#])
        let code = try await Self.make(body: body, status: 201).mint(title: "t", body: "").get()
        #expect(code.status == .expired)
        #expect(code.canonical == "zz-copper-lantern-sky-zz")
    }

    @Test func discoveryDropsAProviderAndAScopeItCannotKnow() async throws {
        let body = try Self.fixture("discovery", [#"["dev"]"#: #"["passkey","dev"]"#,
                                                  #"["free_public"]"#: #"["free_public","galaxy"]"#])
        let discovery = try await Self.make(body: body).discovery().get()
        #expect(discovery.authProviders == [.dev])
        #expect(discovery.scopes == [.freePublic])
    }

    @Test func anAccountWithAnUnknownProviderDecodes() async throws {
        let body = try Self.fixture("account", [#"["dev"]"#: #"["github","dev"]"#])
        let account = try await Self.make(body: body).me().get()
        #expect(account.providers == [.dev])
        #expect(account.id == "account-1")
    }

    @Test func tokensWithAnUnknownTypeAndLifetimeSignIn() async throws {
        let body = try Self.fixture("tokens", [#""Bearer""#: #""DPoP""#, "900": "600"])
        let tokens = TokenStore(storage: MemoryRefreshTokenStorage(token: nil))
        let transport = StubTransport { _ in StubTransport.Reply(200, nil).withBody(body) }
        let service = await ZZService.live(origin: URL(string: "http://localhost:8787")!, tokens: tokens, transport: transport)
        try await service.exchange(provider: .dev, idToken: "dev:local-tester", nonce: "nonce-1").get()
        #expect(await tokens.currentAccessToken == "access-1")
    }

    @Test func revokeWithAnUnknownStatusSucceeds() async throws {
        try await Self.make(body: #"{"status":"gone"}"#).revoke(codeID: "code-1").get()
    }

    /// Only an unknown value is rewritten. A value of the wrong JSON type is
    /// still refused.
    @Test func anEnumValueOfTheWrongTypeIsStillRefused() async throws {
        let body = try Self.fixture("resolve", [#""view":"public""#: #""view":5"#])
        let result = await try Self.make(body: body).resolve("zz-kathy-lost-cat-zz")
        #expect(result.failure == .unavailable)
    }

    /// An error body is not a record: an error code the contract does not
    /// list stays unavailable, as on Android.
    @Test func anUnknownErrorCodeIsUnavailable() async throws {
        let result = await try Self.make(body: #"{"error":"brand-new"}"#, status: 403).myCodes()
        #expect(result.failure == .unavailable)
    }
}
