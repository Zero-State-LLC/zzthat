package com.zer0state.zzthat.grammar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PipelineTest {
    private val list = Vectors.fixture7

    @Test
    fun readingOrderIsTopToBottomThenLeftToRight() {
        val lines = listOf(
            RecognizedLine("sky-zz", 1.0, NormRect(0.1, 0.5, 0.3, 0.1)),
            RecognizedLine("lantern", 1.0, NormRect(0.5, 0.21, 0.3, 0.1)),
            RecognizedLine("zz-copper", 1.0, NormRect(0.1, 0.2, 0.3, 0.1)),
        )
        assertEquals(listOf("zz-copper", "lantern", "sky-zz"), ReadPipeline.readingOrder(lines).map { it.text })
        val result = ReadPipeline(null, null).read(lines)
        assertEquals("zz-copper lantern sky-zz", result.text)
        assertEquals(listOf("zz-copper-lantern-sky-zz"), result.candidates.map { it.canonical })
    }

    @Test
    fun aCandidateTakesTheLowestLine() {
        val lines = listOf(
            RecognizedLine("zz-copper-lantern", 0.9, NormRect(0.0, 0.0, 1.0, 0.1)),
            RecognizedLine("sky-zz", 0.7, NormRect(0.0, 0.2, 1.0, 0.1)),
        )
        val candidate = ReadPipeline(list, list).read(lines).candidates.single()
        assertEquals(listOf("copper", "lantern", "sky"), candidate.rows.map { it.token })
        assertEquals(listOf(0, 0, 1), candidate.rows.map { it.lineIndex })
        assertEquals(0.7, Bands.candidateConfidence(candidate.rows)!!, 0.0)
        assertEquals(2, candidate.boxes.size)
    }

    /** Two codes on two lines: each candidate gets only its own line's box. */
    @Test
    fun eachCandidateIsBoxedOnItsOwnLine() {
        val top = NormRect(0.1, 0.1, 0.8, 0.1)
        val bottom = NormRect(0.2, 0.5, 0.6, 0.1)
        val lines = listOf(RecognizedLine("zz-river-falcon-sky-zz", 0.9, top), RecognizedLine("zz-b2-4-zz", 0.6, bottom))
        val result = ReadPipeline(null, null).read(lines)
        assertEquals(listOf("zz-river-falcon-sky-zz", "zz-b2-4-zz"), result.candidates.map { it.canonical })
        assertEquals(listOf(listOf(top), listOf(bottom)), result.candidates.map { it.boxes })
        assertEquals(listOf(0.9, 0.6), result.candidates.map { Bands.candidateConfidence(it.rows) })
    }

    /** Word boxes from the reader narrow a candidate's box to its own words. */
    @Test
    fun wordBoxesNarrowTheBox() {
        val text = "Box zz-copper-lantern-sky-zz goes"
        val first = NormRect(0.0, 0.1, 0.1, 0.1)
        val code = NormRect(0.12, 0.1, 0.6, 0.1)
        val last = NormRect(0.75, 0.1, 0.2, 0.1)
        val line = RecognizedLine(
            text, 0.9, NormRect(0.0, 0.1, 0.95, 0.1),
            listOf(RecognizedLine.Piece(0..2, first), RecognizedLine.Piece(4..27, code), RecognizedLine.Piece(29..32, last)),
        )
        assertEquals(listOf(listOf(code)), ReadPipeline(null, null).read(listOf(line)).candidates.map { it.boxes })
    }

    @Test
    fun severalCandidatesNeverAutoOpen() {
        val line = RecognizedLine("zz-copper-lantern-sky-zz and zz-copper-sky-river-zz", 1.0, NormRect(0.0, 0.0, 1.0, 0.1))
        val result = ReadPipeline(list, list).read(listOf(line))
        assertEquals(2, result.candidates.size)
        assertNull(result.autoOpen)
        val single = ReadPipeline(list, list).read(listOf(RecognizedLine("zz-copper-lantern-sky-zz", 1.0, NormRect.ZERO)))
        assertEquals("zz-copper-lantern-sky-zz", single.autoOpen?.canonical)
    }

    @Test
    fun loaderReadsVersionedListsAndChecksThem() {
        val directory = Files.createTempDirectory("zz-lists").toFile()
        try {
            File(directory, "fixture-7.txt").writeText(list.words.joinToString("\n") + "\n")
            File(directory, "proto-v0.report.md").writeText("# report")
            val lists = WordListLoader.load(directory)
            assertEquals(listOf("fixture-7"), lists.map { it.version })
            assertEquals(list.words, lists.first().words)
            assertEquals("fixture-7", WordListLoader.matching(lists, "fixture-7")?.version)
            assertNull(WordListLoader.matching(lists, "proto-v0"))
            assertNull(WordListLoader.matching(lists, null))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun aMissingFolderLoadsNoList() {
        assertTrue(WordListLoader.load(File("/no/such/zz/folder")).isEmpty())
    }

    @Test
    fun loaderRefusesABadList() {
        val cases = mapOf(
            "copper\nCopper\n" to "t: line 2 is not a to z",
            "copper\nzz\n" to "t: line 2 is a marker or macro prefix",
            "copper\ncopper\n" to "t: line 2 repeats a word",
            "" to "t: is empty",
        )
        for ((text, message) in cases) {
            val error = assertThrows(WordListException::class.java) { WordListLoader.parse("t", text) }
            assertEquals(message, error.message)
        }
    }

    private val pinned by lazy { WordListLoader.load(File(System.getProperty("zz.wordlists"))) }

    /** #43 A2 and FR-044: a bundled list that matches the server's version is used for both the classifier and the check word. */
    @Test
    fun aMatchingListDecidesBothLists() {
        for (version in listOf("fixture-7", "proto-v0")) {
            val pipeline = ReadPipeline.of(pinned, serverVersion = version)
            assertEquals(version, pipeline.classifyList?.version)
            assertEquals(version, pipeline.checkList?.version)
        }
    }

    /** #43 A2: with no matching list, there is no local check word and the classifier uses proto-v0, never fixture-7. */
    @Test
    fun withoutAMatchTheClassifierUsesProtoV0() {
        val pipeline = ReadPipeline.of(pinned, serverVersion = "proto-v1")
        assertEquals("proto-v0", pipeline.classifyList?.version)
        assertNull(pipeline.checkList)
    }

    /** #43 A2: before discovery loads there is no server version, and the classifier uses proto-v0. */
    @Test
    fun beforeDiscoveryTheClassifierUsesProtoV0() {
        val pipeline = ReadPipeline.of(pinned, serverVersion = null)
        assertEquals(ReadPipeline.DEFAULT_CLASSIFY_VERSION, pipeline.classifyList?.version)
        assertNull(pipeline.checkList)
        // The fixture code is not a word code against proto-v0, so a camera read asks the person.
        val read = pipeline.read(listOf(RecognizedLine("zz-copper-lantern-sky-zz", 0.95, NormRect.ZERO))).candidates.single()
        assertNotEquals(CodeClass.Word, read.codeClass)
        assertNull(read.check)
        assertEquals(Band.CLARIFY, read.band)
    }

    /** #43 A2: a build without proto-v0 bundled classifies against no list rather than a fixture. */
    @Test
    fun withoutProtoV0BundledThereIsNoFallbackList() {
        val fixtureOnly = pinned.filter { it.version == "fixture-7" }
        assertNull(ReadPipeline.of(fixtureOnly, serverVersion = null).classifyList)
        assertEquals("fixture-7", ReadPipeline.of(fixtureOnly, serverVersion = "fixture-7").classifyList?.version)
    }

    /** The pinned lists in contracts/wordlists load, and their lengths are prime (spec 003 Q30). */
    @Test
    fun thePinnedListsLoad() {
        val lists = WordListLoader.load(File(System.getProperty("zz.wordlists")))
        assertEquals(listOf("fixture-7", "proto-v0"), lists.map { it.version })
        assertEquals(list.words, lists.first { it.version == "fixture-7" }.words)
        for (loaded in lists) {
            val n = loaded.size
            assertTrue("${loaded.version} has $n words", n > 1 && (2..Math.sqrt(n.toDouble()).toInt()).none { n % it == 0 })
        }
    }
}
