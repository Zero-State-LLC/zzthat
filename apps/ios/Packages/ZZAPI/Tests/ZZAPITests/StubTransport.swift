import Foundation
import HTTPTypes
import OpenAPIRuntime
@testable import ZZAPI

/// A stub `ClientTransport` that records every request and answers from a
/// handler (runtime.md, Testing: API fixtures with a stub transport).
final class StubTransport: ClientTransport, @unchecked Sendable {
    struct Sent: Sendable {
        let request: HTTPRequest
        let body: Data?
        let operationID: String
    }

    typealias Handler = @Sendable (Sent) async throws -> Reply

    struct Reply: Sendable {
        var status: Int
        var fixture: String?
        var inline: String?
        var headers: [String: String]

        init(_ status: Int, _ fixture: String? = nil, headers: [String: String] = [:], contract: Bool = true) {
            self.status = status
            self.fixture = fixture
            var all = ["Cache-Control": "no-store", "Content-Type": "application/json"]
            if contract { all["X-ZZ-Contract"] = "1" }
            if fixture == nil { all["Content-Type"] = nil }
            self.headers = all.merging(headers) { _, new in new }
        }
    }

    private let lock = NSLock()
    private var log: [Sent] = []
    private let handler: Handler

    init(_ handler: @escaping Handler) {
        self.handler = handler
    }

    var sent: [Sent] {
        lock.lock()
        defer { lock.unlock() }
        return log
    }

    func send(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL,
              operationID: String) async throws -> (HTTPResponse, HTTPBody?) {
        let data: Data? = if let body { try await Data(collecting: body, upTo: 10 * 1024 * 1024) } else { nil }
        let sent = Sent(request: request, body: data, operationID: operationID)
        lock.withLock { log.append(sent) }
        let reply = try await handler(sent)
        var fields = HTTPFields()
        for (name, value) in reply.headers { fields[HTTPField.Name(name)!] = value }
        let response = HTTPResponse(status: .init(code: reply.status), headerFields: fields)
        if let inline = reply.inline { return (response, HTTPBody(inline)) }
        guard let fixture = reply.fixture else { return (response, nil) }
        return (response, HTTPBody(try Fixture.data(fixture)))
    }
}

enum Fixture {
    static func data(_ name: String) throws -> Data {
        guard let url = Bundle.module.url(forResource: "Fixtures/\(name)", withExtension: "json") else {
            throw CocoaError(.fileNoSuchFile)
        }
        return try Data(contentsOf: url)
    }

    static func text(_ name: String) throws -> String {
        String(decoding: try data(name), as: UTF8.self)
    }
}

/// A refresh token kept in memory for tests.
func service(_ transport: StubTransport, refreshToken: String? = nil,
             accessToken: String? = nil) async throws -> ZZService {
    let tokens = TokenStore(storage: MemoryRefreshTokenStorage(token: refreshToken))
    if let accessToken { try await tokens.signIn(accessToken: accessToken, refreshToken: refreshToken) }
    return await ZZService.live(origin: URL(string: "http://localhost:8787")!, tokens: tokens, transport: transport)
}
