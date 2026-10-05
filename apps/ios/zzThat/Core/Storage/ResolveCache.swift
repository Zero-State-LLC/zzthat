import Foundation
import ZZAPI

/// The time source FR-041 runs on, so tests can move it.
protocol AppClock: Sendable {
    func now() -> Date
}

struct SystemClock: AppClock {
    func now() -> Date { Date() }
}

/// The one resolve cache (FR-041, FR-049): a small JSON file in the caches
/// folder, keyed by canonical code, at most 50 entries, oldest dropped first.
/// Only a resolve whose Cache-Control is the public value is ever written.
final class ResolveCache: @unchecked Sendable {
    struct Entry: Codable, Hashable, Sendable {
        let canonical: String
        let title: String
        let body: String
        let updatedAt: Date
        let shareText: String
        let fetchedAt: Date
    }

    enum Lookup: Hashable {
        /// Online, within 60 seconds of the fetch: use it.
        case fresh(Entry)
        /// Offline, within 360 seconds of the fetch: show it, labeled offline.
        case offline(Entry)
        /// Fetch first (online), or the not-on-this-device state (offline).
        case none
    }

    static let onlineLifetime: TimeInterval = 60
    static let offlineLifetime: TimeInterval = 360
    static let capacity = 50

    private let fileURL: URL
    private let clock: any AppClock
    private let lock = NSLock()
    private var entries: [String: Entry]

    init(fileURL: URL = ResolveCache.defaultURL, clock: any AppClock = SystemClock()) {
        self.fileURL = fileURL
        self.clock = clock
        if let data = try? Data(contentsOf: fileURL),
           let stored = try? JSONDecoder.cache.decode([Entry].self, from: data) {
            entries = Dictionary(stored.map { ($0.canonical, $0) }, uniquingKeysWith: { $0.fetchedAt > $1.fetchedAt ? $0 : $1 })
        } else {
            entries = [:]
        }
    }

    static var defaultURL: URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("resolve-cache.json")
    }

    /// Keeps a resolve. The caller passes only a cacheable response.
    func store(_ response: ResolveResponse) {
        guard response.cacheable else { return }
        let record = response.record
        let entry = Entry(canonical: record.canonical, title: record.record.title, body: record.record.body,
                          updatedAt: record.record.updatedAt, shareText: record.share.text, fetchedAt: clock.now())
        lock.withLock {
            entries[entry.canonical] = entry
            while entries.count > Self.capacity,
                  let oldest = entries.values.min(by: { $0.fetchedAt < $1.fetchedAt }) {
                entries[oldest.canonical] = nil
            }
            persist()
        }
    }

    func lookup(_ canonical: String, online: Bool) -> Lookup {
        lock.withLock {
            guard let entry = entries[canonical] else { return .none }
            let age = clock.now().timeIntervalSince(entry.fetchedAt)
            if age >= Self.offlineLifetime || age < 0 {
                entries[canonical] = nil
                persist()
                return .none
            }
            if online { return age < Self.onlineLifetime ? .fresh(entry) : .none }
            return .offline(entry)
        }
    }

    /// After a 2xx from revoke, re-roll, or a record version for this code,
    /// and after a not-found answer.
    func remove(_ canonical: String) {
        lock.withLock {
            guard entries.removeValue(forKey: canonical) != nil else { return }
            persist()
        }
    }

    /// `DELETE /v1/me` clears every entry.
    func removeAll() {
        lock.withLock {
            entries = [:]
            try? FileManager.default.removeItem(at: fileURL)
        }
    }

    var count: Int { lock.withLock { entries.count } }

    private func persist() {
        let sorted = entries.values.sorted { $0.fetchedAt < $1.fetchedAt }
        guard let data = try? JSONEncoder.cache.encode(sorted) else { return }
        try? data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}

private extension JSONDecoder {
    static var cache: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .secondsSince1970
        return decoder
    }
}

private extension JSONEncoder {
    static var cache: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .secondsSince1970
        return encoder
    }
}
