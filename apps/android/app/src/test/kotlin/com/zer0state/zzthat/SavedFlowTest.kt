package com.zer0state.zzthat

import com.zer0state.zzthat.api.ApiResult
import com.zer0state.zzthat.api.generated.models.TokenRequest
import com.zer0state.zzthat.resolve.ResolveModel
import com.zer0state.zzthat.resolve.ResolvePhase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saved (FR-050, zzThis RM-115, tasks.md T098): canonical codes kept on the
 * device only, newest first. Saving, removing, and clearing make no server
 * call. Account deletion and the Account screen's clear empty the list.
 */
class SavedFlowTest {
    private val code = "zz-kathy-lost-cat-zz"
    private val other = "zz-copper-lantern-sky-zz"
    private val third = "zz-b2-4-zz"

    private fun stub() = Stub { operation, request ->
        when (operation) {
            "resolveCode" -> {
                val canonical = request.url.encodedPath.removePrefix("/v1/resolve/")
                Stub.Reply(200, Fixtures.resolveJson(canonical), mapOf("Cache-Control" to Fixtures.PUBLIC_CACHE_CONTROL))
            }
            "deleteMe" -> Stub.Reply(204)
            else -> Stub.Reply(500, Fixtures.FAILED)
        }
    }

    @Test
    fun savingFromResolveKeepsTheCanonicalCode(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service(signedIn = false))
            val model = ResolveModel(code)
            model.load(app)
            assertTrue(model.phase is ResolvePhase.Record)
            assertFalse(model.saved)
            val before = app.savedRevision
            model.save(app)
            assertTrue("Resolve offers Remove from Saved", model.saved)
            assertTrue("Saved reads the list again", app.savedRevision > before)
            assertEquals(listOf(code), app.saved.list())
            val again = ResolveModel(code)
            again.load(app)
            assertTrue("a later resolve knows the code is saved", again.saved)
            again.unsave(app)
            assertFalse(again.saved)
            assertEquals(emptyList<String>(), app.saved.list())
            // The second resolve is the FR-041 copy, so the one call is the first resolve.
            assertEquals("saving and removing make no call", listOf("resolveCode"), stub.operations)
        }
    }

    @Test
    fun theListIsNewestFirst(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service(signedIn = false))
            app.save(code)
            app.save(other)
            app.save(third)
            assertEquals(listOf(third, other, code), app.saved.list())
            app.unsave(other)
            assertEquals(listOf(third, code), app.saved.list())
            assertTrue(app.saved.isSaved(code))
            assertFalse(app.saved.isSaved(other))
        }
    }

    /** The Account screen's Clear saved codes empties Saved, and an open Resolve shows Save again. */
    @Test
    fun clearingSavedEmptiesTheList(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service(signedIn = false))
            val model = ResolveModel(code)
            model.load(app)
            model.save(app)
            app.save(other)
            app.clearSaved()
            assertEquals(emptyList<String>(), app.saved.list())
            model.syncSaved(app)
            assertFalse(model.saved)
            assertEquals("clearing makes no call", listOf("resolveCode"), stub.operations)
        }
    }

    /** Auth step 9 and FR-050: account deletion clears Saved. */
    @Test
    fun deletingTheAccountEmptiesSaved(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service())
            app.completeSignIn(TokenRequest.Provider.dev)
            app.save(code)
            app.save(other)
            val before = app.savedRevision
            assertTrue(app.deleteAccount() is ApiResult.Ok)
            assertEquals(emptyList<String>(), app.saved.list())
            assertTrue("Saved reads the list again", app.savedRevision > before)
            assertEquals("only the deletion is sent", listOf("deleteMe"), stub.operations)
        }
    }

    @Test
    fun aFailedDeletionKeepsSaved(): Unit = runBlocking {
        Stub { _, _ -> Stub.Reply(500, Fixtures.FAILED) }.use { stub ->
            val app = TestApp.make(stub.service())
            app.completeSignIn(TokenRequest.Provider.dev)
            app.save(code)
            assertTrue(app.deleteAccount() is ApiResult.Err)
            assertEquals(listOf(code), app.saved.list())
        }
    }

    /** Save, remove, and clear touch only the device, signed in or out. */
    @Test
    fun saveUnsaveAndClearMakeNoCall(): Unit = runBlocking {
        for (signedIn in listOf(false, true)) {
            stub().use { stub ->
                val app = TestApp.make(stub.service(signedIn = signedIn))
                app.save(code)
                app.save(other)
                app.unsave(code)
                ResolveModel(third).save(app)
                assertEquals(listOf(third, other), app.saved.list())
                app.clearSaved()
                assertEquals(emptyList<String>(), app.saved.list())
                assertEquals("signed in: $signedIn", emptyList<String>(), stub.operations)
            }
        }
    }
}
