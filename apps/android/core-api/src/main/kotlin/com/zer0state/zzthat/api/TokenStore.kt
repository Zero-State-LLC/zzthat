package com.zer0state.zzthat.api

/**
 * Where the refresh token lives between launches. The app keeps it encrypted
 * with an Android Keystore key in DataStore (runtime.md, Auth step 6). Tests
 * and the Demo build use memory.
 */
interface RefreshTokenStorage {
    fun load(): String?

    fun save(token: String)

    fun delete()
}

/** A refresh token kept in memory only, for tests and the Demo build. */
class MemoryRefreshTokenStorage(token: String? = null) : RefreshTokenStorage {
    @Volatile private var token: String? = token

    override fun load(): String? = token

    override fun save(token: String) {
        this.token = token
    }

    override fun delete() {
        token = null
    }
}

/** The answer of one `POST /v1/auth/refresh`. */
sealed interface RefreshOutcome {
    data class Refreshed(val accessToken: String, val refreshToken: String) : RefreshOutcome

    /** The refresh itself returned 401. Only this signs the person out (zzThis spec 005 FR-021). */
    data object Rejected : RefreshOutcome
}

/** Runs one refresh call. It blocks the calling thread, and throws when there is no answer. */
fun interface Refresher {
    fun refresh(refreshToken: String): RefreshOutcome
}

/**
 * The access token in memory and the one refresh in flight (runtime.md, API
 * client step 8). The refresh is synchronized: a request that gets a 401 while
 * a refresh runs waits for it, then retries once with the new token, and a
 * caller whose failed token is no longer the current one takes the current
 * token without refreshing again.
 */
class TokenStore(private val storage: RefreshTokenStorage, refresher: Refresher? = null) {
    private val lock = Any()

    @Volatile private var accessToken: String? = null

    @Volatile var refresher: Refresher? = refresher

    @Volatile private var signedOutHandler: (() -> Unit)? = null

    /** Called when a refresh returns 401 and the session ends. */
    fun onSignedOut(handler: () -> Unit) {
        signedOutHandler = handler
    }

    val currentAccessToken: String? get() = accessToken

    val hasSession: Boolean get() = accessToken != null || storage.load() != null

    /** The refresh token, for sign-out's `POST /v1/auth/revoke`. */
    val refreshToken: String? get() = storage.load()

    fun signIn(accessToken: String, refreshToken: String?) {
        synchronized(lock) {
            refreshToken?.let(storage::save)
            this.accessToken = accessToken
        }
    }

    /** Sign-out and account deletion: delete both tokens (Auth steps 8 and 9). */
    fun clear() {
        synchronized(lock) {
            accessToken = null
            storage.delete()
        }
    }

    /**
     * The token to send now. With no token in memory but a stored refresh
     * token (a fresh launch), this runs the refresh first.
     */
    fun tokenForRequest(): String? = accessToken ?: refreshedAfter(null)

    /**
     * The token to retry with after [failed] got a 401, or null when there is
     * no session any more. Concurrent callers share one refresh.
     */
    fun refreshedAfter(failed: String?): String? {
        val handler: (() -> Unit)?
        synchronized(lock) {
            val current = accessToken
            if (current != null && current != failed) return current
            val stored = storage.load() ?: return null
            val run = refresher ?: return null
            when (val outcome = run.refresh(stored)) {
                is RefreshOutcome.Refreshed -> {
                    storage.save(outcome.refreshToken)
                    accessToken = outcome.accessToken
                    return outcome.accessToken
                }
                RefreshOutcome.Rejected -> {
                    accessToken = null
                    storage.delete()
                    handler = signedOutHandler
                }
            }
        }
        handler?.invoke()
        return null
    }
}
