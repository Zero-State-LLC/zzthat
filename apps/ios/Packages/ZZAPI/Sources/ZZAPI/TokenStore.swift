import Foundation

/// Where the refresh token lives between launches. The app uses the Keychain
/// (runtime.md, Auth step 6). Tests use memory.
public protocol RefreshTokenStorage: Sendable {
    func load() -> String?
    func save(_ token: String) throws
    func delete()
}

/// The answer of one `POST /v1/auth/refresh`.
public enum RefreshOutcome: Sendable, Equatable {
    case refreshed(accessToken: String, refreshToken: String)
    /// The refresh itself returned 401. Only this signs the person out
    /// (zzThis spec 005 FR-021).
    case rejected
}

/// The access token in memory and the one shared refresh in flight
/// (runtime.md, API client step 8). A request that gets a 401 while a refresh
/// runs waits for that refresh, then retries once with the new token.
public actor TokenStore {
    public typealias Refresher = @Sendable (_ refreshToken: String) async throws -> RefreshOutcome

    private let storage: any RefreshTokenStorage
    private var accessToken: String?
    private var refresher: Refresher?
    private var inFlight: Task<String?, any Error>?
    private var signedOutHandler: (@Sendable () -> Void)?

    public init(storage: any RefreshTokenStorage) {
        self.storage = storage
    }

    public func setRefresher(_ refresher: @escaping Refresher) {
        self.refresher = refresher
    }

    /// Called when a refresh returns 401 and the session ends.
    public func onSignedOut(_ handler: @escaping @Sendable () -> Void) {
        signedOutHandler = handler
    }

    public var currentAccessToken: String? { accessToken }

    public var hasSession: Bool { accessToken != nil || storage.load() != nil }

    public func signIn(accessToken: String, refreshToken: String?) throws {
        if let refreshToken { try storage.save(refreshToken) }
        self.accessToken = accessToken
    }

    /// The refresh token, for sign-out's `POST /v1/auth/revoke`.
    public var refreshToken: String? { storage.load() }

    /// Sign-out and account deletion: delete both tokens (Auth steps 8 and 9).
    public func clear() {
        accessToken = nil
        storage.delete()
    }

    /// The token to send now. With no token in memory but a stored refresh
    /// token (a fresh launch), this runs the shared refresh first.
    public func tokenForRequest() async throws -> String? {
        if let accessToken { return accessToken }
        return try await refreshed(after: nil)
    }

    /// The token to retry with after `failed` got a 401, or nil when there
    /// is no session any more. Concurrent callers share one refresh.
    public func refreshed(after failed: String?) async throws -> String? {
        if let accessToken, accessToken != failed { return accessToken }
        if let inFlight { return try await inFlight.value }
        guard let refreshToken = storage.load(), let refresher else { return nil }
        let task = Task { try await self.run(refresher, refreshToken) }
        inFlight = task
        defer { inFlight = nil }
        return try await task.value
    }

    private func run(_ refresher: Refresher, _ refreshToken: String) async throws -> String? {
        switch try await refresher(refreshToken) {
        case .refreshed(let access, let refresh):
            try storage.save(refresh)
            accessToken = access
            return access
        case .rejected:
            accessToken = nil
            storage.delete()
            signedOutHandler?()
            return nil
        }
    }
}

/// A refresh token kept in memory only, for tests and the demo build.
public final class MemoryRefreshTokenStorage: RefreshTokenStorage, @unchecked Sendable {
    private let lock = NSLock()
    private var token: String?

    public init(token: String? = nil) {
        self.token = token
    }

    public func load() -> String? {
        lock.lock()
        defer { lock.unlock() }
        return token
    }

    public func save(_ token: String) {
        lock.lock()
        defer { lock.unlock() }
        self.token = token
    }

    public func delete() {
        lock.lock()
        defer { lock.unlock() }
        token = nil
    }
}
