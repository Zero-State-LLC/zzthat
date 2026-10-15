import Foundation
import HTTPTypes
import OpenAPIRuntime

/// zzThis RM-075 and D-2026-10-13-09 (tasks.md T106, #54): a value that a
/// response enum does not list yet must not fail the whole response.
///
/// Swift OpenAPI Generator 1.13.1 has no fallback case for a closed enum, and
/// the generated types are never edited (runtime.md, API client). So this
/// middleware rewrites an unknown value to a fallback case before the
/// generated client decodes the response. The known values come from each
/// generated enum's `allCases`, so a value the contract adds is known as soon
/// as the contract pin moves. Only the JSON paths are written here: one for
/// each response enum of an operation that `ZZService` calls.
///
/// A value of the wrong JSON type is left alone, so it still fails decoding.
/// Error bodies are not rewritten: an error code the contract does not list
/// stays `unavailable`, as on Android.
public struct EnumToleranceMiddleware: ClientMiddleware {
    public init() {}

    public func intercept(
        _ request: HTTPRequest, body: HTTPBody?, baseURL: URL, operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        var (response, responseBody) = try await next(request, body, baseURL)
        Self.tolerateCacheControl(&response, operationID: operationID)
        guard response.status.kind == .successful, let responseBody,
              let fields = Self.bodyFields[operationID] else { return (response, responseBody) }
        let data = try await Data(collecting: responseBody, upTo: Self.maxBody)
        guard let rewritten = Self.rewrite(data, fields) else { return (response, HTTPBody(data)) }
        if response.headerFields[.contentLength] != nil {
            response.headerFields[.contentLength] = String(rewritten.count)
        }
        return (response, HTTPBody(rewritten))
    }

    /// Far above any contract 1 body; a 50-row page of codes is a few KB.
    static let maxBody = 8 * 1024 * 1024

    // MARK: Fallbacks

    typealias Schemas = Components.Schemas

    /// The fallback for each response enum, by operation. Each choice is the
    /// safest reading of a value the app cannot know yet.
    static let bodyFields: [String: [Field]] = [
        "getDiscovery": [
            // The X-ZZ-Contract header is the version check (FR-032), and it
            // stays strict. No code reads this body field.
            .replace("contract", with: Schemas.Discovery.ContractPayload._1),
            // A provider or scope the app cannot know has no button and no
            // use, so it is dropped from its list.
            .drop("auth_providers", "*", of: Schemas.Discovery.AuthProvidersPayloadPayload.self),
            .drop("scopes", "*", of: Schemas.Discovery.ScopesPayloadPayload.self),
        ],
        "exchangeToken": tokenFields,
        "refreshToken": tokenFields,
        // A provider without a name to show is dropped; Account shows the
        // first one the app knows.
        "getMe": [.drop("providers", "*", of: Schemas.Account.ProvidersPayloadPayload.self)],
        // A view the app cannot know reads like `public`: the app shows the
        // record the server returned and does not branch on the view.
        "resolveCode": [.replace("view", with: Schemas.Resolve.ViewPayload._public)],
        "mintCode": codeFields,
        "rerollCode": codeFields,
        "listMyCodes": [
            .replace("codes", "*", "status", with: Schemas.OwnedCode.StatusPayload.expired),
            .replace("codes", "*", "scope", with: Schemas.OwnedCode.ScopePayload.freePublic),
        ],
        // A 200 from revoke means the code is revoked.
        "revokeCode": [.replace("status", with: Operations.RevokeCode.Output.Ok.Body.JsonPayload.StatusPayload.revoked)],
    ]

    /// The app sends the access token as a bearer token and never reads its
    /// lifetime: a 401 starts the refresh (Auth step 7).
    static let tokenFields: [Field] = [
        .replace("token_type", with: Schemas.TokenResponse.TokenTypePayload.bearer),
        .replace("expires_in", with: Schemas.TokenResponse.ExpiresInPayload._900),
    ]

    /// A status the app cannot know is not active, so there is no Revoke. It
    /// reads as expired, the nearest not-active state with copy, because copy
    /// comes only from zzThis; Android shows the same. Only `free_public`
    /// codes are minted, and no screen reads the scope.
    static let codeFields: [Field] = [
        .replace("status", with: Schemas.Code.StatusPayload.expired),
        .replace("scope", with: Schemas.Code.ScopePayload.freePublic),
    ]

    /// `Cache-Control` is a response enum too. An unknown value becomes
    /// `no-store`, which every response may carry and which only means that
    /// FR-041 does not keep this resolve.
    static func tolerateCacheControl(_ response: inout HTTPResponse, operationID: String) {
        guard let value = response.headerFields[.cacheControl] else { return }
        let known = operationID == "resolveCode" && response.status == .ok
            ? Components.Headers.ResolveCache.allCases.map(\.rawValue)
            : Components.Headers.NoStore.allCases.map(\.rawValue)
        if !known.contains(value) {
            response.headerFields[.cacheControl] = Components.Headers.NoStore.noStore.rawValue
        }
    }

    // MARK: Rewriting

    /// One response enum: where it sits in the body, which values the
    /// generated enum lists, and what an unknown value becomes.
    struct Field: Sendable {
        /// Keys from the body's root; `*` is every element of an array.
        let path: [String]
        /// True for a value of the enum's raw type that the enum does not list.
        let isUnknown: @Sendable (Any) -> Bool
        /// The raw value to write, or nil to drop the element from its array.
        let fallback: (any Sendable)?

        static func replace<E: RawRepresentable & CaseIterable>(_ path: String..., with fallback: E) -> Field
        where E.RawValue: Hashable & Sendable {
            Field(path: path, isUnknown: unknown(E.self), fallback: fallback.rawValue)
        }

        static func drop<E: RawRepresentable & CaseIterable>(_ path: String..., of _: E.Type) -> Field
        where E.RawValue: Hashable & Sendable {
            precondition(path.last == "*", "only an array element can be dropped")
            return Field(path: path, isUnknown: unknown(E.self), fallback: nil)
        }

        private static func unknown<E: RawRepresentable & CaseIterable>(_: E.Type) -> @Sendable (Any) -> Bool
        where E.RawValue: Hashable & Sendable {
            let known = Set(E.allCases.map(\.rawValue))
            return { value in (value as? E.RawValue).map { !known.contains($0) } ?? false }
        }
    }

    /// The body with every unknown value replaced or dropped, or nil when
    /// nothing changed (or it is not JSON), so the original bytes pass through.
    static func rewrite(_ data: Data, _ fields: [Field]) -> Data? {
        guard var root = try? JSONSerialization.jsonObject(with: data) else { return nil }
        var changed = false
        for field in fields where apply(field, to: &root, at: field.path[...]) { changed = true }
        guard changed else { return nil }
        return try? JSONSerialization.data(withJSONObject: root)
    }

    private static func apply(_ field: Field, to value: inout Any, at path: ArraySlice<String>) -> Bool {
        guard let step = path.first else { return false }
        let rest = path.dropFirst()
        if step == "*" {
            guard var array = value as? [Any] else { return false }
            var changed = false
            if !rest.isEmpty {
                for index in array.indices where apply(field, to: &array[index], at: rest) { changed = true }
            } else if let fallback = field.fallback {
                for index in array.indices where field.isUnknown(array[index]) {
                    array[index] = fallback
                    changed = true
                }
            } else {
                let count = array.count
                array.removeAll(where: field.isUnknown)
                changed = array.count != count
            }
            if changed { value = array }
            return changed
        }
        guard var object = value as? [String: Any], var child = object[step] else { return false }
        if rest.isEmpty {
            guard let fallback = field.fallback, field.isUnknown(child) else { return false }
            child = fallback
        } else if !apply(field, to: &child, at: rest) {
            return false
        }
        object[step] = child
        value = object
        return true
    }
}
