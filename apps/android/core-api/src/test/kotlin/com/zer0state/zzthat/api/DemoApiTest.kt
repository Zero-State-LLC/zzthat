package com.zer0state.zzthat.api

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** tasks.md T017: the demo implementation answers from fixtures. */
class DemoApiTest {
    private fun demo() = ZZService(DemoApi().apis(), TokenStore(MemoryRefreshTokenStorage()))

    @Test
    fun discoveryHasFreePublicOn() = runBlocking {
        val discovery = (demo().discovery() as ApiResult.Ok).value
        assertTrue(discovery.freePublic)
        assertFalse(discovery.photoReads)
    }

    @Test
    fun aFixtureCodeResolvesAndAnUnknownOneIsNotFound() = runBlocking {
        val service = demo()
        val found = (service.resolve("zz-kathy-lost-cat-zz") as ApiResult.Ok).value
        assertEquals("Lost cat", found.record.record.title)
        assertNull(found.record.share.url)
        assertEquals("Block this creator shows in Demo", DemoApi.DEMO_CREATOR, found.record.creator)
        assertFalse("demo answers are no-store", found.cacheable)
        assertEquals(ApiFailure.NotFound, service.resolve("zz-hello-zz").failureOrNull)
    }

    @Test
    fun rerollStopsAtTheCap() = runBlocking {
        val service = demo()
        var code = (service.mint("Lost cat", "") as ApiResult.Ok).value
        assertEquals(3, code.rerollsRemaining)
        for (remaining in listOf(2, 1, 0)) {
            code = (service.reroll(code.id) as ApiResult.Ok).value
            assertEquals(remaining, code.rerollsRemaining)
        }
        assertEquals(MessageKey.MINTED_REROLL_CAP, service.reroll(code.id).failureOrNull?.message(CallContext.REROLL))
    }

    @Test
    fun aRevokedCodeIsNotFound() = runBlocking {
        val service = demo()
        val active = (service.myCodes() as ApiResult.Ok).value.codes.first { it.status.value == "active" }
        assertTrue(service.revoke(active.id) is ApiResult.Ok)
        assertEquals(ApiFailure.NotFound, service.resolve(active.canonical).failureOrNull)
    }

    @Test
    fun aVersionChangesTheTitle() = runBlocking {
        val service = demo()
        val row = (service.myCodes() as ApiResult.Ok).value.codes.first()
        assertTrue(service.addVersion(row.recordId, "Parcel (demo)", "") is ApiResult.Ok)
        val record = (service.record(row.recordId) as ApiResult.Ok).value
        assertEquals("Parcel (demo)", record.title)
        assertEquals(2, record.version)
    }

    @Test
    fun signInAndOutKeepTheDemoSessionInMemory() = runBlocking {
        val service = demo()
        assertTrue(service.exchange(com.zer0state.zzthat.api.generated.models.TokenRequest.Provider.google, "demo", "demo-nonce") is ApiResult.Ok)
        assertTrue(service.tokens.hasSession)
        service.signOut()
        assertFalse(service.tokens.hasSession)
    }
}
