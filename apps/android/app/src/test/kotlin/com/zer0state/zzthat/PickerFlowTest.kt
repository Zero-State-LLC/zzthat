package com.zer0state.zzthat

import com.zer0state.zzthat.create.CreateModel
import com.zer0state.zzthat.create.MintedModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The suggested-word picker (zzThis RM-113, FR-017, tasks.md T099): the
 * server chooses the words, and the person keeps them or gets different
 * ones. Keep is local only. The app never sends a code string.
 */
class PickerFlowTest {
    private val code = """{"id":"code-1","canonical":"zz-copper-lantern-sky-zz","check_word":"sky","status":"active","record_id":"record-1",""" +
        """"scope":"free_public","rerolls_remaining":3,"created_at":"2026-10-04T00:00:00Z"}"""
    private val rerolled = code.replace("zz-copper-lantern-sky-zz", "zz-river-falcon-sky-zz").replace("\"rerolls_remaining\":3", "\"rerolls_remaining\":2")

    private val bodies = CopyOnWriteArrayList<String>()

    private fun stub() = Stub { operation, request ->
        bodies += request.body?.utf8().orEmpty()
        when (operation) {
            "mintCode" -> Stub.Reply(201, code)
            "rerollCode" -> Stub.Reply(200, rerolled)
            else -> Stub.Reply(500, Fixtures.FAILED)
        }
    }

    @Test
    fun keepHidesRerollWithNoCall(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service())
            val minted = MintedModel(CreateModel().apply { title = "Lost cat" }.mint(app)!!)
            assertFalse(minted.kept)
            assertFalse("the picker shows while re-rolls remain", minted.capReached)
            minted.keep()
            assertTrue(minted.kept)
            assertEquals("Keep makes no call", listOf("mintCode"), stub.operations)
            minted.reroll(app)
            assertEquals("kept words stay", "zz-copper-lantern-sky-zz", minted.code.canonical)
            assertEquals("a re-roll after Keep makes no call", listOf("mintCode"), stub.operations)
        }
    }

    /** SC-2: the mint behind the picker sends the title and body only, never a code string. */
    @Test
    fun mintAndRerollSendNoCodeString(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service())
            val minted = MintedModel(CreateModel().apply { title = "Lost cat" }.mint(app)!!)
            minted.reroll(app)
            assertEquals("zz-river-falcon-sky-zz", minted.code.canonical)
            minted.keep()
            assertEquals(listOf("mintCode", "rerollCode"), stub.operations)
            val mint = Json.parseToJsonElement(bodies[0]).jsonObject
            assertEquals(setOf("scope", "kind", "record"), mint.keys)
            assertEquals(setOf("title", "body"), mint.getValue("record").jsonObject.keys)
            for (body in bodies) {
                assertFalse(body, body.contains("canonical"))
                assertFalse(body, body.contains("zz-"))
            }
        }
    }
}
