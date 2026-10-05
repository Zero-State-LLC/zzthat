import Foundation
import Testing
import ZZAPI
@testable import zzThat

/// FR-041 with a fake clock (tasks.md T060 to T062, SC-6): 60 seconds online,
/// 360 seconds offline, `no-store` never written, at most 50 entries.
@Suite("FR-041 resolve cache")
struct ResolveCacheTests {
    static func response(_ canonical: String, cacheable: Bool = true) -> ResolveResponse {
        let record = ResolvedRecord(view: ._public, canonical: canonical,
                                    record: .init(title: "Lost cat", body: "Answers on this page.",
                                                  updatedAt: Date(timeIntervalSince1970: 0)),
                                    share: .init(text: canonical, url: nil))
        return ResolveResponse(record: record, cacheable: cacheable)
    }

    static func cache(_ clock: FakeClock, url: URL = TestApp.temporaryURL("resolve-cache.json")) -> ResolveCache {
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        return ResolveCache(fileURL: url, clock: clock)
    }

    static let code = "zz-kathy-lost-cat-zz"

    @Test func onlineUsesTheCopyFor60Seconds() {
        let clock = FakeClock()
        let cache = Self.cache(clock)
        cache.store(Self.response(Self.code), for: Self.code)
        guard case .fresh(let entry) = cache.lookup(Self.code, online: true) else {
            Issue.record("expected the fresh copy")
            return
        }
        #expect(entry.title == "Lost cat")
        clock.advance(59)
        #expect(cache.lookup(Self.code, online: true) == .fresh(entry))
        clock.advance(1)
        #expect(cache.lookup(Self.code, online: true) == .none, "at 60 seconds, online fetches before showing")
    }

    @Test func offlineShowsTheCopyUntil360Seconds() {
        let clock = FakeClock()
        let cache = Self.cache(clock)
        cache.store(Self.response(Self.code), for: Self.code)
        clock.advance(60)
        guard case .offline(let entry) = cache.lookup(Self.code, online: false) else {
            Issue.record("expected the offline copy")
            return
        }
        clock.advance(299)
        #expect(cache.lookup(Self.code, online: false) == .offline(entry))
        clock.advance(1)
        #expect(cache.lookup(Self.code, online: false) == .none, "at 360 seconds, not on this device")
        #expect(cache.count == 0, "the expired entry is dropped")
    }

    @Test func aNoStoreResolveIsNeverWritten() throws {
        let clock = FakeClock()
        let url = TestApp.temporaryURL("resolve-cache.json")
        let cache = Self.cache(clock, url: url)
        cache.store(Self.response(Self.code, cacheable: false), for: Self.code)
        #expect(cache.lookup(Self.code, online: false) == .none)
        #expect(cache.count == 0)
        #expect(!FileManager.default.fileExists(atPath: url.path))
    }

    @Test func ownerWritesAndAccountDeletionDropEntries() {
        let clock = FakeClock()
        let cache = Self.cache(clock)
        cache.store(Self.response(Self.code), for: Self.code)
        cache.store(Self.response("zz-copper-lantern-sky-zz"), for: "zz-copper-lantern-sky-zz")
        cache.remove(Self.code)
        #expect(cache.lookup(Self.code, online: false) == .none)
        #expect(cache.count == 1)
        cache.removeAll()
        #expect(cache.count == 0)
    }

    @Test func atMost50EntriesOldestDroppedFirst() {
        let clock = FakeClock()
        let cache = Self.cache(clock)
        for index in 0...50 {
            cache.store(Self.response("zz-code-\(index)-zz"), for: "zz-code-\(index)-zz")
            clock.advance(1)
        }
        #expect(cache.count == 50)
        #expect(cache.lookup("zz-code-0-zz", online: false) == .none)
        if case .offline = cache.lookup("zz-code-50-zz", online: false) {} else { Issue.record("newest missing") }
    }

    @Test func theCopyLivesInOneFileAcrossLaunches() {
        let clock = FakeClock()
        let url = TestApp.temporaryURL("resolve-cache.json")
        Self.cache(clock, url: url).store(Self.response(Self.code), for: Self.code)
        clock.advance(30)
        guard case .fresh = Self.cache(clock, url: url).lookup(Self.code, online: true) else {
            Issue.record("expected the stored copy after a relaunch")
            return
        }
    }

    @Test func theDefaultFileIsInTheCachesFolder() {
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        #expect(ResolveCache.defaultURL.deletingLastPathComponent() == caches)
    }
}
