package com.zer0state.zzthat.auth

import com.zer0state.zzthat.api.DemoApi
import com.zer0state.zzthat.api.MemoryRefreshTokenStorage
import com.zer0state.zzthat.api.MessageKey
import com.zer0state.zzthat.api.TokenStore
import com.zer0state.zzthat.api.ZZApiFactory
import com.zer0state.zzthat.api.ZZService
import com.zer0state.zzthat.api.generated.models.TokenRequest
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Sign-in leaves no half session (runtime.md, Screens: Sign in; Auth). */
class SignInFlowTest {
    private class Server(private val answer: (String) -> Pair<Int, String>) : AutoCloseable {
        val paths = CopyOnWriteArrayList<String>()
        val bodies = CopyOnWriteArrayList<String>()
        private val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    paths += path
                    bodies += request.body?.utf8().orEmpty()
                    val (status, json) = answer(path)
                    return MockResponse.Builder().code(status).setHeader("X-ZZ-Contract", "1").setHeader("Cache-Control", "no-store")
                        .setHeader("Content-Type", "application/json").body(json).build()
                }
            }
            start()
        }

        fun service(): ZZService = ZZApiFactory.live(server.url("/").toString(), MemoryRefreshTokenStorage())

        override fun close() = server.close()
    }

    private val nonce = """{"nonce":"n-1","expires_in":600}"""
    private val tokens = """{"access_token":"access-1","token_type":"Bearer","expires_in":900,"refresh_token":"refresh-1"}"""

    @Test
    fun aRefusedExchangeLeavesNoSession() = runBlocking {
        Server { path -> if (path == "/v1/auth/nonce") 200 to nonce else 401 to """{"error":"unauthorized"}""" }.use { server ->
            val service = server.service()
            val outcome = SignInFlow(service).run(TokenRequest.Provider.google, online = true, demo = false) { ProviderToken.Token("id-1") }
            assertEquals(SignInFlow.Outcome.Failed(MessageKey.SIGN_IN_FAILED), outcome)
            assertFalse(service.tokens.hasSession)
            assertEquals(listOf("/v1/auth/nonce", "/v1/auth/token"), server.paths)
        }
    }

    @Test
    fun aFailedProviderSignInLeavesNoSessionAndSendsNoToken() = runBlocking {
        Server { 200 to nonce }.use { server ->
            val service = server.service()
            val outcome = SignInFlow(service).run(TokenRequest.Provider.google, online = true, demo = false) { ProviderToken.Failed }
            assertEquals(SignInFlow.Outcome.Failed(MessageKey.SIGN_IN_FAILED), outcome)
            assertFalse(service.tokens.hasSession)
            assertEquals(listOf("/v1/auth/nonce"), server.paths)
        }
    }

    @Test
    fun aCancelledProviderSheetShowsNothing() = runBlocking {
        Server { 200 to nonce }.use { server ->
            val outcome = SignInFlow(server.service()).run(TokenRequest.Provider.google, true, false) { ProviderToken.Cancelled }
            assertEquals(SignInFlow.Outcome.Cancelled, outcome)
        }
    }

    /** The provider gets the raw server nonce, and the exchange sends the same nonce with the ID token. */
    @Test
    fun theServerNonceGoesToTheProviderAndTheExchange() = runBlocking {
        Server { path -> if (path == "/v1/auth/nonce") 200 to nonce else 200 to tokens }.use { server ->
            val service = server.service()
            var seen: String? = null
            val outcome = SignInFlow(service).run(TokenRequest.Provider.google, true, false) {
                seen = it
                ProviderToken.Token("id-1")
            }
            assertEquals(SignInFlow.Outcome.SignedIn, outcome)
            assertEquals("n-1", seen)
            assertTrue(server.bodies.last().contains("\"nonce\":\"n-1\""))
            assertTrue(server.bodies.last().contains("\"id_token\":\"id-1\""))
            assertTrue(service.tokens.hasSession)
        }
    }

    @Test
    fun offlineSignInIsRefusedWithoutACall() = runBlocking {
        Server { 500 to "{}" }.use { server ->
            val outcome = SignInFlow(server.service()).run(TokenRequest.Provider.google, online = false, demo = false) { ProviderToken.Failed }
            assertEquals(SignInFlow.Outcome.Failed(MessageKey.NO_CONNECTION), outcome)
            assertTrue(server.paths.isEmpty())
        }
    }

    @Test
    fun demoButtonsSignInWithoutAProvider() = runBlocking {
        val service = ZZService(DemoApi().apis(), TokenStore(MemoryRefreshTokenStorage()))
        val outcome = SignInFlow(service).run(TokenRequest.Provider.google, online = true, demo = true) { error("no provider in Demo") }
        assertEquals(SignInFlow.Outcome.SignedIn, outcome)
        assertTrue(service.tokens.hasSession)
    }

    @Test
    fun aPlaceholderWebClientIdHidesGoogle() {
        assertFalse(GoogleIdTokens.isConfigured("unset"))
        assertFalse(GoogleIdTokens.isConfigured(""))
        assertTrue(GoogleIdTokens.isConfigured("123-abc.apps.googleusercontent.com"))
    }
}
