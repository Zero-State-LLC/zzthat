import Foundation
import HTTPTypes
import OpenAPIRuntime
import ZZAPI
import ZZCore
@testable import zzThat

/// A clock the FR-041 tests move by hand.
final class FakeClock: AppClock, @unchecked Sendable {
    private let lock = NSLock()
    private var current: Date

    init(_ start: Date = Date(timeIntervalSince1970: 1_791_072_000)) {
        current = start
    }

    func now() -> Date { lock.withLock { current } }

    func advance(_ seconds: TimeInterval) {
        lock.withLock { current = current.addingTimeInterval(seconds) }
    }
}

/// A transport that answers each operation from a handler, so app tests can
/// drive the generated client with real headers (the Cache-Control a
/// resolve carries decides FR-041).
final class AppStubTransport: ClientTransport, @unchecked Sendable {
    struct Reply: Sendable {
        var status: Int
        var json: String?
        var headers: [String: String] = [:]
    }

    private let lock = NSLock()
    private var log: [String] = []
    private let handler: @Sendable (String, HTTPRequest) throws -> Reply

    init(_ handler: @escaping @Sendable (String, HTTPRequest) throws -> Reply) {
        self.handler = handler
    }

    var operations: [String] { lock.withLock { log } }

    func send(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL,
              operationID: String) async throws -> (HTTPResponse, HTTPBody?) {
        lock.withLock { log.append(operationID) }
        let reply = try handler(operationID, request)
        var fields = HTTPFields()
        fields[HTTPField.Name("X-ZZ-Contract")!] = "1"
        fields[.cacheControl] = "no-store"
        if reply.json != nil { fields[.contentType] = "application/json" }
        for (name, value) in reply.headers { fields[HTTPField.Name(name)!] = value }
        return (HTTPResponse(status: .init(code: reply.status), headerFields: fields), reply.json.map { HTTPBody($0) })
    }
}

enum Fixtures {
    static let publicCacheControl = "public, max-age=60, stale-while-revalidate=300"

    static func resolveJSON(_ canonical: String, title: String = "Lost cat", body: String = "Answers on this page.",
                            creator: String? = nil) -> String {
        let creatorField = creator.map { #","creator":"\#($0)""# } ?? ""
        return #"{"view":"public","canonical":"\#(canonical)","record":{"title":"\#(title)","body":"\#(body)","updated_at":"2026-10-04T00:00:00.123Z"},"share":{"text":"\#(canonical)","url":null}\#(creatorField)}"#
    }

    static let notFound = #"{"error":"not-found"}"#
}

@MainActor
enum TestApp {
    nonisolated static func temporaryURL(_ name: String) -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("zzthat-tests-\(UUID().uuidString)")
            .appendingPathComponent(name)
    }

    nonisolated static func defaults() -> UserDefaults {
        let name = "zzthat-tests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defaults.removePersistentDomain(forName: name)
        return defaults
    }

    /// An app on the given service. `kind` is not Demo unless asked, so the
    /// connectivity flag decides online and offline.
    /// `acceptedTermsVersion` is what the device stored at the Terms step: by
    /// default the current `terms.version`, as after the step. The Terms tests
    /// pass nil for a device that never saw it, or an older version.
    static func make(service: ZZService, kind: BuildKind = .local, online: Bool = true,
                     clock: FakeClock = FakeClock(), bundledLists: [WordList] = [],
                     acceptedTermsVersion: String? = CopyKey.termsVersion.text, termsURL: URL? = nil) -> AppModel {
        let cacheURL = temporaryURL("resolve-cache.json")
        try? FileManager.default.createDirectory(at: cacheURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        let defaults = defaults()
        let terms = TermsStore(defaults: defaults)
        terms.acceptedVersion = acceptedTermsVersion
        return AppModel(config: BuildConfig(kind: kind, apiOrigin: URL(string: "http://localhost:8787"),
                                            supportEmail: "support@zer0state.com", privacyURL: nil,
                                            termsURL: termsURL, googleClientID: nil),
                        service: service, cache: ResolveCache(fileURL: cacheURL, clock: clock),
                        drafts: DraftStore(defaults: defaults), lastCanonical: LastCanonicalStore(defaults: defaults),
                        providerStore: ProviderStore(defaults: defaults), hiddenStore: HiddenStore(defaults: defaults),
                        savedStore: SavedStore(defaults: defaults), termsStore: terms,
                        photos: PhotoScratch(directory: temporaryURL("photos")),
                        connectivity: Connectivity(isOnline: online), bundledLists: bundledLists)
    }

    static func demoService() -> ZZService {
        ZZService(api: DemoAPI(), tokens: TokenStore(storage: MemoryRefreshTokenStorage()))
    }

    static func liveService(_ transport: AppStubTransport, signedIn: Bool = true) async throws -> ZZService {
        let storage = MemoryRefreshTokenStorage(token: signedIn ? "refresh-0" : nil)
        let service = ZZService.live(origin: URL(string: "http://localhost:8787")!, storage: storage,
                                     transport: transport)
        if signedIn { try await service.tokens.signIn(accessToken: "access-0", refreshToken: "refresh-0") }
        return service
    }
}
