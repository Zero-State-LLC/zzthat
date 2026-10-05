import Foundation
import HTTPTypes
import OpenAPIRuntime

extension HTTPField.Name {
    static let contract = HTTPField.Name("X-ZZ-Contract")!
}

/// Every `/v1` call carries exactly one `X-ZZ-Contract: 1` (FR-032). The
/// header is set by replacing, never by appending (runtime.md, API client
/// step 1), so a value the generated client already wrote is not doubled.
public struct ContractMiddleware: ClientMiddleware {
    public init() {}

    public func intercept(
        _ request: HTTPRequest, body: HTTPBody?, baseURL: URL, operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        var request = request
        request.headerFields[.contract] = "1"
        return try await next(request, body, baseURL)
    }
}

/// The bearer rule (runtime.md, API client step 3, FR-016) and the 401 retry
/// (step 8). Resolve, nonce, token, refresh, and revoke never carry a bearer.
/// A report carries one only when the person is signed in.
public struct AuthMiddleware: ClientMiddleware {
    /// Operations whose contract security is the bearer token.
    public static let bearerOperations: Set<String> = [
        "getMe", "deleteMe", "mintCode", "rerollCode", "listMyCodes", "revokeCode",
        "getRecord", "addRecordVersion", "listAudit",
    ]
    /// Operations where the bearer is optional (`security: [{}, bearer]`),
    /// except resolve, which never sends one so it stays cacheable (FR-016).
    public static let optionalBearerOperations: Set<String> = ["createReport", "submitRead"]

    /// Bodies are kept in memory so the one retry can send them again.
    static let maxRetainedBody = 9 * 1024 * 1024

    let tokens: TokenStore

    public init(tokens: TokenStore) {
        self.tokens = tokens
    }

    public func intercept(
        _ request: HTTPRequest, body: HTTPBody?, baseURL: URL, operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        var request = request
        request.headerFields[.authorization] = nil
        if Self.optionalBearerOperations.contains(operationID) {
            if let token = await tokens.currentAccessToken {
                request.headerFields[.authorization] = "Bearer \(token)"
            }
            return try await next(request, body, baseURL)
        }
        guard Self.bearerOperations.contains(operationID) else {
            return try await next(request, body, baseURL)
        }
        let data: Data? = if let body { try await Data(collecting: body, upTo: Self.maxRetainedBody) } else { nil }
        let token = try await tokens.tokenForRequest()
        var first = request
        if let token { first.headerFields[.authorization] = "Bearer \(token)" }
        let (response, responseBody) = try await next(first, data.map { HTTPBody($0) }, baseURL)
        guard response.status == .unauthorized, token != nil else { return (response, responseBody) }
        guard let renewed = try await tokens.refreshed(after: token) else { return (response, responseBody) }
        var retry = request
        retry.headerFields[.authorization] = "Bearer \(renewed)"
        return try await next(retry, data.map { HTTPBody($0) }, baseURL)
    }
}
