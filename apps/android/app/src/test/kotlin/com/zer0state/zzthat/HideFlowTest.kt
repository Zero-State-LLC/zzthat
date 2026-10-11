package com.zer0state.zzthat

import com.zer0state.zzthat.api.ApiResult
import com.zer0state.zzthat.api.generated.models.ReportRequest
import com.zer0state.zzthat.api.generated.models.TokenRequest
import com.zer0state.zzthat.report.ReportModel
import com.zer0state.zzthat.report.ReportState
import com.zer0state.zzthat.resolve.ResolveModel
import com.zer0state.zzthat.resolve.ResolvePhase
import com.zer0state.zzthat.resolve.ResolvedView
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hide this code (FR-035, zzThis RM-101): a list on the device only. A hidden
 * code still resolves, and shows the hidden state without its title or body.
 * Hiding, unhiding, and the Account reset make no server call.
 */
class HideFlowTest {
    private val code = "zz-kathy-lost-cat-zz"
    private val other = "zz-copper-lantern-sky-zz"
    private val record = ResolvePhase.Record(ResolvedView(code, "Lost cat", "Answers on this page.", code), offline = false)

    private fun stub(status: Int = 200) = Stub { operation, _ ->
        when {
            operation == "resolveCode" && status == 200 ->
                Stub.Reply(200, Fixtures.resolveJson(code), mapOf("Cache-Control" to Fixtures.PUBLIC_CACHE_CONTROL))
            operation == "resolveCode" && status == 404 -> Stub.Reply(404, Fixtures.NOT_FOUND)
            operation == "createReport" -> Stub.Reply(202, """{"id":"r-1"}""")
            operation == "deleteMe" -> Stub.Reply(204)
            else -> Stub.Reply(500, Fixtures.FAILED)
        }
    }

    /** The hidden state is a phase with no title and no body, so the screen has none to draw. */
    @Test
    fun hidingThenResolvingAgainShowsTheHiddenState(): Unit = runBlocking {
        stub().use { stub ->
            val clock = FakeClock()
            val app = TestApp.make(stub.service(signedIn = false), clock = clock)
            val model = ResolveModel(code)
            model.load(app)
            assertEquals(record, model.phase)
            model.hide(app)
            assertEquals("the title and body leave the screen at once", ResolvePhase.Hidden, model.phase)
            assertEquals("hiding makes no call", listOf("resolveCode"), stub.operations)
            // Past the FR-041 60 seconds, so resolve fetches again: the normal path, scanned or typed.
            clock.advance(61)
            val again = ResolveModel(code)
            again.load(app)
            assertEquals(ResolvePhase.Hidden, again.phase)
            assertEquals("a hidden code still resolves", listOf("resolveCode", "resolveCode"), stub.operations)
            assertEquals("the last canonical text is kept as usual", code, app.lastCanonical.value())
        }
    }

    @Test
    fun unhidingShowsTheRecordAgain(): Unit = runBlocking {
        stub().use { stub ->
            val clock = FakeClock()
            val app = TestApp.make(stub.service(signedIn = false), clock = clock)
            val model = ResolveModel(code)
            model.load(app)
            model.hide(app)
            model.unhide(app)
            assertEquals("the record shows again at once", record, model.phase)
            assertEquals("unhiding makes no call", listOf("resolveCode"), stub.operations)
            assertFalse(app.hidden.isHidden(code))
            clock.advance(61)
            val again = ResolveModel(code)
            again.load(app)
            assertEquals(record, again.phase)
        }
    }

    /** Only a record is hidden: not-found stays the one not-found state (FR-010). */
    @Test
    fun aHiddenCodeThatIsNotFoundStaysNotFound(): Unit = runBlocking {
        stub(status = 404).use { stub ->
            val app = TestApp.make(stub.service(signedIn = false))
            app.hide(code)
            val model = ResolveModel(code)
            model.load(app)
            assertEquals(ResolvePhase.NotFound, model.phase)
            model.sync(app)
            assertEquals(ResolvePhase.NotFound, model.phase)
        }
    }

    /** The Report sheet's Hide marks the code hidden, and an open Resolve shows it on return. */
    @Test
    fun hidingFromTheReportSheetHidesTheCode(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service(signedIn = false))
            val resolve = ResolveModel(code)
            resolve.load(app)
            val report = ReportModel(code)
            report.loadHidden(app)
            assertFalse(report.state.hidden)
            report.state = report.state.copy(reason = ReportRequest.Reason.spam)
            report.send(app)
            assertEquals(ReportState.Phase.Sent, report.state.phase)
            report.hide(app)
            assertTrue(report.state.hidden)
            assertTrue(app.hidden.isHidden(code))
            resolve.sync(app)
            assertEquals(ResolvePhase.Hidden, resolve.phase)
            assertEquals("hiding from the sheet makes no call", listOf("resolveCode", "createReport"), stub.operations)
        }
    }

    @Test
    fun unhideAllEmptiesTheList(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service(signedIn = false))
            val model = ResolveModel(code)
            model.load(app)
            model.hide(app)
            app.hide(other)
            app.unhideAll()
            assertFalse(app.hidden.isHidden(code))
            assertFalse(app.hidden.isHidden(other))
            model.sync(app)
            assertEquals("an open Resolve shows the record again", record, model.phase)
            assertEquals("the reset makes no call", listOf("resolveCode"), stub.operations)
        }
    }

    /** Hide, unhide, and the reset touch only the device, signed in or out. */
    @Test
    fun hideUnhideAndUnhideAllMakeNoCall(): Unit = runBlocking {
        for (signedIn in listOf(false, true)) {
            stub().use { stub ->
                val app = TestApp.make(stub.service(signedIn = signedIn))
                app.hide(code)
                app.hide(other)
                app.unhide(code)
                ReportModel(other).hide(app)
                app.unhideAll()
                assertFalse(app.hidden.isHidden(other))
                assertEquals("signed in: $signedIn", emptyList<String>(), stub.operations)
            }
        }
    }

    /** Auth step 9 and FR-035: account deletion clears the hidden list too. */
    @Test
    fun deletingTheAccountEmptiesTheList(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service())
            app.completeSignIn(TokenRequest.Provider.dev)
            app.hide(code)
            app.hide(other)
            assertTrue(app.deleteAccount() is ApiResult.Ok)
            assertFalse(app.hidden.isHidden(code))
            assertFalse(app.hidden.isHidden(other))
            assertEquals("only the deletion is sent", listOf("deleteMe"), stub.operations)
        }
    }

    @Test
    fun aFailedDeletionKeepsTheList(): Unit = runBlocking {
        Stub { _, _ -> Stub.Reply(500, Fixtures.FAILED) }.use { stub ->
            val app = TestApp.make(stub.service())
            app.completeSignIn(TokenRequest.Provider.dev)
            app.hide(code)
            assertTrue(app.deleteAccount() is ApiResult.Err)
            assertTrue(app.hidden.isHidden(code))
        }
    }
}
