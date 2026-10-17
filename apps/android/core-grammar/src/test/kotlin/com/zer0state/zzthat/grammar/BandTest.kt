package com.zer0state.zzthat.grammar

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * tasks.md T022: the shared band rows, one table for both platforms
 * (apps/ios/Packages/ZZCore/Tests/ZZCoreTests/Fixtures/band-rows.json, read in
 * place): (token, line index, line confidence) rows in, one band out.
 */
@RunWith(Parameterized::class)
class SharedBandRowTest(@Suppress("unused") private val label: String, private val row: JsonObject) {
    @Test
    fun row() {
        val list = Vectors.fixture7
        val code = Grammar.parse(row.string("canonical")!!).codeOrNull()!!
        val codeClass = Classifier.classify(code, list.words)
        val check = CheckWord.verify(code, list)
        assertEquals(CodeClass.Word, codeClass)
        assertEquals(CheckResult.Ok, check)
        val rows = (row["rows"] as JsonArray).map {
            val item = it.jsonObject
            BandRow(item.string("token")!!, item["line"]!!.jsonPrimitive.int, item["confidence"]!!.jsonPrimitive.double)
        }
        // A row without `confirm_only` is the spec 004 band table; a
        // `confirm_only: true` row (spec 004 FR-023) runs confirm-only mode.
        val confirmOnly = row["confirm_only"]?.jsonPrimitive?.boolean ?: false
        val config = CaptureConfig.DEFAULT.copy(confirmOnly = confirmOnly)
        val band = Bands.band(ScanKind.Code(code), codeClass, check, rows, ReadSource.CAMERA, config = config)
        assertEquals(row.string("band"), band.wire)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun rows(): List<Array<Any>> {
            val shared = (Vectors.read("zz.bandRows").jsonObject["shared"] as JsonArray).map { it.jsonObject }
            assertEquals("the three shared rows", 3, shared.size)
            return Vectors.cases(shared) { it.string("name").orEmpty() }
        }
    }
}

/** One test per band trigger in zzThis spec 004 (Decision bands and Prototype band values). */
class BandTest {
    private val list = Vectors.fixture7

    /** The spec 004 band table, as a platform with a PASS receipt runs it. Confirm-only has its own tests below. */
    private val table = CaptureConfig.DEFAULT.copy(confirmOnly = false)

    private fun camera(vararg lines: Pair<String, Double>, pipeline: ReadPipeline = ReadPipeline(list, list, table)): List<ReadCandidate> {
        val recognized = lines.mapIndexed { offset, (text, confidence) ->
            RecognizedLine(text, confidence, NormRect(0.1, 0.1 + offset * 0.2, 0.8, 0.1))
        }
        return pipeline.read(recognized).candidates
    }

    private fun typed(text: String): TypedRead = ReadPipeline(list, list).readTyped(text)

    private fun typedBand(text: String): Band = (typed(text) as TypedRead.Read).candidate.band

    @Test
    fun cameraAcceptVerifiedWordCode() {
        val found = camera("zz-copper-lantern-sky-zz" to 0.95)
        assertEquals(listOf(Band.ACCEPT), found.map { it.band })
        assertEquals(CheckResult.Ok, found.first().check)
    }

    @Test
    fun cameraClarifyWordBetweenThresholds() {
        assertEquals(listOf(Band.CLARIFY), camera("zz-copper-lantern-sky-zz" to 0.6).map { it.band })
    }

    @Test
    fun cameraClarifyNearWord() {
        val found = camera("zz-coper-lantern-sky-zz" to 0.95)
        assertEquals(listOf(Band.CLARIFY), found.map { it.band })
        assertEquals(CodeClass.Confirm(mapOf(1 to listOf("copper"))), found.first().codeClass)
    }

    @Test
    fun cameraClarifyCheckWordMismatch() {
        val found = camera("zz-copper-maple-sky-zz" to 0.95)
        assertEquals(listOf(Band.CLARIFY), found.map { it.band })
        assertEquals(CheckResult.CheckMismatch, found.first().check)
    }

    @Test
    fun cameraClarifyFieldHandleOrName() {
        for (text in listOf("zz-b2-smith-1-zz", "zz-@agentsmith-zz", "zz-vitalik.eth-zz")) {
            assertEquals(text, listOf(Band.CLARIFY), camera(text to 0.99).map { it.band })
        }
    }

    @Test
    fun cameraRetryPartial() {
        assertEquals(listOf(Band.RETRY), camera("label zz-copper-lantern" to 0.95).map { it.band })
    }

    @Test
    fun cameraRetryNoCandidate() {
        assertEquals(Band.RETRY, Bands.band(null, null, null, emptyList(), ReadSource.CAMERA))
    }

    @Test
    fun cameraRetryWordBelowHalf() {
        val found = camera("zz-copper-lantern" to 0.9, "sky-zz" to 0.45)
        assertEquals(listOf("zz-copper-lantern-sky-zz"), found.map { it.canonical })
        assertEquals(listOf(Band.RETRY), found.map { it.band })
    }

    @Test
    fun cameraAbstainInvalid() {
        assertEquals(listOf(Band.ABSTAIN), camera("zz-#tag-zz" to 0.95).map { it.band })
    }

    @Test
    fun cameraAbstainWrongLength() {
        val found = camera("zz-copper-lantern-zz" to 0.95)
        assertEquals(listOf(Band.ABSTAIN), found.map { it.band })
        assertEquals(CheckResult.WrongLength, found.first().check)
    }

    @Test
    fun cameraAbstainBare() {
        assertEquals(listOf(Band.ABSTAIN), camera("zz" to 0.95).map { it.band })
    }

    /**
     * FR-044 and Danny's #43 A2: with no bundled list matching the server's
     * version, no local check word runs, and the bundled fixture list is not
     * used to classify, so the reading goes to Clarify instead of opening.
     */
    @Test
    fun cameraWithoutAMatchingListSkipsTheLocalCheck() {
        val pipeline = ReadPipeline.of(listOf(list), serverVersion = "proto-v0", capture = table)
        val found = camera("zz-copper-maple-sky-zz" to 0.95, pipeline = pipeline)
        assertNull(found.first().check)
        assertEquals(CodeClass.Field, found.first().codeClass)
        assertEquals(listOf(Band.CLARIFY), found.map { it.band })
    }

    @Test
    fun typedAccept() {
        for (text in listOf("zz-copper-lantern-sky-zz", "zz-b2-smith-1-zz", "zz-@agentsmith-zz", "zz-vitalik.eth-zz")) {
            assertEquals(text, Band.ACCEPT, typedBand(text))
        }
    }

    @Test
    fun typedClarifyNearWordOrMismatch() {
        for (text in listOf("zz-coper-lantern-sky-zz", "zz-copper-maple-sky-zz")) {
            assertEquals(text, Band.CLARIFY, typedBand(text))
        }
    }

    @Test
    fun typedAbstainParserFailure() {
        assertEquals(TypedRead.Refused(ParseFailure.RESERVED_SYMBOL), typed("zz-#tag-zz"))
    }

    @Test
    fun typedAbstainWrongLengthOrBare() {
        for (text in listOf("zz-copper-lantern-zz", "zz", "(zz)")) {
            assertEquals(text, Band.ABSTAIN, typedBand(text))
        }
    }

    /** spec 004 FR-023: each camera Accept row again in confirm-only mode is Clarify, with the full canonical code. */
    @Test
    fun confirmOnlyTurnsEveryCameraAcceptIntoClarify() {
        val confirmOnly = ReadPipeline(list, list, CaptureConfig.DEFAULT.copy(confirmOnly = true))
        val accepted = camera("zz-copper-lantern-sky-zz" to 0.95)
        assertEquals(listOf(Band.ACCEPT), accepted.map { it.band })
        val found = camera("zz-copper-lantern-sky-zz" to 0.95, pipeline = confirmOnly)
        assertEquals(listOf(Band.CLARIFY), found.map { it.band })
        assertEquals(listOf(true), found.map { it.confirmOnly })
        assertEquals(listOf("zz-copper-lantern-sky-zz"), found.map { it.canonical })
        assertEquals("every line at 0.80", listOf(Band.CLARIFY), camera("zz-copper-lantern-sky-zz" to 0.80, pipeline = confirmOnly).map { it.band })
    }

    /** FR-023: every other camera band, and every typed band, is unchanged. */
    @Test
    fun confirmOnlyLeavesTheOtherBandsAlone() {
        val confirmOnly = ReadPipeline(list, list, CaptureConfig.DEFAULT.copy(confirmOnly = true))
        val rows = listOf(
            "zz-copper-lantern-sky-zz" to 0.6,
            "zz-coper-lantern-sky-zz" to 0.95,
            "label zz-copper-lantern" to 0.95,
            "zz-copper-lantern-sky-zz" to 0.4,
            "zz-#tag-zz" to 0.95,
            "zz-copper-lantern-zz" to 0.95,
            "zz" to 0.95,
        )
        for (row in rows) {
            val expected = camera(row).map { it.band }
            assertEquals(row.first, expected, camera(row, pipeline = confirmOnly).map { it.band })
            assertTrue(camera(row, pipeline = confirmOnly).none { it.confirmOnly })
        }
        for (text in listOf("zz-copper-lantern-sky-zz", "zz-b2-smith-1-zz", "zz-copper-maple-sky-zz", "zz-copper-lantern-zz")) {
            val typed = (confirmOnly.readTyped(text) as TypedRead.Read).candidate
            assertEquals(text, typedBand(text), typed.band)
            assertFalse(typed.confirmOnly)
        }
        assertEquals(Band.ACCEPT, (confirmOnly.readTyped("zz-copper-lantern-sky-zz") as TypedRead.Read).candidate.band)
    }

    /** decision-band-mapping precedence: a field code is Clarify before low confidence is Retry. */
    @Test
    fun aFieldCodeClarifiesBeforeLowConfidenceRetries() {
        assertEquals(listOf(Band.CLARIFY), camera("zz-b2-smith-1-zz" to 0.3).map { it.band })
    }

    @Test
    fun aCameraCodeWithoutConfidenceClarifies() {
        val recognized = listOf(RecognizedLine("zz-copper-lantern-sky-zz", null, NormRect(0.1, 0.1, 0.8, 0.1)))
        assertEquals(listOf(Band.CLARIFY), ReadPipeline(list, list, table).read(recognized).candidates.map { it.band })
    }

    @Test
    fun thresholdsAreThePrototypeValues() {
        assertEquals(0.80, BandThresholds.ACCEPT, 0.0)
        assertEquals(0.50, BandThresholds.RETRY_BELOW, 0.0)
    }
}
