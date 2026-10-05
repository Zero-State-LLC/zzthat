import Foundation
import Observation
import ZZAPI
import ZZCore

/// Process-wide dependencies and the state every tab shares: the build, the
/// API, the session, discovery, connectivity, and the local stores
/// (runtime.md, Modules: App owns dependency wiring). Features read it and
/// call core modules through it; they never call each other.
@MainActor
@Observable
final class AppModel {
    let config: BuildConfig
    let service: ZZService
    let cache: ResolveCache
    let drafts: DraftStore
    let lastCanonical: LastCanonicalStore
    let providerStore: ProviderStore
    let photos: PhotoScratch
    let connectivity: Connectivity
    /// The wordlists bundled from contracts/wordlists (FR-044). The pin has
    /// none yet, so no local check word runs and FR-008 holds.
    let bundledLists: [WordList]

    private(set) var discovery: Discovery?
    private(set) var isSignedIn = false
    private(set) var provider: SignInProvider?
    /// Bumped after an owner write, so My codes and Code detail reload.
    private(set) var ownerRevision = 0

    init(config: BuildConfig, service: ZZService, cache: ResolveCache, drafts: DraftStore,
         lastCanonical: LastCanonicalStore, providerStore: ProviderStore, photos: PhotoScratch,
         connectivity: Connectivity, bundledLists: [WordList]) {
        self.config = config
        self.service = service
        self.cache = cache
        self.drafts = drafts
        self.lastCanonical = lastCanonical
        self.providerStore = providerStore
        self.photos = photos
        self.connectivity = connectivity
        self.bundledLists = bundledLists
        provider = providerStore.value
    }

    /// The app as the build configures it.
    static func launch(config: BuildConfig = .current) -> AppModel {
        let service: ZZService
        switch (config.kind, config.apiOrigin) {
        case (.demo, _):
            // The in-memory API. A Demo build never touches the network.
            service = ZZService(api: DemoAPI(), tokens: TokenStore(storage: MemoryRefreshTokenStorage()))
        case (_, let origin?):
            service = .live(origin: origin, storage: KeychainRefreshTokenStorage())
        case (_, nil):
            // A Release build without its injected ZZ_API_ORIGIN: every call
            // answers error.unavailable. It never falls back to demo data.
            service = .unconfigured(storage: KeychainRefreshTokenStorage())
        }
        let connectivity = Connectivity()
        if !config.isDemo { connectivity.watch() }
        return AppModel(config: config, service: service, cache: ResolveCache(), drafts: DraftStore(),
                        lastCanonical: LastCanonicalStore(), providerStore: ProviderStore(), photos: PhotoScratch(),
                        connectivity: connectivity, bundledLists: Self.loadBundledLists())
    }

    static func loadBundledLists(bundle: Bundle = .main) -> [WordList] {
        guard let folder = bundle.url(forResource: "wordlists", withExtension: nil) else { return [] }
        return (try? WordListLoader.load(directory: folder)) ?? []
    }

    var isDemo: Bool { config.isDemo }

    /// Demo builds answer from memory, so they are never offline.
    var isOnline: Bool { config.isDemo || connectivity.isOnline }

    /// The read pipeline with the bundled lists and the server's list
    /// version (FR-044).
    var pipeline: ReadPipeline {
        ReadPipeline(bundled: bundledLists, serverVersion: discovery?.wordlistVersion)
    }

    /// Discovery says which sign-in buttons to draw (FR-040).
    var authProviders: [SignInProvider] {
        (discovery?.authProviders ?? []).compactMap { SignInProvider(rawValue: $0.rawValue) }
    }

    // MARK: Launch and foreground

    func start() async {
        photos.purge()
        await service.tokens.onSignedOut { [weak self] in
            Task { @MainActor in self?.sessionEnded() }
        }
        isSignedIn = await service.tokens.hasSession
        if !isSignedIn { providerStore.value = nil }
        provider = providerStore.value
        await refreshDiscovery()
    }

    /// `GET /v1` at launch, on return to the foreground, and before Create.
    @discardableResult
    func refreshDiscovery() async -> Result<Discovery, APIFailure> {
        let result = await service.discovery()
        if case .success(let value) = result { discovery = value }
        return result
    }

    // MARK: Session

    func completeSignIn(provider: SignInProvider) {
        providerStore.value = provider
        self.provider = provider
        isSignedIn = true
    }

    /// The refresh itself returned 401 (zzThis spec 005 FR-021).
    private func sessionEnded() {
        isSignedIn = false
        provider = nil
        providerStore.value = nil
    }

    /// Account shows the provider the server reports (runtime.md, Localization).
    func noteAccount(_ account: Account) {
        guard let first = account.providers.first, let value = SignInProvider(rawValue: first.rawValue) else { return }
        providerStore.value = value
        provider = value
    }

    /// Sign-out: revoke, then delete the tokens (Auth step 8).
    func signOut() async {
        await service.signOut()
        sessionEnded()
        ownerRevision += 1
    }

    /// `DELETE /v1/me`, then the tokens, the draft, and every cache (Auth step 9).
    func deleteAccount() async -> Result<Void, APIFailure> {
        let result = await service.deleteAccount()
        guard case .success = result else { return result }
        drafts.discard()
        cache.removeAll()
        lastCanonical.value = nil
        photos.purge()
        sessionEnded()
        ownerRevision += 1
        return result
    }

    /// A 401 the refresh could not fix: the person signs in again.
    func needsSignIn() {
        Task {
            isSignedIn = await service.tokens.hasSession
            if !isSignedIn { sessionEnded() }
        }
    }

    // MARK: Owner writes

    /// After a 2xx from revoke, re-roll, or a record version, the cached
    /// resolve for that canonical form is dropped (FR-041).
    func ownerChanged(canonical: String) {
        cache.remove(canonical)
        ownerRevision += 1
    }
}
