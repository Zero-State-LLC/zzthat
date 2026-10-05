import Foundation
import Testing
@testable import ZZAPI

/// One fixture test per row of the spec.md error table (tasks.md T011, T013,
/// T014), through the generated client and a stub transport.
@Suite("Spec error table")
struct ErrorTableTests {
    enum Call: String, Sendable, CaseIterable {
        case resolve, mint, recordVersion, report, reroll, revoke, me, myCodes, record, deleteAccount, signIn, discovery

        var context: CallContext {
            switch self {
            case .resolve: .resolve
            case .mint: .mint
            case .recordVersion: .recordVersion
            case .report: .report
            case .reroll: .reroll
            case .revoke: .revoke
            case .me, .myCodes, .record: .ownerRead
            case .deleteAccount: .deleteAccount
            case .signIn: .signIn
            case .discovery: .discovery
            }
        }

        func failure(_ service: ZZService) async -> APIFailure? {
            func failure<T>(_ result: Result<T, APIFailure>) -> APIFailure? {
                if case .failure(let failure) = result { return failure }
                return nil
            }
            switch self {
            case .resolve: return failure(await service.resolve("zz-copper-lantern-sky-zz"))
            case .mint: return failure(await service.mint(title: "Lost cat", body: "Answers on this page."))
            case .recordVersion: return failure(await service.addVersion(recordID: "record-1", title: "Lost cat", body: ""))
            case .report: return failure(await service.report(canonical: "zz-copper-lantern-sky-zz", reason: .spam, note: ""))
            case .reroll: return failure(await service.reroll(codeID: "code-1"))
            case .revoke: return failure(await service.revoke(codeID: "code-1"))
            case .me: return failure(await service.me())
            case .myCodes: return failure(await service.myCodes())
            case .record: return failure(await service.record(id: "record-1"))
            case .deleteAccount: return failure(await service.deleteAccount())
            case .signIn: return failure(await service.exchange(provider: .dev, idToken: "dev:local-tester", nonce: "n"))
            case .discovery: return failure(await service.discovery())
            }
        }
    }

    struct Case: Sendable, CustomTestStringConvertible {
        let row: String
        let call: Call
        let status: Int
        let fixture: String?
        var headers: [String: String] = [:]
        var contract = true
        let expected: MessageKey

        var testDescription: String { "\(row) on \(call.rawValue)" }
    }

    static let notFoundCases = ["unknown", "expired", "used", "revoked", "a signed-out private read"]

    static let cases: [Case] = notFoundCases.map {
        Case(row: "404 not-found, \($0)", call: .resolve, status: 404, fixture: "not-found", expected: .resolveNotFound)
    } + [
        Case(row: "400 malformed on resolve", call: .resolve, status: 400, fixture: "malformed", expected: .typeMalformed),
        Case(row: "400 malformed, check-mismatch", call: .resolve, status: 400, fixture: "malformed-check-mismatch",
             expected: .scanCheckMismatch),
        Case(row: "400 malformed, wrong-length", call: .resolve, status: 400, fixture: "malformed-wrong-length",
             expected: .scanWrongLength),
        Case(row: "400 contract-version", call: .resolve, status: 400, fixture: "contract-version", expected: .updateApp),
        Case(row: "400 contract-version", call: .mint, status: 400, fixture: "contract-version", expected: .updateApp),
        Case(row: "400 contract-version", call: .me, status: 400, fixture: "contract-version", expected: .updateApp),
        Case(row: "400 contract-version", call: .reroll, status: 400, fixture: "contract-version", expected: .updateApp),
        Case(row: "400 malformed on a write", call: .mint, status: 400, fixture: "malformed-body", expected: .nothingSaved),
        Case(row: "400 malformed on a write", call: .recordVersion, status: 400, fixture: "malformed-body",
             expected: .nothingSaved),
        Case(row: "400 malformed on a write", call: .report, status: 400, fixture: "malformed-body", expected: .nothingSaved),
        Case(row: "no X-ZZ-Contract on an error", call: .resolve, status: 502, fixture: nil, contract: false,
             expected: .unavailable),
        Case(row: "no X-ZZ-Contract on a 200", call: .discovery, status: 200, fixture: "discovery", contract: false,
             expected: .unavailable),
        Case(row: "no X-ZZ-Contract on a 404", call: .resolve, status: 404, fixture: "not-found", contract: false,
             expected: .unavailable),
        Case(row: "422 unsupported (bare mark)", call: .resolve, status: 422, fixture: "unsupported", expected: .scanBare),
        Case(row: "422 reserved-handle", call: .mint, status: 422, fixture: "reserved-handle", expected: .reservedHandle),
        Case(row: "422 content-refused", call: .mint, status: 422, fixture: "content-refused",
             expected: .createContentRefused),
        Case(row: "422 content-refused", call: .recordVersion, status: 422, fixture: "content-refused",
             expected: .createContentRefused),
        Case(row: "403 reroll-cap", call: .reroll, status: 403, fixture: "reroll-cap", expected: .mintedRerollCap),
        Case(row: "403 scope-unavailable", call: .mint, status: 403, fixture: "scope-unavailable",
             expected: .createUnavailable),
        Case(row: "503 not-ready on mint", call: .mint, status: 503, fixture: "not-ready", expected: .createUnavailable),
        Case(row: "401 on a write", call: .mint, status: 401, fixture: "unauthorized", expected: .signInNeeded),
        Case(row: "401 on a write", call: .recordVersion, status: 401, fixture: "unauthorized", expected: .signInNeeded),
        Case(row: "401 on a write", call: .revoke, status: 401, fixture: "unauthorized", expected: .signInNeeded),
        Case(row: "401 on a write", call: .reroll, status: 401, fixture: "unauthorized", expected: .signInNeeded),
        Case(row: "403 otherwise", call: .mint, status: 403, fixture: "forbidden", expected: .forbidden),
        Case(row: "403 otherwise", call: .myCodes, status: 403, fixture: "forbidden", expected: .forbidden),
        Case(row: "409 taken", call: .mint, status: 409, fixture: "taken", expected: .taken),
        Case(row: "429", call: .resolve, status: 429, fixture: "rate-limited", headers: ["Retry-After": "7"],
             expected: .resolveRateLimited),
        Case(row: "429", call: .report, status: 429, fixture: "rate-limited", headers: ["Retry-After": "7"],
             expected: .resolveRateLimited),
        Case(row: "500 on a write", call: .mint, status: 500, fixture: "failed", expected: .nothingSaved),
        Case(row: "500 on a write", call: .recordVersion, status: 500, fixture: "failed", expected: .nothingSaved),
        Case(row: "500 on a write", call: .revoke, status: 500, fixture: "failed", expected: .nothingSaved),
        Case(row: "500 on a write", call: .reroll, status: 500, fixture: "failed", expected: .nothingSaved),
        Case(row: "500 on a write", call: .report, status: 500, fixture: "failed", expected: .nothingSaved),
        Case(row: "500 on account deletion", call: .deleteAccount, status: 500, fixture: "failed", expected: .deleteFailed),
        Case(row: "undocumented 500 on a read (T013)", call: .me, status: 500, fixture: "failed", expected: .unavailable),
        Case(row: "503 otherwise", call: .resolve, status: 503, fixture: "not-ready", expected: .unavailable),
        Case(row: "503 otherwise", call: .myCodes, status: 503, fixture: "not-ready", expected: .unavailable),
        Case(row: "a failed sign-in", call: .signIn, status: 401, fixture: "unauthorized", expected: .signInFailed),
    ]

    @Test(arguments: cases)
    func row(_ testCase: Case) async throws {
        let transport = StubTransport { _ in
            .init(testCase.status, testCase.fixture, headers: testCase.headers, contract: testCase.contract)
        }
        let service = try await service(transport)
        let failure = try #require(await testCase.call.failure(service))
        #expect(failure.message(for: testCase.call.context) == testCase.expected, "\(failure)")
    }

    @Test func rateLimitKeepsRetryAfter() async throws {
        let transport = StubTransport { _ in .init(429, "rate-limited", headers: ["Retry-After": "7"]) }
        let result = await try service(transport).resolve("zz-copper-lantern-sky-zz")
        #expect(result.failure == .rateLimited(retryAfter: 7))
    }

    @Test func noConnectionFollowsTheOfflineRules() async throws {
        let transport = StubTransport { _ in throw URLError(.notConnectedToInternet) }
        let service = try await service(transport)
        #expect(await service.resolve("zz-copper-lantern-sky-zz").failure == .noConnection)
        let mint = await service.mint(title: "Lost cat", body: "")
        #expect(mint.failure?.message(for: .mint) == .noConnection)
    }

    /// The 404 and 429 fixtures are exactly one field (FR-010, FR-033), and a
    /// body with another field is not accepted as the one not-found body.
    @Test func notFoundAndRateLimitedBodiesHaveOneField() async throws {
        #expect(try Fixture.text("not-found") == #"{"error":"not-found"}"#)
        #expect(try Fixture.text("rate-limited") == #"{"error":"rate-limited"}"#)
        for name in ["not-found", "rate-limited"] {
            let object = try JSONSerialization.jsonObject(with: try Fixture.data(name)) as? [String: Any]
            #expect(object?.count == 1)
        }
    }

    /// The 413 row: the photo upload is not offered in v1 (FR-045), so only
    /// the mapping is checked.
    @Test func payloadTooLargeSavesNothing() {
        let failure = APIFailure.response(status: 413, error: "payload-too-large", reason: nil)
        #expect(failure.message(for: .report) == .nothingSaved)
    }

    /// T013: until the pin declares 500 and 503 on every operation.
    @Test func undocumentedServerErrors() {
        #expect(APIFailure.undocumented(status: 500).message(for: .mint) == .nothingSaved)
        #expect(APIFailure.undocumented(status: 503).message(for: .mint) == .unavailable)
        #expect(APIFailure.undocumented(status: 503).message(for: .resolve) == .unavailable)
    }
}

extension Result {
    var failure: Failure? {
        if case .failure(let failure) = self { return failure }
        return nil
    }
}
