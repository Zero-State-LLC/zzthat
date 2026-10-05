import Foundation
import HTTPTypes
import OpenAPIRuntime
import Testing
@testable import ZZAPI

/// tasks.md T010 and T012: the contract header, the bearer rule, and the
/// single-flight refresh, on captured requests.
@Suite("Requests")
struct RequestTests {
    static func ok(_ sent: StubTransport.Sent) -> StubTransport.Reply {
        switch sent.operationID {
        case "resolveCode":
            .init(200, "resolve", headers: ["Cache-Control": "public, max-age=60, stale-while-revalidate=300"])
        case "getMe": .init(200, "account")
        case "getDiscovery": .init(200, "discovery")
        case "createNonce": .init(200, "nonce")
        case "exchangeToken", "refreshToken": .init(200, "tokens")
        case "revokeToken", "deleteMe": .init(204)
        case "mintCode": .init(201, "code")
        case "createReport": .init(202, nil, headers: ["Content-Type": "application/json"]).withBody("report-id")
        default: .init(500, "failed")
        }
    }

    static func contractFields(_ request: HTTPRequest) -> [String] {
        request.headerFields[values: .contract]
    }

    @Test(arguments: ["getMe", "resolveCode"])
    func exactlyOneContractHeader(_ operation: String) async throws {
        let transport = StubTransport(Self.ok)
        let service = try await service(transport, refreshToken: "refresh-0", accessToken: "access-0")
        if operation == "getMe" {
            _ = try await service.me().get()
        } else {
            _ = try await service.resolve("zz-copper-lantern-sky-zz").get()
        }
        let sent = try #require(transport.sent.first { $0.operationID == operation })
        #expect(Self.contractFields(sent.request) == ["1"])
    }

    @Test func theMiddlewareReplacesAContractHeaderInsteadOfAdding() async throws {
        let middleware = ContractMiddleware()
        var request = HTTPRequest(method: .get, scheme: "http", authority: "localhost", path: "/v1")
        request.headerFields.append(HTTPField(name: .contract, value: "1"))
        request.headerFields.append(HTTPField(name: .contract, value: "2"))
        let captured = Captured()
        _ = try await middleware.intercept(request, body: nil, baseURL: URL(string: "http://localhost")!,
                                           operationID: "getDiscovery") { request, _, _ in
            captured.set(request)
            return (HTTPResponse(status: .ok), nil)
        }
        #expect(Self.contractFields(try #require(captured.request)) == ["1"])
    }

    /// T012 and SC-2: resolve sends the canonical string, no photo, and no
    /// Authorization header, even when the person is signed in.
    @Test func resolveSendsTheCanonicalCodeOnly() async throws {
        let transport = StubTransport(Self.ok)
        let service = try await service(transport, refreshToken: "refresh-0", accessToken: "access-0")
        let response = try await service.resolve("zz-kathy-lost-cat-zz").get()
        #expect(response.cacheable)
        #expect(response.record.record.title == "Lost cat")
        let sent = try #require(transport.sent.first)
        #expect(sent.request.method == .get)
        #expect(sent.request.path == "/v1/resolve/zz-kathy-lost-cat-zz")
        #expect(sent.request.headerFields[.authorization] == nil)
        #expect(sent.body == nil)
    }

    @Test func aNoStoreResolveIsNotCacheable() async throws {
        let transport = StubTransport { _ in .init(200, "resolve") }
        let response = try await service(transport).resolve("zz-kathy-lost-cat-zz").get()
        #expect(!response.cacheable)
    }

    /// SC-2: mint sends no code string and no photo.
    @Test func mintSendsNoCodeString() async throws {
        let transport = StubTransport(Self.ok)
        let service = try await service(transport, refreshToken: "refresh-0", accessToken: "access-0")
        let code = try await service.mint(title: "Lost cat", body: "Answers on this page.").get()
        #expect(code.canonical == "zz-copper-lantern-sky-zz")
        let sent = try #require(transport.sent.first)
        #expect(sent.request.headerFields[.authorization] == "Bearer access-0")
        let mintBody = try #require(sent.body)
        let object = try #require(try JSONSerialization.jsonObject(with: mintBody) as? [String: Any])
        #expect(Set(object.keys) == ["scope", "kind", "record"])
        #expect(object["scope"] as? String == "free_public")
        #expect(object["kind"] as? String == "plain")
    }

    @Test func theBearerRule() async throws {
        let transport = StubTransport(Self.ok)
        let service = try await service(transport, refreshToken: "refresh-0", accessToken: "access-0")
        _ = await service.discovery()
        _ = await service.nonce()
        _ = await service.exchange(provider: .dev, idToken: "dev:local-tester", nonce: "nonce-1")
        _ = await service.report(canonical: "zz-copper-lantern-sky-zz", reason: .other, note: "")
        _ = await service.me()
        _ = await service.signOut()
        _ = await service.report(canonical: "zz-copper-lantern-sky-zz", reason: .other, note: "")
        let auth = transport.sent.map { ($0.operationID, $0.request.headerFields[.authorization]) }
        #expect(auth.map(\.0) == ["getDiscovery", "createNonce", "exchangeToken", "createReport", "getMe",
                                  "revokeToken", "createReport"])
        #expect(auth[0].1 == nil)
        #expect(auth[1].1 == nil)
        #expect(auth[2].1 == nil)
        #expect(auth[3].1 == "Bearer access-1", "a signed-in report carries the bearer")
        #expect(auth[4].1 == "Bearer access-1")
        #expect(auth[5].1 == nil, "revoke sends the refresh token in the body, not a bearer")
        #expect(auth[6].1 == nil, "a signed-out report carries no bearer")
        let revokeBody = try #require(transport.sent[5].body)
        let object = try JSONSerialization.jsonObject(with: revokeBody) as? [String: Any]
        #expect(object?["refresh_token"] as? String == "refresh-1")
        #expect(await service.tokens.hasSession == false)
    }

    @Test func signInKeepsTheRefreshTokenAndSendsTheProvider() async throws {
        let storage = MemoryRefreshTokenStorage()
        let tokens = TokenStore(storage: storage)
        let transport = StubTransport(Self.ok)
        let service = await ZZService.live(origin: URL(string: "http://localhost:8787")!, tokens: tokens,
                                           transport: transport)
        try await service.exchange(provider: .dev, idToken: "dev:local-tester", nonce: "nonce-1").get()
        #expect(storage.load() == "refresh-1")
        #expect(await tokens.currentAccessToken == "access-1")
        let body = try #require(transport.sent.first?.body)
        let object = try #require(try JSONSerialization.jsonObject(with: body) as? [String: Any])
        #expect(object["provider"] as? String == "dev")
        #expect(object["client"] as? String == "ios")
        #expect(object["id_token"] as? String == "dev:local-tester")
        #expect(object["nonce"] as? String == "nonce-1")
        #expect(object["authorization_code"] == nil)
        #expect(object["redirect_uri"] == nil)
    }

    /// T010: two concurrent calls with an expired token make exactly one
    /// refresh, and both succeed.
    @Test func concurrentCallsShareOneRefresh() async throws {
        let refreshes = Counter()
        let transport = StubTransport { sent in
            switch sent.operationID {
            case "refreshToken":
                refreshes.increment()
                try await Task.sleep(for: .milliseconds(100))
                return .init(200, "tokens")
            case "getMe":
                return sent.request.headerFields[.authorization] == "Bearer access-1"
                    ? .init(200, "account") : .init(401, "unauthorized")
            default:
                return .init(500, "failed")
            }
        }
        let service = try await service(transport, refreshToken: "refresh-0", accessToken: "expired")
        async let first = service.me()
        async let second = service.me()
        let results = await [first, second]
        #expect(results.allSatisfy { (try? $0.get()) != nil })
        #expect(refreshes.value == 1)
        #expect(transport.sent.filter { $0.operationID == "getMe" }.count == 4)
    }

    /// A launch with only a stored refresh token refreshes before the first
    /// bearer call.
    @Test func aStoredRefreshTokenStartsTheSession() async throws {
        let transport = StubTransport(Self.ok)
        let service = try await service(transport, refreshToken: "refresh-0")
        _ = try await service.me().get()
        #expect(transport.sent.map(\.operationID) == ["refreshToken", "getMe"])
        let body = try #require(transport.sent.first?.body)
        let object = try JSONSerialization.jsonObject(with: body) as? [String: Any]
        #expect(object?["refresh_token"] as? String == "refresh-0")
    }

    /// The person is signed out only when the refresh itself returns 401.
    @Test func aRejectedRefreshSignsOut() async throws {
        let transport = StubTransport { sent in
            sent.operationID == "refreshToken" ? .init(401, "unauthorized") : .init(401, "unauthorized")
        }
        let service = try await service(transport, refreshToken: "refresh-0", accessToken: "expired")
        let signedOut = Counter()
        await service.tokens.onSignedOut { signedOut.increment() }
        let result = await service.me()
        #expect(result.failure == .unauthorized)
        #expect(result.failure?.message(for: .ownerRead) == .signInNeeded)
        #expect(await service.tokens.hasSession == false)
        #expect(signedOut.value == 1)
        #expect(transport.sent.map(\.operationID) == ["getMe", "refreshToken"])
    }

    /// A refresh that fails for another reason keeps the session.
    @Test func aRefreshWithNoConnectionKeepsTheSession() async throws {
        let transport = StubTransport { sent in
            if sent.operationID == "refreshToken" { throw URLError(.notConnectedToInternet) }
            return .init(401, "unauthorized")
        }
        let service = try await service(transport, refreshToken: "refresh-0", accessToken: "expired")
        #expect(await service.me().failure == .noConnection)
        #expect(await service.tokens.hasSession)
    }

    /// The retry happens once.
    @Test func theRetryHappensOnce() async throws {
        let transport = StubTransport { sent in
            sent.operationID == "refreshToken" ? .init(200, "tokens") : .init(401, "unauthorized")
        }
        let service = try await service(transport, refreshToken: "refresh-0", accessToken: "expired")
        #expect(await service.me().failure == .unauthorized)
        #expect(transport.sent.map(\.operationID) == ["getMe", "refreshToken", "getMe"])
    }

    /// The paged lookup by code id (runtime.md, Navigation).
    @Test func ownedCodeLookupPagesUntilAMatch() async throws {
        let transport = StubTransport { sent in
            sent.request.path?.contains("cursor=page-2") == true
                ? .init(200, "code-page")
                : .init(200, nil).withBody(#"{"codes":[],"next_cursor":"page-2"}"#)
        }
        let service = try await service(transport, refreshToken: "refresh-0", accessToken: "access-0")
        let row = try await service.ownedCode(id: "code-1").get()
        #expect(row?.title == "Lost cat")
        #expect(transport.sent.count == 2)
        #expect(transport.sent[0].request.path?.contains("limit=50") == true)
        let missing = try await service.ownedCode(id: "nope").get()
        #expect(missing == nil)
    }
}

final class Counter: @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0
    func increment() {
        lock.lock()
        count += 1
        lock.unlock()
    }
    var value: Int {
        lock.lock()
        defer { lock.unlock() }
        return count
    }
}

final class Captured: @unchecked Sendable {
    private let lock = NSLock()
    private var stored: HTTPRequest?
    func set(_ request: HTTPRequest) {
        lock.lock()
        stored = request
        lock.unlock()
    }
    var request: HTTPRequest? {
        lock.lock()
        defer { lock.unlock() }
        return stored
    }
}

extension StubTransport.Reply {
    /// A reply with an inline JSON body instead of a fixture file.
    func withBody(_ json: String) -> StubTransport.Reply {
        var reply = self
        reply.inline = json.hasPrefix("{") ? json : #"{"id":"\#(json)"}"#
        reply.headers["Content-Type"] = "application/json"
        return reply
    }
}
