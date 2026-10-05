package com.zer0state.zzthat.api

import com.zer0state.zzthat.api.generated.models.ReportRequest
import com.zer0state.zzthat.api.generated.models.TokenRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

/** tasks.md T010 and T012: the contract header, the bearer rule, and the single-flight refresh, on captured requests. */
class RequestTest {
    private fun keys(body: String?) = Json.parseToJsonElement(body!!).jsonObject

    @Test
    fun exactlyOneContractHeaderOnABearerRouteAndAPublicRoute() = runBlocking {
        ApiStub(Fixtures::ok).use { stub ->
            val service = stub.service(refreshToken = "refresh-0", accessToken = "access-0")
            assertTrue(service.me() is ApiResult.Ok)
            assertTrue(service.resolve("zz-copper-lantern-sky-zz") is ApiResult.Ok)
            for (operation in listOf("getMe", "resolveCode")) {
                val sent = stub.sent.first { it.operation == operation }
                assertEquals(operation, listOf("1"), sent.request.headers.values(CONTRACT_HEADER))
            }
        }
    }

    @Test
    fun theInterceptorReplacesAContractHeaderInsteadOfAdding() {
        ApiStub(Fixtures::ok).use { stub ->
            val client = OkHttpClient.Builder().addInterceptor(ContractInterceptor()).build()
            val request = Request.Builder().url(stub.origin + "v1")
                .addHeader(CONTRACT_HEADER, "_1").addHeader(CONTRACT_HEADER, "2").build()
            client.newCall(request).execute().close()
            assertEquals(listOf("1"), stub.sent.single().request.headers.values(CONTRACT_HEADER))
        }
    }

    /** T012 and SC-2: resolve sends the canonical string, no photo, and no Authorization header, even when the person is signed in. */
    @Test
    fun resolveSendsTheCanonicalCodeOnly() = runBlocking {
        ApiStub(Fixtures::ok).use { stub ->
            val service = stub.service(refreshToken = "refresh-0", accessToken = "access-0")
            val response = (service.resolve("zz-kathy-lost-cat-zz") as ApiResult.Ok).value
            assertTrue(response.cacheable)
            assertEquals("Lost cat", response.record.record.title)
            val sent = stub.sent.single()
            assertEquals("GET", sent.request.method)
            assertEquals("/v1/resolve/zz-kathy-lost-cat-zz", sent.path)
            assertNull(sent.authorization)
            assertEquals(0L, sent.request.bodySize)
        }
    }

    @Test
    fun aNoStoreResolveIsNotCacheable() = runBlocking {
        ApiStub { ApiStub.Reply(200, Fixtures.RESOLVE) }.use { stub ->
            assertFalse((stub.service().resolve("zz-kathy-lost-cat-zz") as ApiResult.Ok).value.cacheable)
        }
    }

    /** SC-2: mint sends no code string and no photo. */
    @Test
    fun mintSendsNoCodeString() = runBlocking {
        ApiStub(Fixtures::ok).use { stub ->
            val service = stub.service(refreshToken = "refresh-0", accessToken = "access-0")
            val code = (service.mint("Lost cat", "Answers on this page.") as ApiResult.Ok).value
            assertEquals("zz-copper-lantern-sky-zz", code.canonical)
            val sent = stub.sent.single()
            assertEquals("Bearer access-0", sent.authorization)
            val body = keys(sent.body)
            assertEquals(setOf("scope", "kind", "record"), body.keys)
            assertEquals("free_public", body["scope"]!!.jsonPrimitive.content)
            assertEquals("plain", body["kind"]!!.jsonPrimitive.content)
            assertEquals(setOf("title", "body"), body["record"]!!.jsonObject.keys)
        }
    }

    @Test
    fun theBearerRule() = runBlocking {
        ApiStub(Fixtures::ok).use { stub ->
            val service = stub.service(refreshToken = "refresh-0", accessToken = "access-0")
            service.discovery()
            service.nonce()
            service.exchange(TokenRequest.Provider.dev, "dev:local-tester", "nonce-1")
            service.report("zz-copper-lantern-sky-zz", ReportRequest.Reason.other, "")
            service.me()
            service.signOut()
            service.report("zz-copper-lantern-sky-zz", ReportRequest.Reason.other, "")
            val auth = stub.sent.map { it.operation to it.authorization }
            assertEquals(
                listOf("getDiscovery", "createNonce", "exchangeToken", "createReport", "getMe", "revokeToken", "createReport"),
                auth.map { it.first },
            )
            assertNull(auth[0].second)
            assertNull(auth[1].second)
            assertNull(auth[2].second)
            assertEquals("a signed-in report carries the bearer", "Bearer access-1", auth[3].second)
            assertEquals("Bearer access-1", auth[4].second)
            assertNull("revoke sends the refresh token in the body, not a bearer", auth[5].second)
            assertNull("a signed-out report carries no bearer", auth[6].second)
            assertEquals("refresh-1", keys(stub.sent[5].body)["refresh_token"]!!.jsonPrimitive.content)
            assertFalse(service.tokens.hasSession)
        }
    }

    @Test
    fun signInKeepsTheRefreshTokenAndSendsTheProvider() = runBlocking {
        ApiStub(Fixtures::ok).use { stub ->
            val storage = MemoryRefreshTokenStorage()
            val service = stub.service(storage = storage)
            assertTrue(service.exchange(TokenRequest.Provider.dev, "dev:local-tester", "nonce-1") is ApiResult.Ok)
            assertEquals("refresh-1", storage.load())
            assertEquals("access-1", service.tokens.currentAccessToken)
            val body = keys(stub.sent.single().body)
            assertEquals(setOf("provider", "client", "id_token", "nonce"), body.keys)
            assertEquals("dev", body["provider"]!!.jsonPrimitive.content)
            assertEquals("android", body["client"]!!.jsonPrimitive.content)
            assertEquals("dev:local-tester", body["id_token"]!!.jsonPrimitive.content)
            assertEquals("nonce-1", body["nonce"]!!.jsonPrimitive.content)
        }
    }

    /** T010: two concurrent calls with an expired token make exactly one refresh, and both succeed. */
    @Test
    fun concurrentCallsShareOneRefresh() = runBlocking {
        val refreshes = AtomicInteger()
        ApiStub { sent ->
            when (sent.operation) {
                "refreshToken" -> {
                    refreshes.incrementAndGet()
                    Thread.sleep(200)
                    ApiStub.Reply(200, Fixtures.TOKENS)
                }
                "getMe" -> if (sent.authorization == "Bearer access-1") {
                    ApiStub.Reply(200, Fixtures.ACCOUNT)
                } else {
                    ApiStub.Reply(401, Fixtures.UNAUTHORIZED)
                }
                else -> ApiStub.Reply(500, Fixtures.FAILED)
            }
        }.use { stub ->
            val service = stub.service(refreshToken = "refresh-0", accessToken = "expired")
            val results = listOf(async(kotlinx.coroutines.Dispatchers.IO) { service.me() }, async(kotlinx.coroutines.Dispatchers.IO) { service.me() })
                .awaitAll()
            assertTrue(results.toString(), results.all { it is ApiResult.Ok })
            assertEquals(1, refreshes.get())
            assertEquals(4, stub.operations.count { it == "getMe" })
        }
    }

    /** A launch with only a stored refresh token refreshes before the first bearer call. */
    @Test
    fun aStoredRefreshTokenStartsTheSession() = runBlocking {
        ApiStub(Fixtures::ok).use { stub ->
            val service = stub.service(refreshToken = "refresh-0")
            assertTrue(service.me() is ApiResult.Ok)
            assertEquals(listOf("refreshToken", "getMe"), stub.operations)
            assertEquals("refresh-0", keys(stub.sent[0].body)["refresh_token"]!!.jsonPrimitive.content)
            assertEquals(listOf("1"), stub.sent[1].request.headers.values(CONTRACT_HEADER))
            assertEquals("Bearer access-1", stub.sent[1].authorization)
        }
    }

    /** A build without an API origin answers `error.unavailable`, never data. */
    @Test
    fun anUnconfiguredServiceIsUnavailable() = runBlocking {
        val service = ZZApiFactory.unconfigured(MemoryRefreshTokenStorage())
        assertEquals(ApiFailure.Unavailable, service.resolve("zz-kathy-lost-cat-zz").failureOrNull)
        assertEquals(MessageKey.UNAVAILABLE, service.discovery().failureOrNull?.message(CallContext.DISCOVERY))
    }

    /** The person is signed out only when the refresh itself returns 401. */
    @Test
    fun aRejectedRefreshSignsOut() = runBlocking {
        ApiStub { ApiStub.Reply(401, Fixtures.UNAUTHORIZED) }.use { stub ->
            val service = stub.service(refreshToken = "refresh-0", accessToken = "expired")
            val signedOut = AtomicInteger()
            service.tokens.onSignedOut { signedOut.incrementAndGet() }
            val result = service.me()
            assertEquals(ApiFailure.Unauthorized, result.failureOrNull)
            assertEquals(MessageKey.SIGN_IN_NEEDED, result.failureOrNull?.message(CallContext.OWNER_READ))
            assertFalse(service.tokens.hasSession)
            assertEquals(1, signedOut.get())
            assertEquals(listOf("getMe", "refreshToken"), stub.operations)
        }
    }

    /** A refresh that fails for another reason keeps the session. */
    @Test
    fun aRefreshWithNoConnectionKeepsTheSession() = runBlocking {
        ApiStub { ApiStub.Reply(401, Fixtures.UNAUTHORIZED) }.use { stub ->
            val offlineRefresh = okhttp3.Interceptor { chain ->
                if (chain.request().url.encodedPath == "/v1/auth/refresh") throw UnknownHostException("offline")
                chain.proceed(chain.request())
            }
            val service = stub.service(refreshToken = "refresh-0", accessToken = "expired", networkInterceptors = listOf(offlineRefresh))
            assertEquals(ApiFailure.NoConnection, service.me().failureOrNull)
            assertTrue(service.tokens.hasSession)
        }
    }

    /** The retry happens once. */
    @Test
    fun theRetryHappensOnce() = runBlocking {
        ApiStub { sent ->
            if (sent.operation == "refreshToken") ApiStub.Reply(200, Fixtures.TOKENS) else ApiStub.Reply(401, Fixtures.UNAUTHORIZED)
        }.use { stub ->
            val service = stub.service(refreshToken = "refresh-0", accessToken = "expired")
            assertEquals(ApiFailure.Unauthorized, service.me().failureOrNull)
            assertEquals(listOf("getMe", "refreshToken", "getMe"), stub.operations)
        }
    }

    /** The paged lookup by code id (runtime.md, Navigation). */
    @Test
    fun ownedCodeLookupPagesUntilAMatch() = runBlocking {
        ApiStub { sent ->
            if (sent.request.url.queryParameter("cursor") == "page-2") {
                ApiStub.Reply(200, Fixtures.CODE_PAGE)
            } else {
                ApiStub.Reply(200, """{"codes":[],"next_cursor":"page-2"}""")
            }
        }.use { stub ->
            val service = stub.service(refreshToken = "refresh-0", accessToken = "access-0")
            assertEquals("Lost cat", (service.ownedCode("code-1") as ApiResult.Ok).value?.title)
            assertEquals(2, stub.sent.size)
            assertEquals("50", stub.sent[0].request.url.queryParameter("limit"))
            assertNull((service.ownedCode("nope") as ApiResult.Ok).value)
        }
    }
}
