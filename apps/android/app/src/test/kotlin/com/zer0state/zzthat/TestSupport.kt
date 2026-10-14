package com.zer0state.zzthat

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.zer0state.zzthat.api.CONTRACT_HEADER
import com.zer0state.zzthat.api.CONTRACT_VERSION
import com.zer0state.zzthat.api.DemoApi
import com.zer0state.zzthat.api.MemoryRefreshTokenStorage
import com.zer0state.zzthat.api.TokenStore
import com.zer0state.zzthat.api.ZZApiFactory
import com.zer0state.zzthat.api.ZZService
import com.zer0state.zzthat.app.AppModel
import com.zer0state.zzthat.app.BuildKind
import com.zer0state.zzthat.app.BuildSettings
import com.zer0state.zzthat.app.Connectivity
import com.zer0state.zzthat.storage.AppClock
import com.zer0state.zzthat.storage.DraftStore
import com.zer0state.zzthat.storage.HiddenStore
import com.zer0state.zzthat.storage.LastCanonicalStore
import com.zer0state.zzthat.storage.PhotoScratch
import com.zer0state.zzthat.storage.ProviderStore
import com.zer0state.zzthat.storage.ResolveCache
import com.zer0state.zzthat.storage.SavedStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/** A clock the FR-041 tests move by hand. */
class FakeClock(private var now: Long = 1_791_072_000_000L) : AppClock {
    override fun nowMillis(): Long = now

    fun advance(seconds: Long) {
        now += seconds * 1000
    }
}

/**
 * The OkHttp test server answering each operation from a handler, so app
 * tests drive the generated client with real headers (the Cache-Control a
 * resolve carries decides FR-041).
 */
class Stub(private val handler: (String, RecordedRequest) -> Reply) : Closeable {
    data class Reply(val status: Int, val json: String? = null, val headers: Map<String, String> = emptyMap())

    val operations = CopyOnWriteArrayList<String>()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val operation = operationOf(request)
                operations += operation
                val reply = handler(operation, request)
                val builder = MockResponse.Builder().code(reply.status)
                    .setHeader(CONTRACT_HEADER, CONTRACT_VERSION)
                    .setHeader("Cache-Control", "no-store")
                if (reply.json != null) builder.setHeader("Content-Type", "application/json").body(reply.json)
                reply.headers.forEach { (name, value) -> builder.setHeader(name, value) }
                return builder.build()
            }
        }
        start()
    }

    /** A live service on this server; signed in means a stored refresh token and an access token in memory. */
    fun service(signedIn: Boolean = true, interceptors: List<okhttp3.Interceptor> = emptyList()): ZZService {
        val service = ZZApiFactory.live(server.url("/").toString(), MemoryRefreshTokenStorage(if (signedIn) "refresh-0" else null), interceptors)
        if (signedIn) service.tokens.signIn("access-0", "refresh-0")
        return service
    }

    override fun close() = server.close()

    private fun operationOf(request: RecordedRequest): String {
        val path = request.url.encodedPath
        return when {
            path == "/v1" -> "getDiscovery"
            path == "/v1/auth/nonce" -> "createNonce"
            path == "/v1/auth/token" -> "exchangeToken"
            path == "/v1/auth/refresh" -> "refreshToken"
            path == "/v1/auth/revoke" -> "revokeToken"
            path == "/v1/me" -> if (request.method == "DELETE") "deleteMe" else "getMe"
            path == "/v1/me/codes" -> "listMyCodes"
            path == "/v1/codes" -> "mintCode"
            path.endsWith("/reroll") -> "rerollCode"
            path.endsWith("/revoke") -> "revokeCode"
            path.startsWith("/v1/resolve/") -> "resolveCode"
            path.endsWith("/versions") -> "addRecordVersion"
            path.startsWith("/v1/records/") -> "getRecord"
            path == "/v1/reports" -> "createReport"
            else -> "unknown $path"
        }
    }
}

object Fixtures {
    const val PUBLIC_CACHE_CONTROL = "public, max-age=60, stale-while-revalidate=300"

    /** A public resolve. [creator] is the optional RM-112 reference, left out when null, as an older server does. */
    fun resolveJson(canonical: String, title: String = "Lost cat", body: String = "Answers on this page.", creator: String? = null) =
        """{"view":"public","canonical":"$canonical","record":{"title":"$title","body":"$body","updated_at":"2026-10-04T00:00:00.123Z"},""" +
            """"share":{"text":"$canonical","url":null}${if (creator != null) ""","creator":"$creator"""" else ""}}"""

    const val NOT_FOUND = """{"error":"not-found"}"""
    const val FAILED = """{"error":"failed"}"""
}

object TestApp {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun temporaryDirectory(): File = Files.createTempDirectory("zzthat-tests").toFile().apply { deleteOnExit() }

    /** An app on the given service. [kind] is not Demo unless asked, so the connectivity flag decides online and offline. */
    fun make(
        service: ZZService,
        kind: BuildKind = BuildKind.LOCAL,
        online: Boolean = true,
        clock: FakeClock = FakeClock(),
        googleWebClientId: String = "unset",
        bundledLists: List<com.zer0state.zzthat.grammar.WordList> = emptyList(),
    ): AppModel {
        val folder = temporaryDirectory()
        val local = PreferenceDataStoreFactory.create(scope = scope) { File(folder, "local.preferences_pb") }
        return AppModel(
            config = BuildSettings(kind, "http://localhost:8787", "support@zer0state.com", null, googleWebClientId),
            service = service,
            cache = ResolveCache(File(folder, "resolve-cache.json"), clock),
            drafts = DraftStore(local),
            lastCanonical = LastCanonicalStore(local),
            providerStore = ProviderStore(local),
            hidden = HiddenStore(local),
            saved = SavedStore(local),
            photos = PhotoScratch(File(folder, "photos")),
            connectivity = Connectivity(online),
            bundledLists = bundledLists,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
    }

    fun demoService(): ZZService = ZZService(DemoApi().apis(), TokenStore(MemoryRefreshTokenStorage()))
}
