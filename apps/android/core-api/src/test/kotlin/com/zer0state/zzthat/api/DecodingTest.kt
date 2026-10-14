package com.zer0state.zzthat.api

import com.zer0state.zzthat.api.generated.models.Account
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * tasks.md T011: success fixtures carry a timestamp with milliseconds
 * (`.123Z`) and one without (`Z`), and both decode (runtime.md, API client step 9).
 */
class DecodingTest {
    private val forms = mapOf("2026-10-04T00:00:00.123Z" to 123_000_000, "2026-10-04T00:00:00Z" to 0)

    private fun expected(nanos: Int) = OffsetDateTime.of(2026, 10, 4, 0, 0, 0, nanos, ZoneOffset.UTC).toInstant()

    private fun <T> each(fixture: String, status: Int, block: suspend (ZZService, Int) -> T) = runBlocking {
        for ((form, nanos) in forms) {
            val text = fixture.replace(Fixtures.MILLIS, form)
            ApiStub { ApiStub.Reply(status, text) }.use { stub -> block(stub.service(accessToken = "a"), nanos) }
        }
    }

    @Test
    fun code() = each(Fixtures.CODE, 201) { service, nanos ->
        val code = (service.mint("t", "") as ApiResult.Ok).value
        assertEquals(expected(nanos), code.createdAt.toInstant())
        assertEquals("sky", code.checkWord)
    }

    @Test
    fun ownedCode() = each(Fixtures.CODE_PAGE, 200) { service, nanos ->
        val page = (service.myCodes() as ApiResult.Ok).value
        assertEquals(expected(nanos), page.codes.first().createdAt.toInstant())
        assertNull(page.nextCursor)
    }

    @Test
    fun resolve() = each(Fixtures.RESOLVE, 200) { service, nanos ->
        val response = (service.resolve("zz-kathy-lost-cat-zz") as ApiResult.Ok).value
        assertEquals(expected(nanos), response.record.record.updatedAt.toInstant())
        assertNull(response.record.share.url)
        assertEquals("zz-kathy-lost-cat-zz", response.record.share.text)
    }

    /**
     * The optional Resolve `creator` (zzThis RM-112, contract 1 revision 1.1):
     * a string when the server sends one, and null when it is null or left out
     * by an older server.
     */
    @Test
    fun resolveCreator() = runBlocking {
        val withCreator = Fixtures.RESOLVE.replace(Fixtures.MILLIS, "2026-10-04T00:00:00Z").removeSuffix("}") + ""","creator":"creator-a"}"""
        val nullCreator = withCreator.replace("\"creator-a\"", "null")
        val noCreator = Fixtures.RESOLVE.replace(Fixtures.MILLIS, "2026-10-04T00:00:00Z")
        for ((text, expected) in listOf(withCreator to "creator-a", nullCreator to null, noCreator to null)) {
            ApiStub { ApiStub.Reply(200, text) }.use { stub ->
                val response = (stub.service().resolve("zz-kathy-lost-cat-zz") as ApiResult.Ok).value
                assertEquals(text, expected, response.record.creator)
            }
        }
    }

    @Test
    fun account() = each(Fixtures.ACCOUNT, 200) { service, nanos ->
        val account = (service.me() as ApiResult.Ok).value
        assertEquals(expected(nanos), account.createdAt.toInstant())
        assertEquals(listOf(Account.Providers.dev), account.providers)
    }

    @Test
    fun ownerRecord() = each(Fixtures.OWNER_RECORD, 200) { service, nanos ->
        val record = (service.record("record-1") as ApiResult.Ok).value
        assertEquals(expected(nanos), record.updatedAt.toInstant())
        assertEquals(2, record.version)
    }

    @Test
    fun aDateThatIsNotRfc3339IsRefused() = runBlocking {
        ApiStub { ApiStub.Reply(200, Fixtures.ACCOUNT.replace(Fixtures.MILLIS, "4 October 2026")) }.use { stub ->
            assertEquals(ApiFailure.Unavailable, stub.service(accessToken = "a").me().failureOrNull)
        }
    }

    /**
     * Before T106 this body was refused. zzThis D-2026-10-13-09 (RM-075, #54)
     * reverses that: a value contract 1 does not list decodes to the
     * generator's unknown case, and the rest of the body is kept.
     */
    @Test
    fun anEnumValueOutsideContract1DecodesToTheUnknownCase() = runBlocking {
        ApiStub { ApiStub.Reply(200, Fixtures.ACCOUNT.replace("\"dev\"", "\"github\"")) }.use { stub ->
            val account = (stub.service(accessToken = "a").me() as ApiResult.Ok).value
            assertEquals(listOf(Account.Providers.unknown_default_open_api), account.providers)
            assertEquals("account-1", account.id)
        }
    }
}
