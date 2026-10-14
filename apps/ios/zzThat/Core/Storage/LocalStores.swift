import Foundation
import ZZAPI

/// The draft title and body, labeled "Not a code yet." (FR-024). It holds no
/// code words, is never shared, and is never resolved. Kept until mint or
/// discard.
final class DraftStore: @unchecked Sendable {
    struct Draft: Codable, Hashable {
        var title: String
        var body: String
    }

    private let defaults: UserDefaults
    private let key = "draft.record"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var draft: Draft? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(Draft.self, from: data)
    }

    func save(title: String, body: String) {
        guard !FieldLimits.isBlank(title) || !FieldLimits.isBlank(body) else { return discard() }
        defaults.set(try? JSONEncoder().encode(Draft(title: title, body: body)), forKey: key)
    }

    func discard() {
        defaults.removeObject(forKey: key)
    }
}

/// The last canonical text, so resolve can be retried (Storage table). Kept
/// until the person clears it.
final class LastCanonicalStore: @unchecked Sendable {
    private let defaults: UserDefaults
    private let key = "resolve.last_canonical"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var value: String? {
        get { defaults.string(forKey: key) }
        set {
            if let newValue { defaults.set(newValue, forKey: key) } else { defaults.removeObject(forKey: key) }
        }
    }
}

/// The codes the person hid and the creators they blocked on this device
/// (FR-035, zzThis D-2026-10-10-25). Neither list leaves the phone: hiding,
/// blocking, and undoing them call no server. Account deletion and
/// `account.unhide_all` empty both.
final class HiddenStore: @unchecked Sendable {
    private let defaults: UserDefaults
    private let key = "hidden.codes"
    private let creatorsKey = "hidden.creators"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var codes: Set<String> {
        get { Set(defaults.stringArray(forKey: key) ?? []) }
        set {
            if newValue.isEmpty { defaults.removeObject(forKey: key) } else { defaults.set(newValue.sorted(), forKey: key) }
        }
    }

    /// Blocked creator references (the Resolve `creator`, RM-112).
    var creators: Set<String> {
        get { Set(defaults.stringArray(forKey: creatorsKey) ?? []) }
        set {
            if newValue.isEmpty {
                defaults.removeObject(forKey: creatorsKey)
            } else {
                defaults.set(newValue.sorted(), forKey: creatorsKey)
            }
        }
    }
}

/// The codes the person saved on this device, newest first (FR-050, zzThis
/// RM-115). Only canonical codes: no photo and no thumbnail. Never sent
/// anywhere. Account deletion and `account.clear_saved` empty it.
final class SavedStore: @unchecked Sendable {
    private let defaults: UserDefaults
    private let key = "saved.codes"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var codes: [String] {
        get { defaults.stringArray(forKey: key) ?? [] }
        set {
            if newValue.isEmpty { defaults.removeObject(forKey: key) } else { defaults.set(newValue, forKey: key) }
        }
    }
}

/// The provider of the signed-in account, so Account can show it offline
/// (runtime.md, Screens: Account, Offline). Not a secret.
final class ProviderStore: @unchecked Sendable {
    private let defaults: UserDefaults
    private let key = "account.provider"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var value: SignInProvider? {
        get { defaults.string(forKey: key).flatMap(SignInProvider.init(rawValue:)) }
        set {
            if let newValue { defaults.set(newValue.rawValue, forKey: key) } else { defaults.removeObject(forKey: key) }
        }
    }
}

/// The photo of one scan or write check, in the caches folder only while it
/// runs, and deleted when it ends (FR-004, T063).
final class PhotoScratch: @unchecked Sendable {
    private let directory: URL
    private let lock = NSLock()
    private var current: URL?

    init(directory: URL = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("scan-photos", isDirectory: true)) {
        self.directory = directory
    }

    /// Keeps the photo for the scan in progress, replacing any earlier one.
    @discardableResult
    func begin(_ data: Data) throws -> URL {
        try lock.withLock {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            if let current { try? FileManager.default.removeItem(at: current) }
            let url = directory.appendingPathComponent(UUID().uuidString).appendingPathExtension("jpg")
            try data.write(to: url, options: [.atomic, .completeFileProtection])
            current = url
            return url
        }
    }

    var currentURL: URL? { lock.withLock { current } }

    /// The scan or write check ended: delete its photo.
    func end() {
        lock.withLock {
            if let current { try? FileManager.default.removeItem(at: current) }
            current = nil
        }
    }

    /// At launch: delete anything a crash left behind.
    func purge() {
        lock.withLock {
            try? FileManager.default.removeItem(at: directory)
            current = nil
        }
    }

    var storedFiles: [URL] {
        (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)) ?? []
    }
}
