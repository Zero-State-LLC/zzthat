package com.zer0state.zzthat.api

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * tasks.md T095, zzThis RM-075 (D-2026-10-10-12): the generated client is a
 * tolerant reader, so an additive, optional field such as the planned
 * Resolve `creator` (RM-112) does not break a shipped app. An unknown enum
 * value is not tolerated yet: issue #54.
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
}
