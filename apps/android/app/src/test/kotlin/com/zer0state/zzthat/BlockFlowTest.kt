package com.zer0state.zzthat

import com.zer0state.zzthat.api.ApiResult
import com.zer0state.zzthat.api.generated.models.ReportRequest
import com.zer0state.zzthat.api.generated.models.TokenRequest
import com.zer0state.zzthat.report.ReportModel
import com.zer0state.zzthat.report.ReportState
import com.zer0state.zzthat.resolve.ResolveModel
import com.zer0state.zzthat.resolve.ResolvePhase
import com.zer0state.zzthat.resolve.ResolvedView
import com.zer0state.zzthat.storage.ResolveCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Block this creator (FR-035, zzThis RM-112, tasks.md T096): a second list on
 * the device only, keyed by the opaque Resolve `creator`. A code whose creator
 * is blocked still resolves, and shows the blocked state without its title or
 * body. Blocking, unblocking, and the Account reset make no server call.
 */
class BlockFlowTest {
    private val code = "zz-kathy-lost-cat-zz"
    private val sameCreator = "zz-copper-lantern-sky-zz"
    private val otherCreator = "zz-b2-4-zz"
    private val creator = "creator-a"

    private fun creatorOf(canonical: String): String? = when (canonical) {
        code, sameCreator -> creator
        otherCreator -> "creator-b"
        else -> null
    }

    private fun stub(creators: (String) -> String? = ::creatorOf) = Stub { operation, request ->
        when (operation) {
            "resolveCode" -> {
                val canonical = request.url.encodedPath.removePrefix("/v1/resolve/")
                Stub.Reply(200, Fixtures.resolveJson(canonical, creator = creators(canonical)), mapOf("Cache-Control" to Fixtures.PUBLIC_CACHE_CONTROL))
            }
            "createReport" -> Stub.Reply(202, """{"id":"r-1"}""")
            "deleteMe" -> Stub.Reply(204)
            else -> Stub.Reply(500, Fixtures.FAILED)
        }
    }

    private fun record(canonical: String, creator: String? = this.creator) =
        ResolvePhase.Record(ResolvedView(canonical, "Lost cat", "Answers on this page.", canonical, creator), offline = false)

    /** The blocked state is a phase with no title and no body, so the screen has none to draw. */
    @Test
    fun blockingThenResolvingAgainShowsTheBlockedState(): Unit = runBlocking {
        stub().use { stub ->
            val clock = FakeClock()
            val app = TestApp.make(stub.service(signedIn = false), clock = clock)
            val model = ResolveModel(code)
            model.load(app)
            assertEquals(record(code), model.phase)
            assertTrue("Block this creator is offered", (model.phase as ResolvePhase.Record).canBlock)
            model.block(app)
            assertEquals("the title and body leave the screen at once", ResolvePhase.Blocked, model.phase)
            assertEquals("blocking makes no call", listOf("resolveCode"), stub.operations)
            // Past the FR-041 60 seconds, so resolve fetches again: the normal path, scanned or typed.
            clock.advance(61)
            val again = ResolveModel(code)
            again.load(app)
            assertEquals(ResolvePhase.Blocked, again.phase)
            assertEquals("a blocked creator's code still resolves", listOf("resolveCode", "resolveCode"), stub.operations)
        }
    }

    @Test
    fun anotherCodeWithTheSameCreatorIsBlocked(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service(signedIn = false))
            val model = ResolveModel(code)
            model.load(app)
            model.block(app)
            val same = ResolveModel(sameCreator)
            same.load(app)
            assertEquals(ResolvePhase.Blocked, same.phase)
            val other = ResolveModel(otherCreator)
            other.load(app)
            assertEquals("another creator's code shows", record(otherCreator, "creator-b"), other.phase)
        }
    }

    @Test
    fun unblockingShowsTheRecordAgain(): Unit = runBlocking {
        stub().use { stub ->
            val clock = FakeClock()
            val app = TestApp.make(stub.service(signedIn = false), clock = clock)
            val model = ResolveModel(code)
            model.load(app)
            model.block(app)
            model.unblock(app)
            assertEquals("the record shows again at once", record(code), model.phase)
            assertFalse(app.hidden.isBlocked(creator))
            assertEquals("unblocking makes no call", listOf("resolveCode"), stub.operations)
            clock.advance(61)
            val again = ResolveModel(sameCreator)
            again.load(app)
            assertEquals(record(sameCreator), again.phase)
        }
    }

    /** A private record, or an older server, sends no creator: there is nothing to block. */
    @Test
    fun aRecordWithNoCreatorOffersNoBlock(): Unit = runBlocking {
        stub(creators = { null }).use { stub ->
            val app = TestApp.make(stub.service(signedIn = false))
            val model = ResolveModel(code)
            model.load(app)
            val shown = model.phase as ResolvePhase.Record
            assertNull(shown.view.creator)
            assertFalse("no Block this creator", shown.canBlock)
            assertNull(model.creator)
            model.block(app)
            assertEquals("Block does nothing without a creator", record(code, creator = null), model.phase)
            val report = ReportModel(code, model.creator)
            report.loadHidden(app)
            assertNull("the Report sheet offers no Block either", report.state.creator)
            assertFalse(report.state.blocked)
            report.block(app)
            assertFalse(report.state.blocked)
        }
    }

    @Test
    fun blockedTakesPrecedenceOverHidden(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service(signedIn = false))
            val model = ResolveModel(code)
            model.load(app)
            model.hide(app)
            assertEquals(ResolvePhase.Hidden, model.phase)
            model.block(app)
            assertEquals(ResolvePhase.Blocked, model.phase)
            model.unblock(app)
            assertEquals("the code is still hidden", ResolvePhase.Hidden, model.phase)
            model.unhide(app)
            assertEquals(record(code), model.phase)
        }
    }

    /** FR-041: the cached copy carries the creator, so a fresh or offline copy of a blocked creator's code stays blocked. */
    @Test
    fun aCachedOrOfflineResolveOfABlockedCreatorsCodeIsBlocked(): Unit = runBlocking {
        stub().use { stub ->
            val clock = FakeClock()
            val app = TestApp.make(stub.service(signedIn = false), clock = clock)
            ResolveModel(code).load(app)
            assertEquals(creator, (app.cache.lookup(code, online = true) as ResolveCache.Lookup.Fresh).entry.creator)
            app.block(creator)
            val cached = ResolveModel(code)
            cached.load(app)
            assertEquals("the fresh copy shows blocked", ResolvePhase.Blocked, cached.phase)
            assertEquals("the copy answered, with no second fetch", listOf("resolveCode"), stub.operations)
            clock.advance(120)
            app.connectivity.set(false)
            val offline = ResolveModel(code)
            offline.load(app)
            assertEquals("the offline copy shows blocked", ResolvePhase.Blocked, offline.phase)
            app.unblock(creator)
            offline.sync(app)
            val shown = offline.phase as ResolvePhase.Record
            assertTrue("unblocked, the copy shows labeled offline", shown.offline)
            assertEquals(creator, shown.view.creator)
            assertEquals(listOf("resolveCode"), stub.operations)
        }
    }

    /** The Report sheet's Block blocks the creator, and an open Resolve shows it on return. */
    @Test
    fun blockingFromTheReportSheetBlocksTheCreator(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service(signedIn = false))
            val resolve = ResolveModel(code)
            resolve.load(app)
            val report = ReportModel(code, resolve.creator)
            report.loadHidden(app)
            assertEquals(creator, report.state.creator)
            assertFalse(report.state.blocked)
            report.state = report.state.copy(reason = ReportRequest.Reason.spam)
            report.send(app)
            assertEquals(ReportState.Phase.Sent, report.state.phase)
            report.block(app)
            assertTrue(report.state.blocked)
            assertTrue(app.hidden.isBlocked(creator))
            assertFalse("blocking does not hide the code itself", app.hidden.isHidden(code))
            resolve.sync(app)
            assertEquals(ResolvePhase.Blocked, resolve.phase)
            val again = ReportModel(sameCreator, creator)
            again.loadHidden(app)
            assertTrue("a later report of the same creator says blocked", again.state.blocked)
            assertEquals("blocking from the sheet makes no call", listOf("resolveCode", "createReport"), stub.operations)
        }
    }

    /** The Account reset, Unhide everything, clears blocked creators too. */
    @Test
    fun unhideAllClearsBlockedCreators(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service(signedIn = false))
            val model = ResolveModel(code)
            model.load(app)
            model.block(app)
            app.block("creator-b")
            app.hide(otherCreator)
            app.unhideAll()
            assertFalse(app.hidden.isBlocked(creator))
            assertFalse(app.hidden.isBlocked("creator-b"))
            assertFalse(app.hidden.isHidden(otherCreator))
            model.sync(app)
            assertEquals("an open Resolve shows the record again", record(code), model.phase)
            assertEquals("the reset makes no call", listOf("resolveCode"), stub.operations)
        }
    }

    /** Auth step 9 and FR-035: account deletion clears blocked creators too. */
    @Test
    fun deletingTheAccountClearsBlockedCreators(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service())
            app.completeSignIn(TokenRequest.Provider.dev)
            app.block(creator)
            app.block("creator-b")
            assertTrue(app.deleteAccount() is ApiResult.Ok)
            assertFalse(app.hidden.isBlocked(creator))
            assertFalse(app.hidden.isBlocked("creator-b"))
            assertEquals("only the deletion is sent", listOf("deleteMe"), stub.operations)
        }
    }

    @Test
    fun aFailedDeletionKeepsBlockedCreators(): Unit = runBlocking {
        Stub { _, _ -> Stub.Reply(500, Fixtures.FAILED) }.use { stub ->
            val app = TestApp.make(stub.service())
            app.completeSignIn(TokenRequest.Provider.dev)
            app.block(creator)
            assertTrue(app.deleteAccount() is ApiResult.Err)
            assertTrue(app.hidden.isBlocked(creator))
        }
    }

    /** Block, unblock, and the reset touch only the device, signed in or out. */
    @Test
    fun blockUnblockAndUnhideAllMakeNoCall(): Unit = runBlocking {
        for (signedIn in listOf(false, true)) {
            stub().use { stub ->
                val app = TestApp.make(stub.service(signedIn = signedIn))
                app.block(creator)
                app.block("creator-b")
                app.unblock(creator)
                ReportModel(code, "creator-c").block(app)
                assertTrue(app.hidden.isBlocked("creator-c"))
                app.unhideAll()
                assertFalse(app.hidden.isBlocked("creator-b"))
                assertEquals("signed in: $signedIn", emptyList<String>(), stub.operations)
            }
        }
    }
}
