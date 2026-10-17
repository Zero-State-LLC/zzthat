package com.zer0state.zzthat.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.zer0state.zzthat.api.ApiResult
import com.zer0state.zzthat.api.DemoApi
import com.zer0state.zzthat.api.MemoryRefreshTokenStorage
import com.zer0state.zzthat.api.TokenStore
import com.zer0state.zzthat.api.ZZApiFactory
import com.zer0state.zzthat.api.ZZService
import com.zer0state.zzthat.api.generated.models.Account
import com.zer0state.zzthat.api.generated.models.Discovery
import com.zer0state.zzthat.api.generated.models.TokenRequest
import com.zer0state.zzthat.auth.EncryptedRefreshTokenStorage
import com.zer0state.zzthat.auth.KeystoreTokenCipher
import com.zer0state.zzthat.grammar.ReadPipeline
import com.zer0state.zzthat.grammar.WordList
import com.zer0state.zzthat.grammar.WordListLoader
import com.zer0state.zzthat.storage.DraftStore
import com.zer0state.zzthat.storage.HiddenStore
import com.zer0state.zzthat.storage.LastCanonicalStore
import com.zer0state.zzthat.storage.PhotoScratch
import com.zer0state.zzthat.storage.ProviderStore
import com.zer0state.zzthat.storage.ResolveCache
import com.zer0state.zzthat.storage.SavedStore
import com.zer0state.zzthat.storage.TermsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Process-wide dependencies and the state every tab shares: the build, the
 * API, the session, discovery, connectivity, and the local stores (runtime.md,
 * Modules: App owns dependency wiring). Features read it and call core
 * modules through it; they never call each other.
 */
class AppModel(
    val config: BuildSettings,
    val service: ZZService,
    val cache: ResolveCache,
    val drafts: DraftStore,
    val lastCanonical: LastCanonicalStore,
    val providerStore: ProviderStore,
    /** The codes hidden and the creators blocked on this device (FR-035). Never sent anywhere. */
    val hidden: HiddenStore,
    /** The codes saved on this device (FR-050). Never sent anywhere. */
    val saved: SavedStore,
    /** The Terms version accepted on this device (design/UX.md, Terms). Never sent anywhere. */
    val terms: TermsStore,
    /** The current Terms version, `terms.version` from the pinned copy.json. It is compared, never shown. */
    val termsVersion: String,
    val photos: PhotoScratch,
    val connectivity: Connectivity,
    /** The wordlists bundled from contracts/wordlists (FR-044). */
    val bundledLists: List<WordList>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    var discovery by mutableStateOf<Discovery?>(null)
        private set
    var isSignedIn by mutableStateOf(false)
        private set

    /** The provider of the signed-in account: `apple`, `google`, or `dev`. */
    var provider by mutableStateOf<String?>(null)
        private set

    /** Bumped after an owner write, so My codes and Code detail reload. */
    var ownerRevision by mutableIntStateOf(0)
        private set

    /** Bumped after a hide, an unhide, a block, an unblock, or the Account reset, so an open Resolve shows the new state. */
    var hiddenRevision by mutableIntStateOf(0)
        private set

    /** Bumped after a save, a removal, or a clear, so Saved and an open Resolve read the list again. */
    var savedRevision by mutableIntStateOf(0)
        private set

    /** The Terms version accepted on this device, mirrored from [terms] by [loadTerms]. */
    var acceptedTermsVersion by mutableStateOf<String?>(null)
        private set

    /** The signed-in account's id from `GET /v1/me`, for `account.id` (D-2026-10-13-08). Memory only: shown, never stored. */
    var accountId by mutableStateOf<String?>(null)
        private set

    val isDemo: Boolean get() = config.isDemo

    /** Demo builds answer from memory, so they are never offline. */
    val isOnline: Boolean get() = isDemo || connectivity.isOnline

    /** The read pipeline with the bundled lists and the server's list version (FR-044). */
    val pipeline: ReadPipeline get() = ReadPipeline.of(bundledLists, discovery?.wordlistVersion)

    /**
     * Discovery decides the sign-in buttons (FR-040). A provider the contract
     * does not list yet has no button, so it is dropped (T106), as on iOS.
     */
    val authProviders: List<String> get() = discovery?.authProviders.orEmpty()
        .filter { it != Discovery.AuthProviders.unknown_default_open_api }
        .map { it.value }

    // MARK: Launch and foreground

    suspend fun start() {
        withContext(Dispatchers.IO) { photos.purge() }
        service.tokens.onSignedOut { scope.launch { sessionEnded() } }
        isSignedIn = withContext(Dispatchers.IO) { service.tokens.hasSession }
        if (!isSignedIn) providerStore.set(null)
        provider = providerStore.value()
        loadTerms()
        refreshDiscovery()
    }

    /** `GET /v1` at launch, on return to the foreground, and before Create. */
    suspend fun refreshDiscovery(): ApiResult<Discovery> {
        val result = service.discovery()
        if (result is ApiResult.Ok) discovery = result.value
        return result
    }

    // MARK: Session

    suspend fun completeSignIn(provider: TokenRequest.Provider) {
        providerStore.set(provider.value)
        this.provider = provider.value
        isSignedIn = true
    }

    /** The refresh itself returned 401 (zzThis spec 005 FR-021), or the person signed out. */
    private suspend fun sessionEnded() {
        isSignedIn = false
        provider = null
        accountId = null
        providerStore.set(null)
    }

    /** Account shows the provider the server reports (runtime.md, Localization). */
    suspend fun noteAccount(account: Account) {
        accountId = account.id
        // A provider the contract does not list yet has no name to show (T106).
        val first = account.providers.firstOrNull { it != Account.Providers.unknown_default_open_api } ?: return
        providerStore.set(first.value)
        provider = first.value
    }

    /** Sign-out: revoke, then delete the tokens (Auth step 8). */
    suspend fun signOut() {
        service.signOut()
        sessionEnded()
        ownerRevision += 1
    }

    /**
     * `DELETE /v1/me`, then the tokens, the draft, the hidden codes and blocked
     * creators, Saved, the accepted Terms version, and every cache (Auth step 9,
     * FR-035, FR-050; design/UX.md, Terms).
     */
    suspend fun deleteAccount(): ApiResult<Unit> {
        val result = service.deleteAccount()
        if (result !is ApiResult.Ok) return result
        drafts.discard()
        hidden.clear()
        hiddenRevision += 1
        saved.clear()
        savedRevision += 1
        terms.clear()
        acceptedTermsVersion = null
        withContext(Dispatchers.IO) {
            cache.removeAll()
            photos.purge()
        }
        lastCanonical.set(null)
        sessionEnded()
        ownerRevision += 1
        return result
    }

    // MARK: Terms (design/UX.md, Terms). Device only: none of these calls a server.

    /** The Terms step shows before the next create: nothing is stored, or the stored version is not the current `terms.version`. */
    val needsTerms: Boolean get() = acceptedTermsVersion != termsVersion

    /** Reads the accepted version from the device. */
    suspend fun loadTerms() {
        acceptedTermsVersion = terms.acceptedVersion()
    }

    /** The person confirmed 18 or older and accepted the Terms. */
    suspend fun acceptTerms() {
        terms.accept(termsVersion)
        acceptedTermsVersion = termsVersion
    }

    /** A 401 the refresh could not fix: the person signs in again. */
    suspend fun needsSignIn() {
        isSignedIn = withContext(Dispatchers.IO) { service.tokens.hasSession }
        if (!isSignedIn) sessionEnded()
    }

    // MARK: Hidden codes

    /** Hide this code on this device (FR-035). No server call. */
    suspend fun hide(canonical: String) {
        hidden.hide(canonical)
        hiddenRevision += 1
    }

    /** Unhide this code on this device (FR-035). No server call. */
    suspend fun unhide(canonical: String) {
        hidden.unhide(canonical)
        hiddenRevision += 1
    }

    /** The Account screen reset: every hidden code and every blocked creator shows again (FR-035). No server call. */
    suspend fun unhideAll() {
        hidden.clear()
        hiddenRevision += 1
    }

    // MARK: Blocked creators

    /** Block this creator on this device (FR-035, zzThis RM-112). [creator] is the opaque Resolve reference. No server call. */
    suspend fun block(creator: String) {
        hidden.block(creator)
        hiddenRevision += 1
    }

    /** Unblock this creator on this device (FR-035). No server call. */
    suspend fun unblock(creator: String) {
        hidden.unblock(creator)
        hiddenRevision += 1
    }

    // MARK: Saved

    /** Save this canonical code on this device (FR-050). No server call. */
    suspend fun save(canonical: String) {
        saved.save(canonical)
        savedRevision += 1
    }

    /** Remove this code from Saved (FR-050). No server call. */
    suspend fun unsave(canonical: String) {
        saved.unsave(canonical)
        savedRevision += 1
    }

    /** The Account screen's Clear saved codes (FR-050). No server call. */
    suspend fun clearSaved() {
        saved.clear()
        savedRevision += 1
    }

    // MARK: Owner writes

    /** After a 2xx from revoke, re-roll, or a record version, the cached resolve for that canonical form is dropped (FR-041). */
    suspend fun ownerChanged(canonical: String) {
        withContext(Dispatchers.IO) { cache.remove(canonical) }
        ownerRevision += 1
    }

    /** A new code was minted, so My codes reads the list again. */
    fun ownerMinted() {
        ownerRevision += 1
    }

    companion object {
        /** The app as the build configures it. */
        fun launch(context: Context, config: BuildSettings = BuildSettings.current): AppModel {
            val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val local = PreferenceDataStoreFactory.create(scope = ioScope) { context.preferencesDataStoreFile("zzthat_local") }
            val service = when {
                // The in-memory API. A Demo build never touches the network.
                config.isDemo -> ZZService(DemoApi().apis(), TokenStore(MemoryRefreshTokenStorage()))
                else -> {
                    val session = PreferenceDataStoreFactory.create(scope = ioScope) { context.preferencesDataStoreFile("zzthat_session") }
                    val storage = EncryptedRefreshTokenStorage(session, KeystoreTokenCipher())
                    val origin = config.apiOrigin
                    // A Release build without its injected origin answers error.unavailable. It never falls back to demo data.
                    if (origin != null) ZZApiFactory.live(origin, storage) else ZZApiFactory.unconfigured(storage)
                }
            }
            val connectivity = Connectivity()
            if (!config.isDemo) connectivity.watch(context)
            return AppModel(
                config = config,
                service = service,
                cache = ResolveCache(ResolveCache.defaultFile(context.cacheDir)),
                drafts = DraftStore(local),
                lastCanonical = LastCanonicalStore(local),
                providerStore = ProviderStore(local),
                hidden = HiddenStore(local),
                saved = SavedStore(local),
                terms = TermsStore(local),
                termsVersion = context.getString(com.zer0state.zzthat.R.string.terms_version),
                photos = PhotoScratch(PhotoScratch.defaultDirectory(context.cacheDir)),
                connectivity = connectivity,
                bundledLists = loadBundledLists(context),
            )
        }

        /** The bundled lists in assets/wordlists, from contracts/wordlists at the pin (FR-044). */
        fun loadBundledLists(context: Context): List<WordList> {
            // A list that cannot be read is refused, never fixed up, and then no
            // local check word runs (FR-008): the server's answer decides.
            return try {
                context.assets.list(WORDLISTS).orEmpty().filter(WordListLoader::isListFile).sorted().map { name ->
                    val text = context.assets.open("$WORDLISTS/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
                    WordListLoader.parse(WordListLoader.versionOf(name), text)
                }
            } catch (error: Exception) {
                emptyList()
            }
        }

        const val WORDLISTS = "wordlists"
    }
}
