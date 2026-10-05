package com.zer0state.zzthat.api

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Interceptor
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The OkHttp test server (runtime.md, Testing: API fixtures), answering each
 * contract operation from a handler and recording every request it gets.
 */
class ApiStub(private val handler: (Sent) -> Reply) : Closeable {
    data class Sent(val operation: String, val request: RecordedRequest) {
        val body: String? get() = request.body?.utf8()
        val authorization: String? get() = request.headers["Authorization"]
        val path: String get() = request.url.encodedPath
    }

    data class Reply(
        val status: Int,
        val json: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val contract: Boolean = true,
    )

    private val server = MockWebServer()
    private val log = CopyOnWriteArrayList<Sent>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val sent = Sent(operationOf(request), request)
                log += sent
                val reply = handler(sent)
                val builder = MockResponse.Builder().code(reply.status).setHeader("Cache-Control", "no-store")
                if (reply.contract) builder.setHeader(CONTRACT_HEADER, CONTRACT_VERSION)
                if (reply.json != null) builder.setHeader("Content-Type", "application/json").body(reply.json)
                reply.headers.forEach { (name, value) -> builder.setHeader(name, value) }
                return builder.build()
            }
        }
        server.start()
    }

    val origin: String get() = server.url("/").toString()

    val sent: List<Sent> get() = log.toList()

    val operations: List<String> get() = sent.map { it.operation }

    /** A live service on this server, with the refresh token stored and, when given, an access token in memory. */
    fun service(
        refreshToken: String? = null,
        accessToken: String? = null,
        storage: RefreshTokenStorage = MemoryRefreshTokenStorage(refreshToken),
        networkInterceptors: List<Interceptor> = emptyList(),
    ): ZZService {
        val service = ZZApiFactory.live(origin, storage, networkInterceptors)
        if (accessToken != null) service.tokens.signIn(accessToken, refreshToken)
        return service
    }

    override fun close() = server.close()

    companion object {
        fun operationOf(request: RecordedRequest): String {
            val path = request.url.encodedPath
            val method = request.method
            return when {
                method == "GET" && path == "/v1" -> "getDiscovery"
                method == "POST" && path == "/v1/auth/nonce" -> "createNonce"
                method == "POST" && path == "/v1/auth/token" -> "exchangeToken"
                method == "POST" && path == "/v1/auth/refresh" -> "refreshToken"
                method == "POST" && path == "/v1/auth/revoke" -> "revokeToken"
                method == "GET" && path == "/v1/me" -> "getMe"
                method == "DELETE" && path == "/v1/me" -> "deleteMe"
                method == "GET" && path == "/v1/me/codes" -> "listMyCodes"
                method == "POST" && path == "/v1/codes" -> "mintCode"
                method == "POST" && path.matches(Regex("/v1/codes/[^/]+/reroll")) -> "rerollCode"
                method == "POST" && path.matches(Regex("/v1/codes/[^/]+/revoke")) -> "revokeCode"
                method == "GET" && path.startsWith("/v1/resolve/") -> "resolveCode"
                method == "GET" && path.matches(Regex("/v1/records/[^/]+")) -> "getRecord"
                method == "POST" && path.matches(Regex("/v1/records/[^/]+/versions")) -> "addRecordVersion"
                method == "POST" && path == "/v1/reports" -> "createReport"
                else -> "unknown $method $path"
            }
        }
    }
}

/** Success and error bodies (tasks.md T011). Times carry milliseconds; the decoding test also sends whole seconds. */
object Fixtures {
    const val MILLIS = "2026-10-04T00:00:00.123Z"
    const val ACCOUNT = """{"id":"account-1","providers":["dev"],"created_at":"$MILLIS"}"""
    const val CODE_PAGE = """{"codes":[{"id":"code-1","canonical":"zz-copper-lantern-sky-zz","check_word":"sky","status":"active","record_id":"record-1","scope":"free_public","rerolls_remaining":3,"created_at":"$MILLIS","title":"Lost cat"}],"next_cursor":null}"""
    const val CODE = """{"id":"code-1","canonical":"zz-copper-lantern-sky-zz","check_word":"sky","status":"active","record_id":"record-1","scope":"free_public","rerolls_remaining":3,"created_at":"$MILLIS"}"""
    const val DISCOVERY = """{"contract":"1","free_public":true,"auth_providers":["dev"],"scopes":["free_public"],"wordlist_version":"fixture-7","photo_reads":false}"""
    const val NONCE = """{"nonce":"nonce-1","expires_in":600}"""
    const val OWNER_RECORD = """{"id":"record-1","version":2,"title":"Lost cat","body":"Answers on this page.","updated_at":"$MILLIS"}"""
    const val RESOLVE = """{"view":"public","canonical":"zz-kathy-lost-cat-zz","record":{"title":"Lost cat","body":"Answers on this page.","updated_at":"$MILLIS"},"share":{"text":"zz-kathy-lost-cat-zz","url":null}}"""
    const val TOKENS = """{"access_token":"access-1","token_type":"Bearer","expires_in":900,"refresh_token":"refresh-1"}"""
    const val REPORT_ID = """{"id":"report-1"}"""

    const val CONTENT_REFUSED = """{"error":"content-refused"}"""
    const val CONTRACT_VERSION_BODY = """{"error":"contract-version"}"""
    const val FAILED = """{"error":"failed"}"""
    const val FORBIDDEN = """{"error":"forbidden"}"""
    const val MALFORMED_BODY = """{"error":"malformed"}"""
    const val MALFORMED_CHECK_MISMATCH = """{"error":"malformed","reason":"check-mismatch"}"""
    const val MALFORMED_WRONG_LENGTH = """{"error":"malformed","reason":"wrong-length"}"""
    const val MALFORMED = """{"error":"malformed","reason":"no-marker"}"""
    const val NOT_FOUND = """{"error":"not-found"}"""
    const val NOT_READY = """{"error":"not-ready"}"""
    const val RATE_LIMITED = """{"error":"rate-limited"}"""
    const val REROLL_CAP = """{"error":"reroll-cap"}"""
    const val RESERVED_HANDLE = """{"error":"reserved-handle"}"""
    const val SCOPE_UNAVAILABLE = """{"error":"scope-unavailable"}"""
    const val TAKEN = """{"error":"taken"}"""
    const val UNAUTHORIZED = """{"error":"unauthorized"}"""
    const val UNSUPPORTED = """{"error":"unsupported","reason":"bare-mark-needs-context"}"""

    /** The success answer for each operation. */
    fun ok(sent: ApiStub.Sent): ApiStub.Reply = when (sent.operation) {
        "resolveCode" -> ApiStub.Reply(200, RESOLVE, mapOf("Cache-Control" to PUBLIC_CACHE_CONTROL))
        "getMe" -> ApiStub.Reply(200, ACCOUNT)
        "getDiscovery" -> ApiStub.Reply(200, DISCOVERY)
        "createNonce" -> ApiStub.Reply(200, NONCE)
        "exchangeToken", "refreshToken" -> ApiStub.Reply(200, TOKENS)
        "revokeToken", "deleteMe" -> ApiStub.Reply(204)
        "mintCode" -> ApiStub.Reply(201, CODE)
        "createReport" -> ApiStub.Reply(202, REPORT_ID)
        else -> ApiStub.Reply(500, FAILED)
    }
}
