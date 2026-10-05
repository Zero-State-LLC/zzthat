import Foundation
import HTTPTypes
import OpenAPIRuntime

/// What went wrong with one call, before it becomes one sentence.
public enum APIFailure: Error, Sendable, Hashable {
    /// A documented error response: its status, its `error` code, and its `reason`.
    case response(status: Int, error: String, reason: String?)
    /// The one not-found body (FR-010).
    case notFound
    /// 429 with `Retry-After` in whole seconds (FR-033).
    case rateLimited(retryAfter: Int)
    /// 401 that the refresh could not fix (FR-015).
    case unauthorized
    /// 400 `contract-version`, the only result that shows `error.update_app` (T013).
    case contractVersion
    /// A status the contract does not declare for this call, with the
    /// contract header present.
    case undocumented(status: Int)
    /// No connection (US6).
    case noConnection
    /// Any other response without a usable `X-ZZ-Contract`, or a body that
    /// does not match the contract (the Swift client throws `ClientError`).
    case unavailable
}

/// The call a failure came from, which decides its sentence (spec.md, Errors).
public enum CallContext: Sendable, Hashable {
    case resolve
    case mint
    case reroll
    case revoke
    case recordVersion
    case report
    case ownerRead
    case signIn
    case signOut
    case deleteAccount
    case discovery

    public var isWrite: Bool {
        switch self {
        case .mint, .reroll, .revoke, .recordVersion, .report, .deleteAccount: true
        case .resolve, .ownerRead, .signIn, .signOut, .discovery: false
        }
    }
}

/// Copy keys from zzThis design/copy.json. These are keys, not sentences: the
/// app looks each one up in its generated catalog.
public enum MessageKey: String, Sendable, CaseIterable {
    case typeMalformed = "type.malformed"
    case resolveNotFound = "resolve.not_found"
    case resolveRateLimited = "resolve.rate_limited"
    case scanCheckMismatch = "scan.check_mismatch"
    case scanWrongLength = "scan.wrong_length"
    case scanBare = "scan.bare"
    case createUnavailable = "create.unavailable"
    case createContentRefused = "create.content_refused"
    case mintedRerollCap = "minted.reroll_cap"
    case signInNeeded = "common.sign_in_needed"
    case signInFailed = "signin.failed"
    case deleteFailed = "delete.failed"
    case forbidden = "error.forbidden"
    case taken = "error.taken"
    case reservedHandle = "error.reserved_handle"
    case nothingSaved = "error.nothing_saved"
    case updateApp = "error.update_app"
    case noConnection = "error.no_connection"
    case unavailable = "error.unavailable"
}

extension APIFailure {
    /// The one sentence for this failure (spec.md, Errors; design/UX.md, Errors).
    public func message(for context: CallContext) -> MessageKey {
        switch self {
        case .contractVersion: return .updateApp
        case .rateLimited: return .resolveRateLimited
        case .unavailable: return .unavailable
        case .noConnection: return .noConnection
        case .notFound: return context == .deleteAccount ? .deleteFailed : .resolveNotFound
        case .unauthorized:
            switch context {
            case .signIn: return .signInFailed
            case .deleteAccount, .mint, .reroll, .revoke, .recordVersion, .report, .ownerRead: return .signInNeeded
            case .resolve, .signOut, .discovery: return .unavailable
            }
        case .undocumented(let status):
            // T013: until the pin declares 500 and 503 on every operation.
            if status == 500, context.isWrite { return context == .deleteAccount ? .deleteFailed : .nothingSaved }
            return context == .signIn ? .signInFailed : .unavailable
        case .response(let status, let error, let reason):
            return Self.message(status: status, error: error, reason: reason, context: context)
        }
    }

    static func message(status: Int, error: String, reason: String?, context: CallContext) -> MessageKey {
        if context == .signIn { return .signInFailed }
        if context == .deleteAccount { return .deleteFailed }
        switch (status, error) {
        case (400, "contract-version"): return .updateApp
        case (400, "malformed") where context == .resolve:
            switch reason {
            case "check-mismatch": return .scanCheckMismatch
            case "wrong-length": return .scanWrongLength
            default: return .typeMalformed
            }
        case (400, _): return context.isWrite ? .nothingSaved : .unavailable
        case (422, "unsupported"): return .scanBare
        case (422, "reserved-handle"): return .reservedHandle
        case (422, "content-refused"): return .createContentRefused
        case (403, "reroll-cap"): return .mintedRerollCap
        case (403, "scope-unavailable"): return .createUnavailable
        case (503, "not-ready") where context == .mint: return .createUnavailable
        case (403, _): return .forbidden
        case (409, "taken"): return .taken
        case (413, _): return .nothingSaved
        case (500, _): return context.isWrite ? .nothingSaved : .unavailable
        default: return .unavailable
        }
    }

    /// Maps an error the generated client threw. A transport failure that
    /// means no connection follows the offline rules (US6).
    public static func from(thrown error: any Error) -> APIFailure {
        if let failure = error as? APIFailure { return failure }
        var underlying: any Error = error
        if let client = error as? ClientError { underlying = client.underlyingError }
        if let failure = underlying as? APIFailure { return failure }
        if let url = underlying as? URLError, Self.offlineCodes.contains(url.code) { return .noConnection }
        return .unavailable
    }

    static let offlineCodes: Set<URLError.Code> = [
        .notConnectedToInternet, .networkConnectionLost, .cannotConnectToHost, .cannotFindHost,
        .dnsLookupFailed, .timedOut, .internationalRoamingOff, .dataNotAllowed, .callIsActive,
    ]

    /// An undocumented status: without the contract header it is
    /// `unavailable` (runtime.md, API client step 10).
    public static func from(undocumented status: Int, _ payload: UndocumentedPayload) -> APIFailure {
        guard payload.headerFields[.contract] == "1" else { return .unavailable }
        return .undocumented(status: status)
    }
}
