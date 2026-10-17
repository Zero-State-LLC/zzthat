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
    let hiddenStore: HiddenStore
    let savedStore: SavedStore
    let termsStore: TermsStore
    /// The current Terms version, `terms.version` from the pinned copy.json
    /// (design/UX.md, Terms). It is compared, never shown.
    let termsVersion: String
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
    /// The codes hidden on this device (FR-035), mirrored from `hiddenStore`
    /// so every screen that shows one updates at once.
    private(set) var hiddenCodes: Set<String>
    /// Creators blocked on this device (FR-035, RM-112).
    private(set) var blockedCreators: Set<String>
    /// Codes saved on this device, newest first (FR-050).
    private(set) var savedCodes: [String]
    /// The Terms version accepted on this device, mirrored from `termsStore`.
    private(set) var acceptedTermsVersion: String?
    /// The signed-in account's id from `GET /v1/me`, for `account.id`
    /// (D-2026-10-13-08). Memory only: it is shown, never stored.
    private(set) var accountID: String?

    init(config: BuildConfig, service: ZZService, cache: ResolveCache, drafts: DraftStore,
         lastCanonical: LastCanonicalStore, providerStore: ProviderStore, hiddenStore: HiddenStore,
         savedStore: SavedStore, termsStore: TermsStore, termsVersion: String = CopyKey.termsVersion.text,
         photos: PhotoScratch, connectivity: Connectivity, bundledLists: [WordList]) {
        self.config = config
        self.service = service
        self.cache = cache
        self.drafts = drafts
        self.lastCanonical = lastCanonical
        self.providerStore = providerStore
        self.hiddenStore = hiddenStore
        self.savedStore = savedStore
        self.termsStore = termsStore
        self.termsVersion = termsVersion
        self.photos = photos
        self.connectivity = connectivity
        self.bundledLists = bundledLists
        provider = providerStore.value
        hiddenCodes = hiddenStore.codes
        blockedCreators = hiddenStore.creators
        savedCodes = savedStore.codes
        acceptedTermsVersion = termsStore.acceptedVersion
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
                        lastCanonical: LastCanonicalStore(), providerStore: ProviderStore(), hiddenStore: HiddenStore(),
                        savedStore: SavedStore(), termsStore: TermsStore(), photos: PhotoScratch(), connectivity: connectivity, bundledLists: Self.loadBundledLists())
    }

    nonisolated static func loadBundledLists(bundle: Bundle = .main) -> [WordList] {
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
        accountID = nil
        providerStore.value = nil
    }

    /// Account shows the provider the server reports (runtime.md, Localization).
    func noteAccount(_ account: Account) {
        accountID = account.id
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

    /// `DELETE /v1/me`, then the tokens, the draft, every cache, the hidden
    /// codes and blocked creators, Saved, and the accepted Terms version
    /// (Auth step 9, FR-035, FR-050; design/UX.md, Terms).
    func deleteAccount() async -> Result<Void, APIFailure> {
        let result = await service.deleteAccount()
        guard case .success = result else { return result }
        drafts.discard()
        cache.removeAll()
        lastCanonical.value = nil
        unhideAll()
        clearSaved()
        termsStore.acceptedVersion = nil
        acceptedTermsVersion = nil
        photos.purge()
        sessionEnded()
        ownerRevision += 1
        return result
    }

    // MARK: Hidden codes (FR-035). Device only: none of these calls a server.

    func isHidden(_ canonical: String) -> Bool { hiddenCodes.contains(canonical) }

    func hide(_ canonical: String) {
        hiddenCodes.insert(canonical)
        hiddenStore.codes = hiddenCodes
    }

    func unhide(_ canonical: String) {
        hiddenCodes.remove(canonical)
        hiddenStore.codes = hiddenCodes
    }

    func isBlocked(creator: String?) -> Bool { creator.map(blockedCreators.contains) ?? false }

    func block(creator: String) {
        blockedCreators.insert(creator)
        hiddenStore.creators = blockedCreators
    }

    func unblock(creator: String) {
        blockedCreators.remove(creator)
        hiddenStore.creators = blockedCreators
    }

    /// `account.unhide_all`, and account deletion: hidden codes and blocked
    /// creators.
    func unhideAll() {
        hiddenCodes = []
        blockedCreators = []
        hiddenStore.codes = []
        hiddenStore.creators = []
    }

    // MARK: Saved (FR-050). Device only: none of these calls a server.

    func isSaved(_ canonical: String) -> Bool { savedCodes.contains(canonical) }

    func save(_ canonical: String) {
        savedCodes.removeAll { $0 == canonical }
        savedCodes.insert(canonical, at: 0)
        savedStore.codes = savedCodes
    }

    func unsave(_ canonical: String) {
        savedCodes.removeAll { $0 == canonical }
        savedStore.codes = savedCodes
    }

    /// `account.clear_saved`, and account deletion.
    func clearSaved() {
        savedCodes = []
        savedStore.codes = []
    }

    // MARK: Terms (design/UX.md, Terms). Device only: none of these calls a server.

    /// The Terms step shows before the next create: nothing is stored, or
    /// the stored version is not the current `terms.version`.
    var needsTerms: Bool { acceptedTermsVersion != termsVersion }

    /// The person confirmed 18 or older and accepted the Terms.
    func acceptTerms() {
        termsStore.acceptedVersion = termsVersion
        acceptedTermsVersion = termsVersion
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

    /// A new code was minted, so My codes reads the list again.
    func ownerMinted() {
        ownerRevision += 1
    }
}
