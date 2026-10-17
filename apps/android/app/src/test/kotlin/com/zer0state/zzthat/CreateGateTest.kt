package com.zer0state.zzthat

import com.zer0state.zzthat.api.ApiResult
import com.zer0state.zzthat.api.DemoApi
import com.zer0state.zzthat.api.generated.models.Code
import com.zer0state.zzthat.api.generated.models.OwnedCode
import com.zer0state.zzthat.api.generated.models.TokenRequest
import com.zer0state.zzthat.app.AppModel
import com.zer0state.zzthat.app.BuildSettings
import com.zer0state.zzthat.app.res
import com.zer0state.zzthat.codes.CodeDetailModel
import com.zer0state.zzthat.codes.CodeDetailScreenState
import com.zer0state.zzthat.create.CreateModel
import com.zer0state.zzthat.create.CreateWait
import com.zer0state.zzthat.create.MintedModel
import com.zer0state.zzthat.resolve.ResolveLoader
import com.zer0state.zzthat.resolve.ResolvePhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/**
 * The Terms step before the first create on a device (design/UX.md, Terms;
 * zzThis D-2026-10-13-04; tasks.md T104). Client-only: the step sends
 * nothing, and the accepted `terms.version` stays on the device.
 */
class TermsStepTest {
    private suspend fun signedIn(acceptedTermsVersion: String?): AppModel {
        val service = TestApp.demoService()
        service.tokens.signIn("demo", "demo")
        val app = TestApp.make(service, acceptedTermsVersion = acceptedTermsVersion)
        app.completeSignIn(TokenRequest.Provider.google)
        return app
    }

    private fun create(title: String = "Lost cat") = CreateModel().apply { this.title = title }

    @Test
    fun theCurrentVersionIsTheCatalogValue() {
        assertTrue(Catalog.TERMS_VERSION.isNotBlank())
    }

    @Test
    fun theStepShowsBeforeTheFirstCreateAndSendsNothing(): Unit = runBlocking {
        Stub { _, _ -> Stub.Reply(500, Fixtures.FAILED) }.use { stub ->
            val app = TestApp.make(stub.service(), acceptedTermsVersion = null)
            assertTrue(app.needsTerms)
            val model = create()
            assertFalse(model.termsDone(app))
            assertNull(model.mint(app))
            assertTrue("Create does not continue before the step", stub.operations.isEmpty())
            assertNull(app.terms.acceptedVersion())
        }
    }

    @Test
    fun createWaitsForBothConfirmations(): Unit = runBlocking {
        val app = signedIn(acceptedTermsVersion = null)
        val model = create()
        model.ageConfirmed = true
        assertFalse(model.termsDone(app))
        assertNull(model.mint(app))
        model.ageConfirmed = false
        model.termsAccepted = true
        assertFalse(model.termsDone(app))
        assertNull(model.mint(app))
        assertTrue("nothing is stored before both are confirmed", app.needsTerms)
        model.ageConfirmed = true
        assertTrue(model.termsDone(app))
        assertNotNull(model.mint(app))
        assertFalse(app.needsTerms)
        assertEquals(Catalog.TERMS_VERSION, app.terms.acceptedVersion())
    }

    @Test
    fun theStepDoesNotShowAgainAfterAcceptance(): Unit = runBlocking {
        val app = signedIn(acceptedTermsVersion = null)
        val first = create().apply {
            ageConfirmed = true
            termsAccepted = true
        }
        assertNotNull(first.mint(app))
        val second = create("Parcel")
        assertTrue(second.termsDone(app))
        assertNotNull("the next create needs no confirmation", second.mint(app))
    }

    @Test
    fun aNewTermsVersionShowsTheStepAgain(): Unit = runBlocking {
        val app = signedIn(acceptedTermsVersion = "draft-2026-01-01")
        assertTrue(app.needsTerms)
        val model = create()
        assertNull(model.mint(app))
        model.ageConfirmed = true
        model.termsAccepted = true
        assertNotNull(model.mint(app))
        assertEquals(Catalog.TERMS_VERSION, app.terms.acceptedVersion())
        assertFalse("the step starts unchecked if it shows again", model.ageConfirmed || model.termsAccepted)
    }

    @Test
    fun accountDeletionClearsTheAcceptedVersion(): Unit = runBlocking {
        val app = signedIn(acceptedTermsVersion = Catalog.TERMS_VERSION)
        assertFalse(app.needsTerms)
        assertTrue(app.deleteAccount() is ApiResult.Ok)
        assertTrue(app.needsTerms)
        assertNull(app.terms.acceptedVersion())
    }

    @Test
    fun signingOutKeepsTheAcceptedVersion(): Unit = runBlocking {
        val app = signedIn(acceptedTermsVersion = Catalog.TERMS_VERSION)
        app.signOut()
        assertFalse(app.needsTerms)
    }

    @Test
    fun scanningAndTypingNeverAskForTheStep(): Unit = runBlocking {
        val app = TestApp.make(TestApp.demoService(), acceptedTermsVersion = null)
        // Scan and Type both end in this resolve (US2, US2a).
        assertTrue("a signed-out code resolves without the Terms step", ResolveLoader.load("zz-kathy-lost-cat-zz", app) is ResolvePhase.Record)
        assertTrue("the step is still pending, and resolve did not need it", app.needsTerms)
        assertNull("resolving neither asks nor stores", app.terms.acceptedVersion())
    }

    @Test
    fun theTermsLinkIsABuildSettingAndHiddenWithoutOne() {
        val url = "https://zzthat.com/terms/"
        assertEquals(url, TestApp.make(TestApp.demoService(), termsUrl = url).config.termsUrl)
        assertNull(TestApp.make(TestApp.demoService()).config.termsUrl)
    }

    /** RM-111: the community links are the zzThis site footer's addresses. */
    @Test
    fun theCommunityLinksAreTheFooterAddresses() {
        assertEquals("https://discord.gg/hwXvVKYzn4", BuildSettings.DISCORD_URL)
        assertEquals("https://www.instagram.com/zzthatcom/", BuildSettings.INSTAGRAM_URL)
    }
}

/**
 * A 429 on mint or re-roll (FR-033, T014; design/UX.md, Create: the daily
 * limit): Create waits for `Retry-After` and keeps the title and body.
 */
class CreateRateLimitTest {
    private fun limited(operation: String, retryAfter: String) = Stub { op, _ ->
        if (op == operation) Stub.Reply(429, """{"error":"rate-limited"}""", mapOf("Retry-After" to retryAfter)) else Stub.Reply(500, Fixtures.FAILED)
    }

    @Test
    fun theDailyCapIsAWaitLongerThanAnHour() {
        assertEquals(R.string.resolve_rate_limited, CreateWait.message(1))
        assertEquals(R.string.resolve_rate_limited, CreateWait.message(CreateWait.DAILY_LIMIT_SECONDS))
        assertEquals(R.string.create_daily_limit, CreateWait.message(CreateWait.DAILY_LIMIT_SECONDS + 1))
        assertEquals(R.string.create_daily_limit, CreateWait.message(20 * 3600))
    }

    @Test
    fun aDailyCapOnMintWaitsAndKeepsTheFields(): Unit = runBlocking {
        limited("mintCode", "36000").use { stub ->
            val app = TestApp.make(stub.service())
            val model = CreateModel().apply {
                title = "Lost cat"
                body = "Answers"
            }
            assertNull(model.mint(app))
            assertEquals(CreateModel.Phase.Failure(R.string.create_daily_limit), model.phase)
            assertTrue(model.waiting)
            assertEquals("Lost cat", model.title)
            assertEquals("Answers", model.body)
            assertNull(model.mint(app))
            assertEquals("Get a code stays off until Retry-After ends", listOf("mintCode"), stub.operations.toList())
            model.endWait()
            assertFalse(model.waiting)
            model.mint(app)
            assertEquals(listOf("mintCode", "mintCode"), stub.operations.toList())
        }
    }

    @Test
    fun aShortRateLimitOnMintShowsTheWaitSentence(): Unit = runBlocking {
        limited("mintCode", "30").use { stub ->
            val app = TestApp.make(stub.service())
            val model = CreateModel().apply { title = "Lost cat" }
            assertNull(model.mint(app))
            assertEquals(CreateModel.Phase.Failure(R.string.resolve_rate_limited), model.phase)
            assertTrue(model.waiting)
        }
    }

    @Test
    fun aRateLimitedRerollWaitsAndKeepsTheWords(): Unit = runBlocking {
        limited("rerollCode", "36000").use { stub ->
            val app = TestApp.make(stub.service())
            val code = Code(
                "code-1", "zz-copper-sky-river-zz", "river", Code.Status.active, "record-1", Code.Scope.free_public, 5,
                OffsetDateTime.parse("2026-10-04T00:00:00Z"),
            )
            val minted = MintedModel(code)
            minted.reroll(app)
            assertEquals(MintedModel.Phase.Failure(R.string.create_daily_limit), minted.phase)
            assertTrue(minted.waiting)
            assertEquals(code.canonical, minted.code.canonical)
            assertFalse(minted.capReached)
            minted.reroll(app)
            assertEquals("Get different words stays off until Retry-After ends", listOf("rerollCode"), stub.operations.toList())
            minted.endWait()
            assertFalse(minted.waiting)
        }
    }

    @Test
    fun aWaitThatHasPassedLetsCreateContinue(): Unit = runBlocking {
        limited("mintCode", "1").use { stub ->
            val app = TestApp.make(stub.service())
            val model = CreateModel().apply { title = "Lost cat" }
            model.mint(app)
            assertTrue(model.waiting)
            delay(1200)
            model.mint(app)
            assertEquals(listOf("mintCode", "mintCode"), stub.operations.toList())
        }
    }
}

/** Idle-expired codes in My codes (tasks.md T103; zzThis D-2026-10-11-15), and the account id on Account (D-2026-10-13-08). */
class ExpiredAndAccountTest {
    @Test
    fun theDemoListHasAnExpiredRowWithNoRevoke(): Unit = runBlocking {
        val service = TestApp.demoService()
        service.tokens.signIn("demo", "demo")
        val rows = (service.myCodes() as ApiResult.Ok).value.codes
        val expired = rows.first { it.status == OwnedCode.Status.expired }
        assertEquals(R.string.codes_status_expired, expired.status.res)
        assertFalse(CodeDetailScreenState(expired, phase = CodeDetailModel.Phase.Loaded).showsRevoke)
        val active = rows.first { it.status == OwnedCode.Status.active }
        assertTrue(CodeDetailScreenState(active, phase = CodeDetailModel.Phase.Loaded).showsRevoke)
        assertTrue("an expired code is not public (FR-010)", service.resolve(expired.canonical) !is ApiResult.Ok)
    }

    @Test
    fun accountShowsTheIdFromGetMeWhileSignedIn(): Unit = runBlocking {
        val service = TestApp.demoService()
        service.tokens.signIn("demo", "demo")
        val app = TestApp.make(service)
        app.completeSignIn(TokenRequest.Provider.google)
        assertNull(app.accountId)
        app.noteAccount((service.me() as ApiResult.Ok).value)
        assertEquals(DemoApi.ACCOUNT.id, app.accountId)
        app.signOut()
        assertNull("signed out, no id shows", app.accountId)
    }
}
