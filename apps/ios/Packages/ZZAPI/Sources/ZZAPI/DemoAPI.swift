import Foundation
import OpenAPIRuntime

/// The in-memory implementation of the generated `APIProtocol` for Demo
/// builds (runtime.md, API client step 4; tasks.md T017). It makes no network
/// call and issues nothing: the fixture codes are not real codes.
///
/// Fixture text is zzThis's own demo data at the pin: the resolve example in
/// the pinned OpenAPI file, the demo records in `src/content/demo.ts` (docs/
/// SPEC.md Section 4.2), and the fixture-7 issuable codes in vectors.json.
public actor DemoAPI: APIProtocol {
    public struct Record: Sendable, Hashable {
        public let title: String
        public let body: String
    }

    public static let fixedDate = Date(timeIntervalSince1970: 1_791_072_000) // 2026-10-04T00:00:00Z

    /// Public demo records by canonical code.
    public static let records: [String: Record] = [
        "zz-kathy-lost-cat-zz": Record(title: "Lost cat", body: "Answers on this page."),
        "zz-copper-lantern-sky-zz": Record(
            title: "Crate, field supply (demo)",
            body: "NSN: DEMO-0000-00-000-0001\nDocument number: DEMO-DOC-0001\nHand receipt: DEMO-HR-01"),
        "zz-river-falcon-sky-zz": Record(
            title: "Parcel (demo)",
            body: "Reference: DEMO-PARCEL-01\nStatus: Ready for drop-off (demo)"),
        "zz-b2-4-zz": Record(title: "Duffel group B2, item 4 (demo)", body: "Hand receipt: DEMO-HR-02"),
    ]

    /// The words a demo mint and its re-rolls show, from the fixture-7 list.
    public static let mintSequence: [(canonical: String, checkWord: String)] = [
        ("zz-copper-sky-river-zz", "river"),
        ("zz-lantern-maple-copper-zz", "copper"),
        ("zz-sky-river-maple-zz", "maple"),
        ("zz-harbor-falcon-maple-zz", "maple"),
    ]

    struct Owned {
        var code: OwnedCode
        var record: Record
        var version: Int
    }

    private var owned: [Owned]
    private var minted = 0

    public init() {
        owned = [
            Owned(code: Self.ownedCode(id: "demo-code-1", canonical: "zz-copper-lantern-sky-zz", checkWord: "sky",
                                       status: .active, recordID: "demo-record-1",
                                       title: Self.records["zz-copper-lantern-sky-zz"]!.title),
                  record: Self.records["zz-copper-lantern-sky-zz"]!, version: 1),
            Owned(code: Self.ownedCode(id: "demo-code-2", canonical: "zz-river-falcon-sky-zz", checkWord: "sky",
                                       status: .active, recordID: "demo-record-2",
                                       title: Self.records["zz-river-falcon-sky-zz"]!.title),
                  record: Self.records["zz-river-falcon-sky-zz"]!, version: 1),
            Owned(code: Self.ownedCode(id: "demo-code-3", canonical: "zz-b2-4-zz", checkWord: nil,
                                       status: .revoked, recordID: "demo-record-3",
                                       title: Self.records["zz-b2-4-zz"]!.title),
                  record: Self.records["zz-b2-4-zz"]!, version: 1),
        ]
    }

    static func ownedCode(id: String, canonical: String, checkWord: String?, status: OwnedCode.StatusPayload,
                          recordID: String, title: String) -> OwnedCode {
        OwnedCode(id: id, canonical: canonical, checkWord: checkWord, status: status, recordId: recordID,
                  scope: .freePublic, rerollsRemaining: 0, createdAt: fixedDate, title: title)
    }

    public static let discovery = Discovery(
        contract: ._1, freePublic: true, authProviders: [.apple, .google], scopes: [.freePublic],
        wordlistVersion: "fixture-7", photoReads: false)

    public static let account = Account(id: "demo-account", providers: [.apple], createdAt: fixedDate)

    // MARK: APIProtocol

    public func getDiscovery(_ input: Operations.GetDiscovery.Input) async throws -> Operations.GetDiscovery.Output {
        .ok(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore), body: .json(Self.discovery)))
    }

    public func getOpenApi(_ input: Operations.GetOpenApi.Input) async throws -> Operations.GetOpenApi.Output {
        .serviceUnavailable(Self.notReady)
    }

    public func createNonce(_ input: Operations.CreateNonce.Input) async throws -> Operations.CreateNonce.Output {
        .ok(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore),
                  body: .json(.init(nonce: "demo-nonce", expiresIn: 600))))
    }

    public func exchangeToken(_ input: Operations.ExchangeToken.Input) async throws -> Operations.ExchangeToken.Output {
        .ok(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore),
                  body: .json(.init(accessToken: "demo-access", tokenType: .bearer, expiresIn: ._900,
                                    refreshToken: "demo-refresh"))))
    }

    public func refreshToken(_ input: Operations.RefreshToken.Input) async throws -> Operations.RefreshToken.Output {
        .ok(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore),
                  body: .json(.init(accessToken: "demo-access", tokenType: .bearer, expiresIn: ._900,
                                    refreshToken: "demo-refresh"))))
    }

    public func revokeToken(_ input: Operations.RevokeToken.Input) async throws -> Operations.RevokeToken.Output {
        .noContent(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore)))
    }

    public func getMe(_ input: Operations.GetMe.Input) async throws -> Operations.GetMe.Output {
        .ok(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore), body: .json(Self.account)))
    }

    public func deleteMe(_ input: Operations.DeleteMe.Input) async throws -> Operations.DeleteMe.Output {
        .noContent(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore)))
    }

    public func mintCode(_ input: Operations.MintCode.Input) async throws -> Operations.MintCode.Output {
        guard case .json(let request) = input.body else { return .badRequest(Self.error(.malformed)) }
        let code = nextCode(recordID: "demo-record-new", record: request.record, rerolls: 3)
        return .created(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore), body: .json(code)))
    }

    public func rerollCode(_ input: Operations.RerollCode.Input) async throws -> Operations.RerollCode.Output {
        guard let index = owned.firstIndex(where: { $0.code.id == input.path.id }) else {
            return .notFound(Self.notFound)
        }
        let current = owned[index]
        guard current.code.rerollsRemaining > 0, current.code.status == .active else {
            return .forbidden(Self.error(.rerollCap))
        }
        // The previous words are retired and leave the owner list (spec 005 FR-006).
        owned.remove(at: index)
        let code = nextCode(recordID: current.code.recordId,
                            record: .init(title: current.record.title, body: current.record.body),
                            rerolls: current.code.rerollsRemaining - 1)
        return .ok(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore), body: .json(code)))
    }

    public func listMyCodes(_ input: Operations.ListMyCodes.Input) async throws -> Operations.ListMyCodes.Output {
        .ok(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore),
                  body: .json(.init(codes: owned.map(\.code), nextCursor: nil))))
    }

    public func revokeCode(_ input: Operations.RevokeCode.Input) async throws -> Operations.RevokeCode.Output {
        guard let index = owned.firstIndex(where: { $0.code.id == input.path.id }) else {
            return .notFound(Self.notFound)
        }
        owned[index].code.status = .revoked
        return .ok(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore), body: .json(.init(status: .revoked))))
    }

    public func resolveCode(_ input: Operations.ResolveCode.Input) async throws -> Operations.ResolveCode.Output {
        let canonical = input.path.code
        let mine = owned.first { $0.code.canonical == canonical && $0.code.status == .active }?.record
        let isRevoked = owned.contains { $0.code.canonical == canonical && $0.code.status != .active }
        guard let record = mine ?? (isRevoked ? nil : Self.records[canonical]) else {
            return .notFound(Self.notFound)
        }
        let body = ResolvedRecord(
            view: ._public, canonical: canonical,
            record: .init(title: record.title, body: record.body, updatedAt: Self.fixedDate),
            share: .init(text: canonical, url: nil))
        return .ok(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore), body: .json(body)))
    }

    public func getRecord(_ input: Operations.GetRecord.Input) async throws -> Operations.GetRecord.Output {
        guard let row = owned.first(where: { $0.code.recordId == input.path.id }) else {
            return .notFound(Self.notFound)
        }
        let record = OwnerRecord(id: row.code.recordId, version: row.version, title: row.record.title,
                                 body: row.record.body, updatedAt: Self.fixedDate)
        return .ok(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore), body: .json(record)))
    }

    public func addRecordVersion(_ input: Operations.AddRecordVersion.Input) async throws -> Operations.AddRecordVersion.Output {
        guard case .json(let record) = input.body,
              let index = owned.firstIndex(where: { $0.code.recordId == input.path.id }) else {
            return .notFound(Self.notFound)
        }
        owned[index].record = Record(title: record.title, body: record.body)
        owned[index].code.title = record.title
        owned[index].version += 1
        return .created(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore),
                              body: .json(.init(id: input.path.id, version: owned[index].version))))
    }

    public func submitRead(_ input: Operations.SubmitRead.Input) async throws -> Operations.SubmitRead.Output {
        .serviceUnavailable(Self.notReady)
    }

    public func createReport(_ input: Operations.CreateReport.Input) async throws -> Operations.CreateReport.Output {
        .accepted(.init(headers: .init(xZZContract: ._1, cacheControl: .noStore), body: .json(.init(id: "demo-report"))))
    }

    public func listAudit(_ input: Operations.ListAudit.Input) async throws -> Operations.ListAudit.Output {
        .serviceUnavailable(Self.notReady)
    }

    // MARK: Fixtures

    private func nextCode(recordID: String, record: Components.Schemas.EverydayRecord, rerolls: Int) -> MintedCode {
        let next = Self.mintSequence[minted % Self.mintSequence.count]
        minted += 1
        let id = "demo-minted-\(minted)"
        let code = MintedCode(id: id, canonical: next.canonical, checkWord: next.checkWord, status: .active,
                              recordId: recordID, scope: .freePublic, rerollsRemaining: rerolls,
                              createdAt: Self.fixedDate)
        owned.insert(Owned(code: Self.ownedCode(id: id, canonical: next.canonical, checkWord: next.checkWord,
                                                status: .active, recordID: recordID, title: record.title),
                           record: Record(title: record.title, body: record.body), version: 1), at: 0)
        owned[0].code.rerollsRemaining = rerolls
        return code
    }

    static let notFound = Components.Responses.NotFound(
        headers: .init(xZZContract: ._1, cacheControl: .noStore), body: .json(.init(error: .notFound)))

    static let notReady = Components.Responses.NotReady(
        headers: .init(xZZContract: ._1, cacheControl: .noStore), body: .json(.init(error: .notReady)))

    static func error(_ code: Components.Schemas._Error._ErrorPayload) -> Components.Responses._Error {
        .init(headers: .init(xZZContract: ._1, cacheControl: .noStore), body: .json(.init(error: code)))
    }
}
