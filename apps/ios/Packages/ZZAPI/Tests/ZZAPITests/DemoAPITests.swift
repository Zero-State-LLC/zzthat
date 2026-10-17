import Foundation
import Testing
@testable import ZZAPI

/// tasks.md T017: the demo implementation answers from fixtures.
@Suite("Demo API")
struct DemoAPITests {
    static func demo() -> ZZService {
        ZZService(api: DemoAPI(), tokens: TokenStore(storage: MemoryRefreshTokenStorage()))
    }

    @Test func discoveryHasFreePublicOn() async throws {
        let discovery = try await Self.demo().discovery().get()
        #expect(discovery.freePublic)
        #expect(discovery.photoReads == false)
    }

    @Test func aFixtureCodeResolvesAndAnUnknownOneIsNotFound() async throws {
        let service = Self.demo()
        let found = try await service.resolve("zz-kathy-lost-cat-zz").get()
        #expect(found.record.record.title == "Lost cat")
        #expect(found.record.share.url == nil)
        #expect(await service.resolve("zz-hello-zz").failure == .notFound)
    }

    @Test func rerollStopsAtTheCap() async throws {
        let service = Self.demo()
        var code = try await service.mint(title: "Lost cat", body: "").get()
        #expect(code.rerollsRemaining == 5)
        var seen = [code.canonical]
        for remaining in [4, 3, 2, 1, 0] {
            code = try await service.reroll(codeID: code.id).get()
            #expect(code.rerollsRemaining == remaining)
            // A re-roll retires the old words for good (design/UX.md, Minted code).
            #expect(!seen.contains(code.canonical))
            seen.append(code.canonical)
        }
        let capped = await service.reroll(codeID: code.id)
        #expect(capped.failure?.message(for: .reroll) == .mintedRerollCap)
    }

    @Test func aRevokedCodeIsNotFound() async throws {
        let service = Self.demo()
        let rows = try await service.myCodes().get().codes
        let active = try #require(rows.first { $0.status == .active })
        try await service.revoke(codeID: active.id).get()
        #expect(await service.resolve(active.canonical).failure == .notFound)
    }

    @Test func aVersionChangesTheTitle() async throws {
        let service = Self.demo()
        let row = try #require(try await service.myCodes().get().codes.first)
        try await service.addVersion(recordID: row.recordId, title: "Parcel (demo)", body: "").get()
        let record = try await service.record(id: row.recordId).get()
        #expect(record.title == "Parcel (demo)")
        #expect(record.version == 2)
    }
}
