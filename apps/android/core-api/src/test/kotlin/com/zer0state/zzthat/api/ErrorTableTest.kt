package com.zer0state.zzthat.api

import com.zer0state.zzthat.api.generated.models.ReportRequest
import com.zer0state.zzthat.api.generated.models.TokenRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.net.UnknownHostException

/** The calls the error table is checked on, and the context that picks each sentence. */
enum class Call(val context: CallContext) {
    RESOLVE(CallContext.RESOLVE),
    MINT(CallContext.MINT),
    RECORD_VERSION(CallContext.RECORD_VERSION),
    REPORT(CallContext.REPORT),
    REROLL(CallContext.REROLL),
    REVOKE(CallContext.REVOKE),
    ME(CallContext.OWNER_READ),
    MY_CODES(CallContext.OWNER_READ),
    RECORD(CallContext.OWNER_READ),
    DELETE_ACCOUNT(CallContext.DELETE_ACCOUNT),
    SIGN_IN(CallContext.SIGN_IN),
    DISCOVERY(CallContext.DISCOVERY),
    ;

    suspend fun failure(service: ZZService): ApiFailure? = when (this) {
        RESOLVE -> service.resolve("zz-copper-lantern-sky-zz")
        MINT -> service.mint("Lost cat", "Answers on this page.")
        RECORD_VERSION -> service.addVersion("record-1", "Lost cat", "")
        REPORT -> service.report("zz-copper-lantern-sky-zz", ReportRequest.Reason.spam, "")
        REROLL -> service.reroll("code-1")
        REVOKE -> service.revoke("code-1")
        ME -> service.me()
        MY_CODES -> service.myCodes()
        RECORD -> service.record("record-1")
        DELETE_ACCOUNT -> service.deleteAccount()
        SIGN_IN -> service.exchange(TokenRequest.Provider.dev, "dev:local-tester", "n")
        DISCOVERY -> service.discovery()
    }.failureOrNull
}

/** One fixture test per row of the spec.md error table (tasks.md T011, T013, T014), through the generated client and the OkHttp test server. */
@RunWith(Parameterized::class)
class ErrorTableTest(
    @Suppress("unused") private val row: String,
    private val call: Call,
    private val status: Int,
    private val json: String?,
    private val headers: Map<String, String>,
    private val contract: Boolean,
    private val expected: MessageKey,
) {
    @Test
    fun row() = runBlocking {
        ApiStub { ApiStub.Reply(status, json, headers, contract) }.use { stub ->
            val failure = call.failure(stub.service())
            assertNotNull("expected a failure", failure)
            assertEquals("$failure", expected, failure!!.message(call.context))
        }
    }

    companion object {
        private fun case(
            row: String,
            call: Call,
            status: Int,
            json: String?,
            expected: MessageKey,
            headers: Map<String, String> = emptyMap(),
            contract: Boolean = true,
        ): Array<Any> = arrayOf("$row on ${call.name.lowercase()}", call, status, json ?: "", headers, contract, expected)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf("unknown", "expired", "used", "revoked", "a signed-out private read").map {
            case("404 not-found, $it", Call.RESOLVE, 404, Fixtures.NOT_FOUND, MessageKey.RESOLVE_NOT_FOUND)
        } + listOf(
            case("400 malformed on resolve", Call.RESOLVE, 400, Fixtures.MALFORMED, MessageKey.TYPE_MALFORMED),
            case("400 malformed, check-mismatch", Call.RESOLVE, 400, Fixtures.MALFORMED_CHECK_MISMATCH, MessageKey.SCAN_CHECK_MISMATCH),
            case("400 malformed, wrong-length", Call.RESOLVE, 400, Fixtures.MALFORMED_WRONG_LENGTH, MessageKey.SCAN_WRONG_LENGTH),
            case("400 contract-version", Call.RESOLVE, 400, Fixtures.CONTRACT_VERSION_BODY, MessageKey.UPDATE_APP),
            case("400 contract-version", Call.MINT, 400, Fixtures.CONTRACT_VERSION_BODY, MessageKey.UPDATE_APP),
            case("400 contract-version", Call.ME, 400, Fixtures.CONTRACT_VERSION_BODY, MessageKey.UPDATE_APP),
            case("400 contract-version", Call.REROLL, 400, Fixtures.CONTRACT_VERSION_BODY, MessageKey.UPDATE_APP),
            case("400 malformed on a write", Call.MINT, 400, Fixtures.MALFORMED_BODY, MessageKey.NOTHING_SAVED),
            case("400 malformed on a write", Call.RECORD_VERSION, 400, Fixtures.MALFORMED_BODY, MessageKey.NOTHING_SAVED),
            case("400 malformed on a write", Call.REPORT, 400, Fixtures.MALFORMED_BODY, MessageKey.NOTHING_SAVED),
            case("no X-ZZ-Contract on an error", Call.RESOLVE, 502, null, MessageKey.UNAVAILABLE, contract = false),
            case("no X-ZZ-Contract on a 200", Call.DISCOVERY, 200, Fixtures.DISCOVERY, MessageKey.UNAVAILABLE, contract = false),
            case("no X-ZZ-Contract on a 404", Call.RESOLVE, 404, Fixtures.NOT_FOUND, MessageKey.UNAVAILABLE, contract = false),
            case("422 unsupported (bare mark)", Call.RESOLVE, 422, Fixtures.UNSUPPORTED, MessageKey.SCAN_BARE),
            case("422 reserved-handle", Call.MINT, 422, Fixtures.RESERVED_HANDLE, MessageKey.RESERVED_HANDLE),
            case("422 content-refused", Call.MINT, 422, Fixtures.CONTENT_REFUSED, MessageKey.CREATE_CONTENT_REFUSED),
            case("422 content-refused", Call.RECORD_VERSION, 422, Fixtures.CONTENT_REFUSED, MessageKey.CREATE_CONTENT_REFUSED),
            case("403 reroll-cap", Call.REROLL, 403, Fixtures.REROLL_CAP, MessageKey.MINTED_REROLL_CAP),
            case("403 scope-unavailable", Call.MINT, 403, Fixtures.SCOPE_UNAVAILABLE, MessageKey.CREATE_UNAVAILABLE),
            case("503 not-ready on mint", Call.MINT, 503, Fixtures.NOT_READY, MessageKey.CREATE_UNAVAILABLE),
            case("401 on a write", Call.MINT, 401, Fixtures.UNAUTHORIZED, MessageKey.SIGN_IN_NEEDED),
            case("401 on a write", Call.RECORD_VERSION, 401, Fixtures.UNAUTHORIZED, MessageKey.SIGN_IN_NEEDED),
            case("401 on a write", Call.REVOKE, 401, Fixtures.UNAUTHORIZED, MessageKey.SIGN_IN_NEEDED),
            case("401 on a write", Call.REROLL, 401, Fixtures.UNAUTHORIZED, MessageKey.SIGN_IN_NEEDED),
            case("403 otherwise", Call.MINT, 403, Fixtures.FORBIDDEN, MessageKey.FORBIDDEN),
            case("403 otherwise", Call.MY_CODES, 403, Fixtures.FORBIDDEN, MessageKey.FORBIDDEN),
            case("409 taken", Call.MINT, 409, Fixtures.TAKEN, MessageKey.TAKEN),
            case("429", Call.RESOLVE, 429, Fixtures.RATE_LIMITED, MessageKey.RESOLVE_RATE_LIMITED, mapOf("Retry-After" to "7")),
            case("429", Call.REPORT, 429, Fixtures.RATE_LIMITED, MessageKey.RESOLVE_RATE_LIMITED, mapOf("Retry-After" to "7")),
            case("500 on a write", Call.MINT, 500, Fixtures.FAILED, MessageKey.NOTHING_SAVED),
            case("500 on a write", Call.RECORD_VERSION, 500, Fixtures.FAILED, MessageKey.NOTHING_SAVED),
            case("500 on a write", Call.REVOKE, 500, Fixtures.FAILED, MessageKey.NOTHING_SAVED),
            case("500 on a write", Call.REROLL, 500, Fixtures.FAILED, MessageKey.NOTHING_SAVED),
            case("500 on a write", Call.REPORT, 500, Fixtures.FAILED, MessageKey.NOTHING_SAVED),
            case("500 on account deletion", Call.DELETE_ACCOUNT, 500, Fixtures.FAILED, MessageKey.DELETE_FAILED),
            case("500 on a read (T013)", Call.ME, 500, Fixtures.FAILED, MessageKey.UNAVAILABLE),
            case("503 otherwise", Call.RESOLVE, 503, Fixtures.NOT_READY, MessageKey.UNAVAILABLE),
            case("503 otherwise", Call.MY_CODES, 503, Fixtures.NOT_READY, MessageKey.UNAVAILABLE),
            case("a failed sign-in", Call.SIGN_IN, 401, Fixtures.UNAUTHORIZED, MessageKey.SIGN_IN_FAILED),
        )
    }
}

class ErrorTableRulesTest {
    @Test
    fun rateLimitKeepsRetryAfter() = runBlocking {
        ApiStub { ApiStub.Reply(429, Fixtures.RATE_LIMITED, mapOf("Retry-After" to "7")) }.use { stub ->
            assertEquals(ApiFailure.RateLimited(7), stub.service().resolve("zz-copper-lantern-sky-zz").failureOrNull)
        }
    }

    @Test
    fun noConnectionFollowsTheOfflineRules() = runBlocking {
        ApiStub(Fixtures::ok).use { stub ->
            val offline = okhttp3.Interceptor { throw UnknownHostException("offline") }
            val service = stub.service(networkInterceptors = listOf(offline))
            assertEquals(ApiFailure.NoConnection, service.resolve("zz-copper-lantern-sky-zz").failureOrNull)
            assertEquals(MessageKey.NO_CONNECTION, service.mint("Lost cat", "").failureOrNull?.message(CallContext.MINT))
        }
    }

    /** The 404 and 429 fixtures are exactly one field (FR-010, FR-033), and a body with another field is not the one not-found body. */
    @Test
    fun notFoundAndRateLimitedBodiesHaveOneField() = runBlocking {
        assertEquals("""{"error":"not-found"}""", Fixtures.NOT_FOUND)
        assertEquals("""{"error":"rate-limited"}""", Fixtures.RATE_LIMITED)
        for (body in listOf(Fixtures.NOT_FOUND, Fixtures.RATE_LIMITED)) assertEquals(1, Json.parseToJsonElement(body).jsonObject.size)
        ApiStub { ApiStub.Reply(404, """{"error":"not-found","hint":"zz-copper-lantern-sky-zz"}""") }.use { stub ->
            assertEquals(ApiFailure.Unavailable, stub.service().resolve("zz-copper-lantern-sky-zz").failureOrNull)
        }
        ApiStub { ApiStub.Reply(429, """{"error":"rate-limited","code":"zz-x-zz"}""", mapOf("Retry-After" to "7")) }.use { stub ->
            assertEquals(ApiFailure.Unavailable, stub.service().resolve("zz-copper-lantern-sky-zz").failureOrNull)
        }
    }

    /** The 413 row: the photo upload is not offered in v1 (FR-045), so only the mapping is checked. */
    @Test
    fun payloadTooLargeSavesNothing() {
        assertEquals(MessageKey.NOTHING_SAVED, ApiFailure.Response(413, "payload-too-large", null).message(CallContext.REPORT))
    }

    /** T013: a 500 on a write saves nothing, and a 503 other than not-ready on mint is unavailable. */
    @Test
    fun serverErrors() {
        assertEquals(MessageKey.NOTHING_SAVED, ApiFailure.Response(500, "failed", null).message(CallContext.MINT))
        assertEquals(MessageKey.UNAVAILABLE, ApiFailure.Response(503, "failed", null).message(CallContext.MINT))
        assertEquals(MessageKey.UNAVAILABLE, ApiFailure.Response(503, "not-ready", null).message(CallContext.RESOLVE))
    }
}
