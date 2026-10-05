package com.zer0state.zzthat

import com.zer0state.zzthat.api.generated.models.TokenRequest
import com.zer0state.zzthat.account.SignInProviders
import com.zer0state.zzthat.account.signIn
import com.zer0state.zzthat.app.BuildKind
import com.zer0state.zzthat.auth.ProviderToken
import com.zer0state.zzthat.auth.SignInFlow
import com.zer0state.zzthat.grammar.NormRect
import com.zer0state.zzthat.grammar.ReadPipeline
import com.zer0state.zzthat.grammar.ReadSource
import com.zer0state.zzthat.grammar.RecognizedLine
import com.zer0state.zzthat.grammar.WordList
import com.zer0state.zzthat.scan.CandidateAction
import com.zer0state.zzthat.scan.ClarifyInput
import com.zer0state.zzthat.scan.ClarifyLogic
import com.zer0state.zzthat.scan.ClarifyStep
import com.zer0state.zzthat.scan.TypeModel
import com.zer0state.zzthat.ui.FieldLimits
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Typed entry, the bands, and Clarify (US2a, spec 004). */
class ReadFlowTest {
    private val fixture7 = WordList("fixture-7", listOf("copper", "lantern", "sky", "maple", "river", "harbor", "falcon"))

    @Test
    fun typedEntryUsesTheGrammar() {
        val pipeline = ReadPipeline(null, null)
        val cases = listOf(
            "zz-b2-smith-1-zz" to CandidateAction.Resolve("zz-b2-smith-1-zz"),
            "ZZ COPPER LANTERN SKY ZZ" to CandidateAction.Resolve("zz-copper-lantern-sky-zz"),
            "zz-#tag-zz" to CandidateAction.Message(R.string.type_malformed),
            "copper" to CandidateAction.Message(R.string.type_malformed),
            "(zz)" to CandidateAction.Message(R.string.scan_bare),
            "   " to CandidateAction.Message(R.string.type_empty),
        )
        for ((text, expected) in cases) {
            val model = TypeModel().apply { this.text = text }
            assertEquals(text, expected, model.action(pipeline))
        }
    }

    @Test
    fun aCameraFieldCodeAsksForOneConfirm() {
        val pipeline = ReadPipeline(null, null)
        assertEquals(ClarifyStep.Confirm, ClarifyLogic.next(listOf("b2", "smith", "1"), emptySet(), confirmed = false, pipeline))
        assertEquals(ClarifyStep.Done("zz-b2-smith-1-zz"), ClarifyLogic.next(listOf("b2", "smith", "1"), emptySet(), confirmed = true, pipeline))
    }

    @Test
    fun aNearWordOffersItsCandidatesAndAsWritten() {
        val pipeline = ReadPipeline(fixture7, fixture7)
        assertEquals(
            ClarifyStep.NearWord(1, "coper", listOf("copper")),
            ClarifyLogic.next(listOf("coper", "lantern", "sky"), emptySet(), confirmed = false, pipeline),
        )
        // Picking the candidate snaps it; the check word then verifies.
        assertEquals(
            ClarifyStep.Done("zz-copper-lantern-sky-zz"),
            ClarifyLogic.next(listOf("copper", "lantern", "sky"), emptySet(), confirmed = true, pipeline),
        )
        // Kept as written, it is a field code and opens as written.
        assertEquals(
            ClarifyStep.Done("zz-coper-lantern-sky-zz"),
            ClarifyLogic.next(listOf("coper", "lantern", "sky"), setOf(1), confirmed = true, pipeline),
        )
    }

    @Test
    fun aCheckWordMismatchIsNeverSentOrCorrected() {
        val pipeline = ReadPipeline(fixture7, fixture7)
        assertEquals(ClarifyStep.CheckMismatch, ClarifyLogic.next(listOf("copper", "maple", "sky"), emptySet(), confirmed = true, pipeline))
    }

    @Test
    fun bandsMapToActions() {
        val pipeline = ReadPipeline(fixture7, fixture7)
        fun action(text: String, confidence: Double = 0.95): CandidateAction? =
            pipeline.read(listOf(RecognizedLine(text, confidence, NormRect(0.0, 0.0, 1.0, 0.1)))).candidates.firstOrNull()
                ?.let { CandidateAction.of(it, ReadSource.CAMERA) }
        assertEquals(CandidateAction.Resolve("zz-copper-lantern-sky-zz"), action("zz-copper-lantern-sky-zz"))
        assertEquals(CandidateAction.Message(R.string.scan_partial), action("label zz-copper-lantern"))
        assertEquals(CandidateAction.Message(R.string.scan_retry), action("zz-copper-lantern-sky-zz", 0.4))
        assertEquals(CandidateAction.Message(R.string.scan_wrong_length), action("zz-copper-lantern-zz"))
        assertEquals(CandidateAction.Message(R.string.scan_bare), action("zz"))
        assertEquals(CandidateAction.Message(R.string.type_malformed), action("zz-#tag-zz"))
        assertTrue("a near-word clarifies", action("zz-coper-lantern-sky-zz") is CandidateAction.Clarify)
        assertEquals(
            CandidateAction.Clarify(ClarifyInput(listOf("copper", "lantern", "sky"), com.zer0state.zzthat.grammar.CodeKind.PLAIN, ReadSource.CAMERA)),
            action("zz-copper-lantern-sky-zz", 0.79),
        )
    }
}

/** Sign-in leaves no half session (runtime.md, Screens: Sign in). */
class SignInAppTest {
    @Test
    fun demoButtonsSignInWithoutAProvider(): Unit = runBlocking {
        val app = TestApp.make(TestApp.demoService(), kind = BuildKind.DEMO)
        val outcome = signIn(TokenRequest.Provider.google, app) { error("Demo calls no provider") }
        assertEquals(SignInFlow.Outcome.SignedIn, outcome)
        assertTrue(app.isSignedIn)
        assertEquals("google", app.provider)
    }

    @Test
    fun aRefusedExchangeLeavesNoSession(): Unit = runBlocking {
        Stub { operation, _ ->
            if (operation == "createNonce") Stub.Reply(200, """{"nonce":"n-1","expires_in":600}""") else Stub.Reply(401, """{"error":"unauthorized"}""")
        }.use { stub ->
            val configured = TestApp.make(stub.service(signedIn = false), googleWebClientId = "123-abc.apps.googleusercontent.com")
            val outcome = signIn(TokenRequest.Provider.google, configured) { ProviderToken.Token("id-1") }
            assertEquals(SignInFlow.Outcome.Failed(com.zer0state.zzthat.api.MessageKey.SIGN_IN_FAILED), outcome)
            assertFalse(configured.isSignedIn)
            assertNull(configured.provider)
            assertFalse(configured.service.tokens.hasSession)
            assertEquals(listOf("createNonce", "exchangeToken"), stub.operations)
        }
    }

    @Test
    fun offlineSignInIsRefused(): Unit = runBlocking {
        Stub { _, _ -> Stub.Reply(500) }.use { stub ->
            val app = TestApp.make(stub.service(signedIn = false), online = false)
            val outcome = signIn(TokenRequest.Provider.dev, app) { ProviderToken.Failed }
            assertEquals(SignInFlow.Outcome.Failed(com.zer0state.zzthat.api.MessageKey.NO_CONNECTION), outcome)
            assertTrue(stub.operations.isEmpty())
        }
    }

    /** Discovery decides the buttons (FR-040): Google on Android, never Apple (ZQ23); Google waits for its client id. */
    @Test
    fun providersFollowDiscoveryAndTheBuild(): Unit = runBlocking {
        val demo = TestApp.make(TestApp.demoService(), kind = BuildKind.DEMO)
        demo.refreshDiscovery()
        assertTrue(TokenRequest.Provider.google in SignInProviders.shown(demo))
        assertFalse(TokenRequest.Provider.apple in SignInProviders.shown(demo))
        val release = TestApp.make(TestApp.demoService(), kind = BuildKind.PROD)
        release.refreshDiscovery()
        assertFalse("Google waits for its client id", TokenRequest.Provider.google in SignInProviders.shown(release))
    }
}

/** DEP-005 limits as Create and Edit record enforce them (runtime.md, Screens). */
class FieldLimitTest {
    @Test
    fun titleStopsAt120CodePointsAfterTrimming() {
        val long = "a".repeat(130)
        assertEquals(120, FieldLimits.limit(long, FieldLimits.TITLE).length)
        val padded = "  " + "b".repeat(120) + "  "
        assertEquals("edge spaces do not count", padded, FieldLimits.limit(padded, FieldLimits.TITLE))
        val accented = "é".repeat(70)
        assertTrue("code points, not characters, are counted", FieldLimits.count(FieldLimits.limit(accented, FieldLimits.TITLE)) <= 120)
        val astral = "𐌰".repeat(130)
        assertEquals(120, FieldLimits.count(FieldLimits.limit(astral, FieldLimits.TITLE)))
    }

    @Test
    fun theReportNoteCountsAsTyped() {
        val note = " " + "n".repeat(500)
        assertEquals(500, FieldLimits.limit(note, FieldLimits.REPORT_NOTE, trimmed = false).length)
    }

    @Test
    fun aOneLineFieldTakesNoLineBreak() {
        assertEquals("Lost cat flyer", FieldLimits.oneLine("Lost\ncat\r\nflyer"))
    }
}
