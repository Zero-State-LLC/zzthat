package com.zer0state.zzthat

import com.zer0state.zzthat.api.ApiResult
import com.zer0state.zzthat.api.ResolveResponse
import com.zer0state.zzthat.api.generated.models.Code
import com.zer0state.zzthat.api.generated.models.OwnedCode
import com.zer0state.zzthat.api.generated.models.ReportRequest
import com.zer0state.zzthat.api.generated.models.Resolve
import com.zer0state.zzthat.api.generated.models.ResolveRecord
import com.zer0state.zzthat.api.generated.models.ResolveShare
import com.zer0state.zzthat.api.generated.models.TokenRequest
import com.zer0state.zzthat.app.AppModel
import com.zer0state.zzthat.codes.CodeDetailModel
import com.zer0state.zzthat.codes.CodesModel
import com.zer0state.zzthat.codes.EditRecordModel
import com.zer0state.zzthat.create.CreateModel
import com.zer0state.zzthat.create.MintedModel
import com.zer0state.zzthat.report.ReportModel
import com.zer0state.zzthat.report.ReportState
import com.zer0state.zzthat.resolve.ResolveLoader
import com.zer0state.zzthat.resolve.ResolvePhase
import com.zer0state.zzthat.resolve.ResolvedView
import com.zer0state.zzthat.storage.DraftStore
import com.zer0state.zzthat.storage.ResolveCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean

/** Resolve through the FR-041 cache, with the generated client and a fake clock (tasks.md T060 to T063). */
class ResolveFlowTest {
    private val code = "zz-kathy-lost-cat-zz"

    private fun stub(status: Int = 200, cacheControl: String = Fixtures.PUBLIC_CACHE_CONTROL, retryAfter: String = "1") = Stub { operation, _ ->
        if (operation != "resolveCode") return@Stub Stub.Reply(500, Fixtures.FAILED)
        when (status) {
            200 -> Stub.Reply(200, Fixtures.resolveJson(code), mapOf("Cache-Control" to cacheControl))
            404 -> Stub.Reply(404, Fixtures.NOT_FOUND)
            429 -> Stub.Reply(429, """{"error":"rate-limited"}""", mapOf("Retry-After" to retryAfter))
            else -> Stub.Reply(status, Fixtures.FAILED)
        }
    }

    @Test
    fun aPublicResolveIsKeptFor60SecondsOnline(): Unit = runBlocking {
        stub().use { stub ->
            val clock = FakeClock()
            val app = TestApp.make(stub.service(), clock = clock)
            val first = ResolveLoader.load(code, app) as ResolvePhase.Record
            assertFalse(first.offline)
            assertEquals("Lost cat", first.view.title)
            assertEquals(code, first.view.shareText)
            clock.advance(59)
            ResolveLoader.load(code, app)
            assertEquals("within 60 seconds the copy is used", listOf("resolveCode"), stub.operations)
            clock.advance(1)
            ResolveLoader.load(code, app)
            assertEquals("after 60 seconds it fetches again", listOf("resolveCode", "resolveCode"), stub.operations)
            assertEquals("the last canonical text is kept for a retry", code, app.lastCanonical.value())
        }
    }

    @Test
    fun offlineShowsTheCopyLabeledOfflineUntil360Seconds(): Unit = runBlocking {
        stub().use { stub ->
            val clock = FakeClock()
            val app = TestApp.make(stub.service(), clock = clock)
            ResolveLoader.load(code, app)
            app.connectivity.set(false)
            clock.advance(359)
            assertEquals(
                ResolvePhase.Record(ResolvedView(code, "Lost cat", "Answers on this page.", code), offline = true),
                ResolveLoader.load(code, app),
            )
            clock.advance(1)
            assertEquals(ResolvePhase.NotOnDevice, ResolveLoader.load(code, app))
            assertEquals(ResolvePhase.NotOnDevice, ResolveLoader.load("zz-copper-lantern-sky-zz", app))
            assertEquals("offline resolve waits; nothing is sent", 1, stub.operations.size)
        }
    }

    @Test
    fun aLostConnectionFallsBackToTheOfflineRules(): Unit = runBlocking {
        val failing = AtomicBoolean(false)
        stub().use { stub ->
            val clock = FakeClock()
            val connection = okhttp3.Interceptor { chain ->
                if (failing.get()) throw UnknownHostException("offline")
                chain.proceed(chain.request())
            }
            val app = TestApp.make(stub.service(interceptors = listOf(connection)), clock = clock)
            ResolveLoader.load(code, app)
            failing.set(true)
            clock.advance(120)
            val phase = ResolveLoader.load(code, app)
            assertTrue("$phase", phase is ResolvePhase.Record && phase.offline)
        }
    }

    @Test
    fun noStoreAndNotFoundAreNeverKept(): Unit = runBlocking {
        stub().use { stub ->
            val app = TestApp.make(stub.service())
            ResolveLoader.load(code, app)
            assertEquals(1, app.cache.count)
        }
        stub(cacheControl = "no-store").use { stub ->
            val app = TestApp.make(stub.service())
            ResolveLoader.load(code, app)
            assertEquals(0, app.cache.count)
        }
        stub(status = 404).use { stub ->
            val app = TestApp.make(stub.service())
            assertEquals(ResolvePhase.NotFound, ResolveLoader.load(code, app))
            assertEquals(0, app.cache.count)
        }
    }

    /** FR-033: one wait message that does not say whether the code exists. */
    @Test
    fun rateLimitedWaitsForRetryAfter(): Unit = runBlocking {
        stub(status = 429, retryAfter = "7").use { stub ->
            val app = TestApp.make(stub.service())
            assertEquals(ResolvePhase.Failure(R.string.resolve_rate_limited, 7), ResolveLoader.load(code, app))
        }
    }

    companion object {
        fun resolve(canonical: String) = Resolve(
            Resolve.View.`public`,
            canonical,
            ResolveRecord("t", "b", OffsetDateTime.of(2026, 10, 4, 0, 0, 0, 0, ZoneOffset.UTC)),
            ResolveShare(canonical, null),
        )
    }
}

/** Owner writes drop the cached resolve (FR-041), and offline writes are refused and never queued (FR-022, T061). */
class OwnerFlowTest {
    private suspend fun signedInDemo(online: Boolean = true): AppModel {
        val service = TestApp.demoService()
        service.tokens.signIn("demo", "demo")
        val app = TestApp.make(service, online = online)
        app.completeSignIn(TokenRequest.Provider.google)
        return app
    }

    private fun cached(canonical: String, app: AppModel) {
        app.cache.store(ResolveResponse(ResolveFlowTest.resolve(canonical), cacheable = true), canonical)
    }

    @Test
    fun rerollDropsTheOldWordsAndStopsAtTheCap(): Unit = runBlocking {
        val app = signedInDemo()
        val create = CreateModel().apply { title = "Lost cat" }
        val before = app.ownerRevision
        val minted = create.mint(app)!!
        assertTrue("My codes reads the list again after a mint", app.ownerRevision > before)
        val model = MintedModel(minted)
        cached(minted.canonical, app)
        model.reroll(app)
        assertNotEquals(minted.canonical, model.code.canonical)
        assertEquals(ResolveCache.Lookup.None, app.cache.lookup(minted.canonical, online = false))
        model.reroll(app)
        model.reroll(app)
        assertTrue(model.capReached)
        assertEquals(0, model.code.rerollsRemaining)
    }

    @Test
    fun revokeDropsTheEntryAndShowsRevoked(): Unit = runBlocking {
        val app = signedInDemo()
        val row = (app.service.myCodes() as ApiResult.Ok).value.codes.first { it.status == OwnedCode.Status.active }
        cached(row.canonical, app)
        val detail = CodeDetailModel(row.id, row)
        detail.load(app)
        detail.revoke(app)
        assertEquals(ResolveCache.Lookup.None, app.cache.lookup(row.canonical, online = false))
        assertEquals(OwnedCode.Status.revoked, detail.row?.status)
        assertEquals("T053", ResolvePhase.NotFound, ResolveLoader.load(row.canonical, app))
    }

    @Test
    fun aRecordVersionDropsTheEntry(): Unit = runBlocking {
        val app = signedInDemo()
        val row = (app.service.myCodes() as ApiResult.Ok).value.codes.first()
        cached(row.canonical, app)
        val edit = EditRecordModel(row.id, null)
        edit.loadOnce(app)
        assertEquals(EditRecordModel.Phase.Editing, edit.phase)
        edit.title = "New title"
        assertTrue(edit.save(app))
        assertEquals(ResolveCache.Lookup.None, app.cache.lookup(row.canonical, online = false))
    }

    @Test
    fun aBlankTitleIsNotSaved(): Unit = runBlocking {
        val app = signedInDemo()
        val row = (app.service.myCodes() as ApiResult.Ok).value.codes.first()
        val edit = EditRecordModel(row.id, row)
        edit.loadOnce(app)
        edit.title = "   "
        assertFalse(edit.save(app))
        assertEquals(EditRecordModel.Phase.Failure(R.string.create_title_required), edit.phase)
    }

    /** Auth step 9: `DELETE /v1/me`, then the tokens, the draft, and every cache. */
    @Test
    fun deletingTheAccountClearsEveryCacheAndTheDraft(): Unit = runBlocking {
        val app = signedInDemo()
        cached("zz-kathy-lost-cat-zz", app)
        cached("zz-copper-lantern-sky-zz", app)
        app.drafts.save("Lost cat", "")
        app.lastCanonical.set("zz-kathy-lost-cat-zz")
        app.photos.begin(byteArrayOf(1, 2, 3))
        assertTrue(app.deleteAccount() is ApiResult.Ok)
        assertEquals(0, app.cache.count)
        assertNull(app.drafts.draft())
        assertNull(app.lastCanonical.value())
        assertTrue(app.photos.storedFiles.isEmpty())
        assertFalse(app.isSignedIn)
        assertNull(app.provider)
        assertFalse(app.service.tokens.hasSession)
    }

    /** A failed deletion keeps the account, the draft, and the cache. */
    @Test
    fun aFailedDeletionKeepsEverything(): Unit = runBlocking {
        Stub { _, _ -> Stub.Reply(500, Fixtures.FAILED) }.use { stub ->
            val app = TestApp.make(stub.service())
            app.completeSignIn(TokenRequest.Provider.dev)
            cached("zz-kathy-lost-cat-zz", app)
            app.drafts.save("Lost cat", "")
            assertTrue(app.deleteAccount() is ApiResult.Err)
            assertEquals(1, app.cache.count)
            assertEquals(DraftStore.Draft("Lost cat", ""), app.drafts.draft())
            assertTrue(app.isSignedIn)
            assertTrue(app.service.tokens.hasSession)
        }
    }

    @Test
    fun offlineMintIsRefusedAndKeptAsADraft(): Unit = runBlocking {
        Stub { _, _ -> Stub.Reply(500, Fixtures.FAILED) }.use { stub ->
            val app = TestApp.make(stub.service(), online = false)
            val create = CreateModel().apply {
                title = "Lost cat"
                body = "Answers on this page."
            }
            assertNull(create.mint(app))
            assertEquals(CreateModel.Phase.Failure(R.string.create_offline), create.phase)
            assertEquals(DraftStore.Draft("Lost cat", "Answers on this page."), app.drafts.draft())
            assertTrue("nothing is queued or sent", stub.operations.isEmpty())
        }
    }

    @Test
    fun offlineRerollAndRevokeAreRefused(): Unit = runBlocking {
        Stub { _, _ -> Stub.Reply(500, Fixtures.FAILED) }.use { stub ->
            val app = TestApp.make(stub.service(), online = false)
            val date = OffsetDateTime.now(ZoneOffset.UTC)
            val code = Code("code-1", "zz-copper-sky-river-zz", "river", Code.Status.active, "record-1", Code.Scope.free_public, 3, date)
            val minted = MintedModel(code)
            minted.reroll(app)
            assertEquals(MintedModel.Phase.Failure(R.string.error_no_connection), minted.phase)
            val row = OwnedCode("code-1", code.canonical, "river", OwnedCode.Status.active, "record-1", OwnedCode.Scope.free_public, 3, date, "t")
            val detail = CodeDetailModel(row.id, row)
            detail.revoke(app)
            assertEquals(CodeDetailModel.RevokePhase.Failure(R.string.error_no_connection), detail.revokePhase)
            val edit = EditRecordModel(row.id, row).apply { title = "t" }
            assertFalse(edit.save(app))
            assertEquals(EditRecordModel.Phase.Failure(R.string.error_no_connection), edit.phase)
            assertTrue(stub.operations.isEmpty())
        }
    }

    /** FR-018 and SC-8: a mint answered `scope-unavailable` is Unavailable, and the fields stay. */
    @Test
    fun scopeUnavailableMakesCreateUnavailable(): Unit = runBlocking {
        Stub { _, _ -> Stub.Reply(403, """{"error":"scope-unavailable"}""") }.use { stub ->
            val app = TestApp.make(stub.service())
            val create = CreateModel().apply { title = "Lost cat" }
            assertNull(create.mint(app))
            assertTrue(create.mintUnavailable)
            assertEquals("the fields stay", "Lost cat", create.title)
        }
    }

    /** T030: a 400 other than contract-version keeps the fields. */
    @Test
    fun aMalformedMintSavesNothingAndKeepsTheFields(): Unit = runBlocking {
        Stub { _, _ -> Stub.Reply(400, """{"error":"malformed"}""") }.use { stub ->
            val app = TestApp.make(stub.service())
            val create = CreateModel().apply {
                title = "Lost cat"
                body = "Answers"
            }
            assertNull(create.mint(app))
            assertEquals(CreateModel.Phase.Failure(R.string.error_nothing_saved), create.phase)
            assertEquals("Lost cat", create.title)
            assertEquals("Answers", create.body)
        }
    }

    /** FR-033: after a 429, Send stays off until `Retry-After` has passed, and the one wait sentence does not say whether the code exists. */
    @Test
    fun aRateLimitedReportWaitsForRetryAfter(): Unit = runBlocking {
        Stub { _, _ -> Stub.Reply(429, """{"error":"rate-limited"}""", mapOf("Retry-After" to "1")) }.use { stub ->
            val app = TestApp.make(stub.service(signedIn = false))
            val model = ReportModel("zz-kathy-lost-cat-zz")
            model.state = model.state.copy(reason = ReportRequest.Reason.spam)
            val sending = async(Dispatchers.Default) { model.send(app) }
            delay(400)
            assertEquals(ReportState.Phase.Failure(R.string.resolve_rate_limited), model.state.phase)
            assertTrue(model.state.waiting)
            assertFalse(model.state.canSend)
            sending.await()
            assertFalse(model.state.waiting)
            assertTrue(model.state.canSend)
            assertEquals(listOf("createReport"), stub.operations)
        }
    }

    @Test
    fun aReportIsRefusedOffline(): Unit = runBlocking {
        Stub { _, _ -> Stub.Reply(202, """{"id":"r-1"}""") }.use { stub ->
            val app = TestApp.make(stub.service(signedIn = false), online = false)
            val model = ReportModel("zz-kathy-lost-cat-zz")
            model.state = model.state.copy(reason = ReportRequest.Reason.other)
            model.send(app)
            assertTrue(model.state.offline)
            assertTrue(stub.operations.isEmpty())
        }
    }

    @Test
    fun myCodesOfflineKeepsNoList(): Unit = runBlocking {
        val app = signedInDemo(online = false)
        val codes = CodesModel()
        codes.reload(app)
        assertTrue(codes.rows.isEmpty())
        assertEquals(CodesModel.Phase.Failure(R.string.error_no_connection), codes.phase)
    }

    @Test
    fun codeDetailFindsTheRowByIdWhenNotGivenOne(): Unit = runBlocking {
        val app = signedInDemo()
        val detail = CodeDetailModel("demo-code-2", null)
        detail.load(app)
        assertEquals("zz-river-falcon-sky-zz", detail.row?.canonical)
        assertEquals("Parcel (demo)", detail.record?.title)
        val missing = CodeDetailModel("no-such-code", null)
        missing.load(app)
        assertEquals(CodeDetailModel.Phase.NotFound, missing.phase)
    }

    /** A 401 the refresh cannot fix on a write asks the person to sign in again, and ends the session. */
    @Test
    fun anUnfixable401OnAWriteEndsTheSession(): Unit = runBlocking {
        Stub { _, _ -> Stub.Reply(401, """{"error":"unauthorized"}""") }.use { stub ->
            val app = TestApp.make(stub.service())
            app.completeSignIn(TokenRequest.Provider.dev)
            val create = CreateModel().apply { title = "Lost cat" }
            assertNull(create.mint(app))
            assertEquals(CreateModel.Phase.Failure(R.string.common_sign_in_needed), create.phase)
            assertFalse(app.isSignedIn)
            assertFalse(app.service.tokens.hasSession)
        }
    }
}
