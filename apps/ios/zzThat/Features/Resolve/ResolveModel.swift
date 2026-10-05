import Foundation
import Observation
import ZZAPI

/// The record a resolve shows: the title and body from the server, and the
/// share text, which is the canonical code (FR-020).
struct ResolvedView: Hashable, Sendable {
    let canonical: String
    let title: String
    let body: String
    let shareText: String

    init(canonical: String, title: String, body: String, shareText: String) {
        self.canonical = canonical
        self.title = title
        self.body = body
        self.shareText = shareText
    }

    init(_ record: ResolvedRecord) {
        self.init(canonical: record.canonical, title: record.record.title, body: record.record.body,
                  shareText: record.share.text)
    }

    init(_ entry: ResolveCache.Entry) {
        self.init(canonical: entry.canonical, title: entry.title, body: entry.body, shareText: entry.shareText)
    }
}

/// What the Resolve screen shows (runtime.md, Screens: Resolve).
enum ResolvePhase: Hashable {
    case loading(previous: ResolvedView?)
    /// A live record, or the FR-041 copy labeled offline.
    case record(ResolvedView, offline: Bool)
    /// The one not-found state (FR-010).
    case notFound
    /// Offline and not cached, or cached longer ago than 360 seconds.
    case notOnDevice
    /// One sentence from the error table. 429 carries `Retry-After`.
    case failure(CopyKey, retryAfter: Int?)
}

/// Resolve with the FR-041 cache in front of `GET /v1/resolve/{code}`.
/// Resolve sends the canonical code only, with no Authorization (FR-016).
@MainActor
enum ResolveLoader {
    static func load(_ canonical: String, app: AppModel) async -> ResolvePhase {
        app.lastCanonical.value = canonical
        guard app.isOnline else { return offline(canonical, app: app) }
        if case .fresh(let entry) = app.cache.lookup(canonical, online: true) {
            return .record(ResolvedView(entry), offline: false)
        }
        switch await app.service.resolve(canonical) {
        case .success(let response):
            // A no-store answer is never kept, and it replaces an older copy.
            if response.cacheable { app.cache.store(response, for: canonical) } else { app.cache.remove(canonical) }
            return .record(ResolvedView(response.record), offline: false)
        case .failure(.notFound):
            app.cache.remove(canonical)
            return .notFound
        case .failure(.noConnection):
            return offline(canonical, app: app)
        case .failure(.rateLimited(let retryAfter)):
            return .failure(.resolveRateLimited, retryAfter: retryAfter)
        case .failure(let failure):
            return .failure(CopyKey(failure.message(for: .resolve)), retryAfter: nil)
        }
    }

    /// Offline: the copy, labeled offline, until 360 seconds after the fetch.
    static func offline(_ canonical: String, app: AppModel) -> ResolvePhase {
        if case .offline(let entry) = app.cache.lookup(canonical, online: false) {
            return .record(ResolvedView(entry), offline: true)
        }
        return .notOnDevice
    }
}

@MainActor
@Observable
final class ResolveModel {
    let canonical: String
    private(set) var phase: ResolvePhase = .loading(previous: nil)
    /// While a 429 wait runs, Try again stays off (FR-033).
    private(set) var waiting = false
    @ObservationIgnored private var loaded = false

    init(canonical: String) {
        self.canonical = canonical
    }

    func loadOnce(app: AppModel) async {
        guard !loaded else { return }
        loaded = true
        await load(app: app)
    }

    func load(app: AppModel) async {
        if case .record(let view, _) = phase { phase = .loading(previous: view) } else { phase = .loading(previous: nil) }
        phase = await ResolveLoader.load(canonical, app: app)
        if case .failure(_, let retryAfter?) = phase { await wait(seconds: retryAfter) }
    }

    private func wait(seconds: Int) async {
        waiting = true
        try? await Task.sleep(for: .seconds(max(seconds, 1)))
        waiting = false
    }
}
