package com.zer0state.zzthat.api

import com.zer0state.zzthat.api.generated.models.Discovery
import com.zer0state.zzthat.api.generated.models.OwnedCode
import com.zer0state.zzthat.api.generated.models.Resolve
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * tasks.md T095, zzThis RM-075 (D-2026-10-10-12): the generated client is a
 * tolerant reader, so an additive, optional field such as the planned
 * Resolve `creator` (RM-112) does not break a shipped app. A value a
 * response enum does not list yet decodes to the generator's
 * unknown_default_open_api case (T106, #54, D-2026-10-13-09).
 */
class TolerantReaderTest {
    private fun resolve(body: String) = runBlocking {
        ApiStub { ApiStub.Reply(200, body) }.use { stub -> stub.service().resolve("zz-kathy-lost-cat-zz") }
    }

    @Test
    fun ignoresUnknownKeys() {
        val result = resolve(
            """{"view":"public","canonical":"zz-kathy-lost-cat-zz","creator":"c-1","later":{"a":[1,2]},""" +
                """"record":{"title":"Lost cat","body":"Answers on this page.","updated_at":"2026-10-04T00:00:00.123Z","extra":true},""" +
                """"share":{"text":"zz-kathy-lost-cat-zz","url":null,"extra":"x"}}""",
        )
        assertEquals("Lost cat", (result as ApiResult.Ok).value.record.record.title)
    }

    @Test
    fun resolveWithAnUnknownViewShowsTheRecord() {
        val result = resolve(
            """{"view":"auditor","canonical":"zz-kathy-lost-cat-zz",""" +
                """"record":{"title":"Lost cat","body":"Answers on this page.","updated_at":"2026-10-04T00:00:00.123Z"},""" +
                """"share":{"text":"zz-kathy-lost-cat-zz","url":null}}""",
        )
        val record = (result as ApiResult.Ok).value.record
        assertEquals(Resolve.View.unknown_default_open_api, record.view)
        assertEquals("Lost cat", record.record.title)
        assertEquals("Answers on this page.", record.record.body)
        assertEquals("zz-kathy-lost-cat-zz", record.share.text)
    }

    @Test
    fun codesWithAnUnknownStatusAndScopeDecode() = runBlocking {
        val page = Fixtures.CODE_PAGE.replace("\"active\"", "\"paused\"").replace("\"free_public\"", "\"galaxy\"")
        val result = ApiStub { ApiStub.Reply(200, page) }.use { stub -> stub.service(accessToken = "a").myCodes() }
        val row = (result as ApiResult.Ok).value.codes.single()
        assertEquals(OwnedCode.Status.unknown_default_open_api, row.status)
        assertEquals(OwnedCode.Scope.unknown_default_open_api, row.scope)
        assertEquals("Lost cat", row.title)
    }

    @Test
    fun discoveryWithAnUnknownProviderDecodes() = runBlocking {
        val body = Fixtures.DISCOVERY.replace("[\"dev\"]", "[\"passkey\",\"dev\"]")
        val result = ApiStub { ApiStub.Reply(200, body) }.use { stub -> stub.service().discovery() }
        val discovery = (result as ApiResult.Ok).value
        assertEquals(listOf(Discovery.AuthProviders.unknown_default_open_api, Discovery.AuthProviders.dev), discovery.authProviders)
    }

    /** An error body is not a record: an error code the contract does not list stays unavailable, as before T106. */
    @Test
    fun anUnknownErrorCodeIsUnavailable() = runBlocking {
        val result = ApiStub { ApiStub.Reply(403, """{"error":"brand-new"}""") }.use { stub ->
            stub.service(accessToken = "a").myCodes()
        }
        assertEquals(ApiFailure.Unavailable, result.failureOrNull)
    }
}
