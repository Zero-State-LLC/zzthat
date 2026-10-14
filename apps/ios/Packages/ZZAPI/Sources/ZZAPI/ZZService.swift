import Foundation
import HTTPTypes
import OpenAPIRuntime
import OpenAPIURLSession

public typealias Discovery = Components.Schemas.Discovery
public typealias MintedCode = Components.Schemas.Code
public typealias OwnedCode = Components.Schemas.OwnedCode
public typealias CodePage = Components.Schemas.CodePage
public typealias ResolvedRecord = Components.Schemas.Resolve
public typealias Account = Components.Schemas.Account
public typealias OwnerRecord = Components.Schemas.OwnerRecord
public typealias ReportReason = Components.Schemas.ReportRequest.ReasonPayload
public typealias SignInProvider = Components.Schemas.TokenRequest.ProviderPayload

/// A resolve and whether FR-041 may keep it: only when its `Cache-Control`
/// is `public, max-age=60, stale-while-revalidate=300`.
public struct ResolveResponse: Sendable, Hashable {
    public let record: ResolvedRecord
    public let cacheable: Bool

    public init(record: ResolvedRecord, cacheable: Bool) {
        self.record = record
        self.cacheable = cacheable
    }
}

/// Every call the apps make (spec.md, API contract), over the generated
/// `APIProtocol`. A live build passes the generated `Client`; a Demo build
/// passes `DemoAPI`. Each call returns the decoded body or one `APIFailure`.
public struct ZZService: Sendable {
    // Keep the generated client private: app features may call only the V1 methods
    // wrapped below, so server-only endpoints such as photo upload are not exposed.
    private let api: any APIProtocol
    public let tokens: TokenStore

    public init(api: any APIProtocol, tokens: TokenStore) {
        self.api = api
        self.tokens = tokens
    }

    /// The session for every `/v1` call: ephemeral, no URL cache, no
    /// cookies (FR-049). FR-041 is the only resolve cache.
    public static func sessionConfiguration() -> URLSessionConfiguration {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.requestCachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.timeoutIntervalForRequest = 30
        return configuration
    }

    /// A live service on `origin`. Refresh runs on its own client with only
    /// the contract middleware, so it never recurses into the 401 retry.
    public static func live(origin: URL, tokens: TokenStore,
                            transport: (any ClientTransport)? = nil) async -> ZZService {
        let parts = clients(origin: origin, transport: transport)
        await tokens.setRefresher(refresher(parts.refresh))
        return ZZService(api: parts.client(tokens), tokens: tokens)
    }

    /// The same live service, built without waiting, for the app's launch.
    /// The token store is made here, so it holds the refresher from the start.
    public static func live(origin: URL, storage: any RefreshTokenStorage,
                            transport: (any ClientTransport)? = nil) -> ZZService {
        let parts = clients(origin: origin, transport: transport)
        let tokens = TokenStore(storage: storage, refresher: refresher(parts.refresh))
        return ZZService(api: parts.client(tokens), tokens: tokens)
    }

    /// A build with no API origin: every call fails with `unavailable`, so
    /// the app shows `error.unavailable` and never invents data.
    public static func unconfigured(storage: any RefreshTokenStorage) -> ZZService {
        live(origin: URL(string: "https://origin.invalid")!, storage: storage, transport: UnconfiguredTransport())
    }

    private static func clients(origin: URL, transport: (any ClientTransport)?)
        -> (refresh: Client, client: @Sendable (TokenStore) -> Client) {
        let transport = transport ?? URLSessionTransport(
            configuration: .init(session: URLSession(configuration: sessionConfiguration())))
        let configuration = Configuration(dateTranscoder: LenientISO8601Transcoder())
        let refresh = Client(serverURL: origin, configuration: configuration, transport: transport,
                             middlewares: [ContractMiddleware()])
        return (refresh, { tokens in
            Client(serverURL: origin, configuration: configuration, transport: transport,
                   middlewares: [ContractMiddleware(), AuthMiddleware(tokens: tokens)])
        })
    }

    private static func refresher(_ refreshClient: Client) -> TokenStore.Refresher {
        { refreshToken in
            switch try await refreshClient.refreshToken(.init(
                headers: .init(xZZContract: ._1), body: .json(.init(refreshToken: refreshToken)))) {
            case .ok(let ok):
                let pair = try ok.body.json
                guard let refresh = pair.refreshToken else { throw APIFailure.unavailable }
                return .refreshed(accessToken: pair.accessToken, refreshToken: refresh)
            case .unauthorized:
                return .rejected
            case .badRequest(let r): throw r.failure(status: 400)
            case .tooManyRequests(let r): throw r.failure(status: 429)
            case .internalServerError(let r): throw r.failure(status: 500)
            case .serviceUnavailable(let r): throw r.failure(status: 503)
            case .undocumented(let status, let payload): throw APIFailure.from(undocumented: status, payload)
            }
        }
    }

    private static func run<T: Sendable>(_ body: () async throws -> Result<T, APIFailure>) async -> Result<T, APIFailure> {
        do {
            return try await body()
        } catch {
            return .failure(APIFailure.from(thrown: error))
        }
    }

    // MARK: Discovery

    public func discovery() async -> Result<Discovery, APIFailure> {
        await Self.run {
            switch try await api.getDiscovery(.init(headers: .init(xZZContract: ._1))) {
            case .ok(let ok): return .success(try ok.body.json)
            case .badRequest(let r): return .failure(r.failure(status: 400))
            case .tooManyRequests(let r): return .failure(r.failure(status: 429))
            case .serviceUnavailable(let r): return .failure(r.failure(status: 503))
            case .undocumented(let status, let payload): return .failure(.from(undocumented: status, payload))
            }
        }
    }

    // MARK: Auth

    public func nonce() async -> Result<String, APIFailure> {
        await Self.run {
            switch try await api.createNonce(.init(headers: .init(xZZContract: ._1))) {
            case .ok(let ok): return .success(try ok.body.json.nonce)
            case .badRequest(let r): return .failure(r.failure(status: 400))
            case .tooManyRequests(let r): return .failure(r.failure(status: 429))
            case .internalServerError(let r): return .failure(r.failure(status: 500))
            case .serviceUnavailable(let r): return .failure(r.failure(status: 503))
            case .undocumented(let status, let payload): return .failure(.from(undocumented: status, payload))
            }
        }
    }

    /// `POST /v1/auth/token` (Auth step 5), then keep the pair.
    public func exchange(provider: SignInProvider, idToken: String, nonce: String,
                         authorizationCode: String? = nil) async -> Result<Void, APIFailure> {
        await Self.run {
            let request = Components.Schemas.TokenRequest(
                provider: provider, client: .ios, idToken: idToken, nonce: nonce,
                authorizationCode: authorizationCode)
            switch try await api.exchangeToken(.init(headers: .init(xZZContract: ._1), body: .json(request))) {
            case .ok(let ok):
                let pair = try ok.body.json
                try await tokens.signIn(accessToken: pair.accessToken, refreshToken: pair.refreshToken)
                return .success(())
            case .badRequest(let r): return .failure(r.failure(status: 400))
            case .unauthorized(let r): return .failure(r.failure(status: 401))
            case .tooManyRequests(let r): return .failure(r.failure(status: 429))
            case .internalServerError(let r): return .failure(r.failure(status: 500))
            case .serviceUnavailable(let r): return .failure(r.failure(status: 503))
            case .undocumented(let status, let payload): return .failure(.from(undocumented: status, payload))
            }
        }
    }

    /// Sign-out: `POST /v1/auth/revoke`, then delete the tokens (Auth step 8).
    /// The tokens are deleted even when the revoke fails, so sign-out always
    /// ends the session on this device.
    @discardableResult
    public func signOut() async -> Result<Void, APIFailure> {
        let refresh = await tokens.refreshToken
        let result: Result<Void, APIFailure> = await Self.run {
            switch try await api.revokeToken(.init(
                headers: .init(xZZContract: ._1), body: .json(.init(refreshToken: refresh)))) {
            case .noContent: return .success(())
            case .badRequest(let r): return .failure(r.failure(status: 400))
            case .unauthorized(let r): return .failure(r.failure(status: 401))
            case .tooManyRequests(let r): return .failure(r.failure(status: 429))
            case .internalServerError(let r): return .failure(r.failure(status: 500))
            case .serviceUnavailable(let r): return .failure(r.failure(status: 503))
            case .undocumented(let status, let payload): return .failure(.from(undocumented: status, payload))
            }
        }
        await tokens.clear()
        return result
    }

    public func me() async -> Result<Account, APIFailure> {
        await Self.run {
            switch try await api.getMe(.init(headers: .init(xZZContract: ._1))) {
            case .ok(let ok): return .success(try ok.body.json)
            case .badRequest(let r): return .failure(r.failure(status: 400))
            case .unauthorized(let r): return .failure(r.failure(status: 401))
            case .tooManyRequests(let r): return .failure(r.failure(status: 429))
            case .serviceUnavailable(let r): return .failure(r.failure(status: 503))
            case .undocumented(let status, let payload): return .failure(.from(undocumented: status, payload))
            }
        }
    }

    /// `DELETE /v1/me` (FR-034). On success the tokens are deleted here; the
    /// app deletes the draft and every cache (Auth step 9).
    public func deleteAccount() async -> Result<Void, APIFailure> {
        let result: Result<Void, APIFailure> = await Self.run {
            switch try await api.deleteMe(.init(headers: .init(xZZContract: ._1))) {
            case .noContent: return .success(())
            case .badRequest(let r): return .failure(r.failure(status: 400))
            case .unauthorized(let r): return .failure(r.failure(status: 401))
            case .tooManyRequests(let r): return .failure(r.failure(status: 429))
            case .internalServerError(let r): return .failure(r.failure(status: 500))
            case .serviceUnavailable(let r): return .failure(r.failure(status: 503))
            case .undocumented(let status, let payload): return .failure(.from(undocumented: status, payload))
            }
        }
        if case .success = result { await tokens.clear() }
        return result
    }

    // MARK: Codes

    /// `GET /v1/resolve/{code}` with the canonical code only: no photo and
    /// no `Authorization` (FR-016, T012).
    public func resolve(_ canonical: String) async -> Result<ResolveResponse, APIFailure> {
        await Self.run {
            switch try await api.resolveCode(.init(path: .init(code: canonical), headers: .init(xZZContract: ._1))) {
            case .ok(let ok):
                let cacheable = ok.headers.cacheControl == .public_comma_MaxAge_equals_60_comma_StaleWhileRevalidate_equals_300
                return .success(ResolveResponse(record: try ok.body.json, cacheable: cacheable))
            case .badRequest(let r): return .failure(r.failure(status: 400))
            case .notFound(let r): return .failure(r.failure(status: 404))
            case .unprocessableContent(let r): return .failure(r.failure(status: 422))
            case .tooManyRequests(let r): return .failure(r.failure(status: 429))
            case .internalServerError(let r): return .failure(r.failure(status: 500))
            case .serviceUnavailable(let r): return .failure(r.failure(status: 503))
            case .undocumented(let status, let payload): return .failure(.from(undocumented: status, payload))
            }
        }
    }

    /// `POST /v1/codes` with scope `free_public`, kind `plain`, and the title
    /// and body. No code string and no photo (FR-017, SC-2).
    public func mint(title: String, body: String) async -> Result<MintedCode, APIFailure> {
        await Self.run {
            let request = Components.Schemas.MintRequest(
                scope: .freePublic, kind: .plain, record: .init(title: title, body: body))
            switch try await api.mintCode(.init(headers: .init(xZZContract: ._1), body: .json(request))) {
            case .created(let created): return .success(try created.body.json)
            case .badRequest(let r): return .failure(r.failure(status: 400))
            case .unauthorized(let r): return .failure(r.failure(status: 401))
            case .forbidden(let r): return .failure(r.failure(status: 403))
            case .conflict(let r): return .failure(r.failure(status: 409))
            case .unprocessableContent(let r): return .failure(r.failure(status: 422))
            case .tooManyRequests(let r): return .failure(r.failure(status: 429))
            case .internalServerError(let r): return .failure(r.failure(status: 500))
            case .serviceUnavailable(let r): return .failure(r.failure(status: 503))
            case .undocumented(let status, let payload): return .failure(.from(undocumented: status, payload))
            }
        }
    }

    public func reroll(codeID: String) async -> Result<MintedCode, APIFailure> {
        await Self.run {
            switch try await api.rerollCode(.init(path: .init(id: codeID), headers: .init(xZZContract: ._1))) {
            case .ok(let ok): return .success(try ok.body.json)
            case .badRequest(let r): return .failure(r.failure(status: 400))
            case .unauthorized(let r): return .failure(r.failure(status: 401))
            case .forbidden(let r): return .failure(r.failure(status: 403))
            case .notFound(let r): return .failure(r.failure(status: 404))
            case .tooManyRequests(let r): return .failure(r.failure(status: 429))
            case .internalServerError(let r): return .failure(r.failure(status: 500))
            case .serviceUnavailable(let r): return .failure(r.failure(status: 503))
            case .undocumented(let status, let payload): return .failure(.from(undocumented: status, payload))
            }
        }
    }

    /// One page of `GET /v1/me/codes`, 50 per page (FR-021).
    public func myCodes(cursor: String? = nil, limit: Int = 50) async -> Result<CodePage, APIFailure> {
        await Self.run {
            switch try await api.listMyCodes(.init(query: .init(limit: limit, cursor: cursor),
                                                   headers: .init(xZZContract: ._1))) {
            case .ok(let ok): return .success(try ok.body.json)
            case .badRequest(let r): return .failure(r.failure(status: 400))
            case .unauthorized(let r): return .failure(r.failure(status: 401))
            case .forbidden(let r): return .failure(r.failure(status: 403))
            case .tooManyRequests(let r): return .failure(r.failure(status: 429))
            case .serviceUnavailable(let r): return .failure(r.failure(status: 503))
            case .undocumented(let status, let payload): return .failure(.from(undocumented: status, payload))
            }
        }
    }

    /// Code detail and Edit record by code id (runtime.md, Navigation):
    /// page through `GET /v1/me/codes` until a row's `id` matches. Nil when
    /// no row matches, which shows the one not-found state.
    public func ownedCode(id: String) async -> Result<OwnedCode?, APIFailure> {
        var cursor: String?
        repeat {
            switch await myCodes(cursor: cursor) {
            case .failure(let failure): return .failure(failure)
            case .success(let page):
                if let row = page.codes.first(where: { $0.id == id }) { return .success(row) }
                cursor = page.nextCursor
            }
        } while cursor != nil
        return .success(nil)
    }

    public func record(id: String) async -> Result<OwnerRecord, APIFailure> {
        await Self.run {
            switch try await api.getRecord(.init(path: .init(id: id), headers: .init(xZZContract: ._1))) {
            case .ok(let ok): return .success(try ok.body.json)
            case .badRequest(let r): return .failure(r.failure(status: 400))
            case .unauthorized(let r): return .failure(r.failure(status: 401))
            case .notFound(let r): return .failure(r.failure(status: 404))
            case .tooManyRequests(let r): return .failure(r.failure(status: 429))
            case .serviceUnavailable(let r): return .failure(r.failure(status: 503))
            case .undocumented(let status, let payload): return .failure(.from(undocumented: status, payload))
            }
        }
    }

    public func addVersion(recordID: String, title: String, body: String) async -> Result<Void, APIFailure> {
        await Self.run {
            switch try await api.addRecordVersion(.init(path: .init(id: recordID), headers: .init(xZZContract: ._1),
                                                        body: .json(.init(title: title, body: body)))) {
            case .created: return .success(())
            case .badRequest(let r): return .failure(r.failure(status: 400))
            case .unauthorized(let r): return .failure(r.failure(status: 401))
            case .forbidden(let r): return .failure(r.failure(status: 403))
            case .notFound(let r): return .failure(r.failure(status: 404))
            case .unprocessableContent(let r): return .failure(r.failure(status: 422))
            case .tooManyRequests(let r): return .failure(r.failure(status: 429))
            case .internalServerError(let r): return .failure(r.failure(status: 500))
            case .serviceUnavailable(let r): return .failure(r.failure(status: 503))
            case .undocumented(let status, let payload): return .failure(.from(undocumented: status, payload))
            }
        }
    }

    public func revoke(codeID: String) async -> Result<Void, APIFailure> {
        await Self.run {
            switch try await api.revokeCode(.init(path: .init(id: codeID), headers: .init(xZZContract: ._1))) {
            case .ok: return .success(())
            case .badRequest(let r): return .failure(r.failure(status: 400))
            case .unauthorized(let r): return .failure(r.failure(status: 401))
            case .forbidden(let r): return .failure(r.failure(status: 403))
            case .notFound(let r): return .failure(r.failure(status: 404))
            case .tooManyRequests(let r): return .failure(r.failure(status: 429))
            case .internalServerError(let r): return .failure(r.failure(status: 500))
            case .serviceUnavailable(let r): return .failure(r.failure(status: 503))
            case .undocumented(let status, let payload): return .failure(.from(undocumented: status, payload))
            }
        }
    }

    /// `POST /v1/reports` (FR-035). The answer never says whether the code exists.
    public func report(canonical: String, reason: ReportReason, note: String) async -> Result<Void, APIFailure> {
        await Self.run {
            let request = Components.Schemas.ReportRequest(canonical: canonical, reason: reason,
                                                           note: note.isEmpty ? nil : note)
            switch try await api.createReport(.init(headers: .init(xZZContract: ._1), body: .json(request))) {
            case .accepted: return .success(())
            case .badRequest(let r): return .failure(r.failure(status: 400))
            case .tooManyRequests(let r): return .failure(r.failure(status: 429))
            case .internalServerError(let r): return .failure(r.failure(status: 500))
            case .serviceUnavailable(let r): return .failure(r.failure(status: 503))
            case .undocumented(let status, let payload): return .failure(.from(undocumented: status, payload))
            }
        }
    }
}

struct UnconfiguredTransport: ClientTransport {
    func send(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL,
              operationID: String) async throws -> (HTTPResponse, HTTPBody?) {
        throw APIFailure.unavailable
    }
}

// MARK: Error responses

protocol FailureResponse {
    func failure(status: Int) -> APIFailure
}

extension Components.Schemas._Error {
    func failure(status: Int) -> APIFailure {
        switch error {
        case .contractVersion: .contractVersion
        case .notFound: .notFound
        case .unauthorized: .unauthorized
        default: .response(status: status, error: error.rawValue, reason: reason)
        }
    }
}

extension Components.Responses._Error: FailureResponse {
    func failure(status: Int) -> APIFailure {
        (try? body.json.failure(status: status)) ?? .unavailable
    }
}

extension Components.Responses.ContractVersion: FailureResponse {
    func failure(status: Int) -> APIFailure {
        (try? body.json.failure(status: status)) ?? .unavailable
    }
}

extension Components.Responses.Unauthorized: FailureResponse {
    func failure(status: Int) -> APIFailure { .unauthorized }
}

extension Components.Responses.NotFound: FailureResponse {
    func failure(status: Int) -> APIFailure { .notFound }
}

extension Components.Responses.RateLimited: FailureResponse {
    func failure(status: Int) -> APIFailure { .rateLimited(retryAfter: headers.retryAfter) }
}

extension Components.Responses.Failed: FailureResponse {
    func failure(status: Int) -> APIFailure {
        (try? body.json.failure(status: status)) ?? .unavailable
    }
}

extension Components.Responses.NotReady: FailureResponse {
    func failure(status: Int) -> APIFailure {
        (try? body.json.failure(status: status)) ?? .unavailable
    }
}
