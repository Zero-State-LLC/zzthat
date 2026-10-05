package com.zer0state.zzthat.grammar

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The classifier rows of contracts/vectors.json (Section 2.2a G1a, SC-1). */
@RunWith(Parameterized::class)
class ClassifierVectorTest(@Suppress("unused") private val label: String, private val row: JsonObject) {
    @Test
    fun row() {
        val section = Vectors.section("classifier")
        val wordlist = section.strings("wordlist")
        val maxDistance = section.int("max_near_distance") ?: error("no max_near_distance")
        assertEquals("levenshtein", section.string("distance"))
        assertEquals(Classifier.MAX_NEAR_DISTANCE, maxDistance)
        val input = row.string("input") ?: error("row has no input")
        val code = Grammar.parse(input).codeOrNull() ?: error("$input does not parse")
        val result = Classifier.classify(code, wordlist, maxDistance)
        assertEquals(row.string("class"), result.wire)
        val expected = row.obj("near_words") ?: error("row has no near_words")
        val actual = result.nearWords.mapKeys { it.key.toString() }
        assertEquals(expected.keys.associateWith { expected.strings(it) }, actual)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun rows(): List<Array<Any>> {
            val rows = (Vectors.section("classifier")["rows"] as? kotlinx.serialization.json.JsonArray)
                ?.map { it as JsonObject }.orEmpty()
            assertTrue("no classifier rows", rows.isNotEmpty())
            return Vectors.cases(rows)
        }
    }
}

/** The matching-key rows of contracts/vectors.json (Section 2.2a G10, SC-1). */
@RunWith(Parameterized::class)
class MatchKeyVectorTest(@Suppress("unused") private val label: String, private val row: JsonObject) {
    @Test
    fun row() {
        val a = row.string("a") ?: error("row has no a")
        val b = row.string("b") ?: error("row has no b")
        val same = row.bool("same_key") ?: error("row has no same_key")
        val keyA = MatchKey.key(a) ?: error("$a has no key")
        val keyB = MatchKey.key(b) ?: error("$b has no key")
        assertEquals("$keyA vs $keyB", same, keyA == keyB)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun rows(): List<Array<Any>> {
            val rows = Vectors.rows("match_key")
            assertTrue("no match_key rows", rows.isNotEmpty())
            return Vectors.cases(rows) { "${it.string("a")} ${it.string("b")}" }
        }
    }
}

/** The check-word verify rows of contracts/vectors.json (spec 003, SC-1). */
@RunWith(Parameterized::class)
class CheckWordVectorTest(@Suppress("unused") private val label: String, private val row: JsonObject) {
    @Test
    fun row() {
        assertEquals(CheckWord.DATA_WORDS, Vectors.section("check_word").int("data_words"))
        val input = row.string("input") ?: error("row has no input")
        val code = Grammar.parse(input).codeOrNull() ?: error("$input does not parse")
        assertEquals(row.string("result"), CheckWord.verify(code, Vectors.fixture7).wire)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun rows(): List<Array<Any>> {
            val rows = (Vectors.section("check_word")["verify"] as? kotlinx.serialization.json.JsonArray)
                ?.map { it as JsonObject }.orEmpty()
            assertTrue("no check_word verify rows", rows.isNotEmpty())
            return Vectors.cases(rows)
        }
    }
}

class CheckWordTest {
    private val list = Vectors.fixture7

    @Test
    fun issuableCodesVerifyAndMatchTheIssuerRule() {
        val issuable = Vectors.section("check_word").strings("issuable_codes")
        assertTrue(issuable.isNotEmpty())
        assertEquals(issuable, CheckWord.issuableCodes(list))
        for (canonical in issuable) {
            val code = Grammar.parse(canonical).codeOrNull() ?: error("$canonical does not parse")
            assertEquals(canonical, CheckResult.Ok, CheckWord.verify(code, list))
        }
    }

    /** spec 003 FR-020 (a), exhaustive on the fixture list: every single wrong word in every position, and every swap of two words, is detected. */
    @Test
    fun everySubstitutionAndSwapIsDetected() {
        for (canonical in CheckWord.issuableCodes(list)) {
            val words = Grammar.parse(canonical).codeOrNull()!!.words
            for (position in 0 until 3) {
                for (word in list.words) {
                    if (word == words[position]) continue
                    val parts = words.toMutableList().also { it[position] = word }
                    assertEquals("$parts", CheckResult.CheckMismatch, CheckWord.verify(parts, list))
                }
            }
            for ((i, j) in listOf(0 to 1, 0 to 2, 1 to 2)) {
                val parts = words.toMutableList().also { it[i] = words[j]; it[j] = words[i] }
                assertEquals("$parts", CheckResult.CheckMismatch, CheckWord.verify(parts, list))
            }
        }
    }

    /** FR-020 (a), per position: with the other word fixed, the check index is one-to-one in each data word, for any prime list size. */
    @Test
    fun checkIndexIsOneToOnePerPosition() {
        for (n in listOf(7, 2663)) {
            for (fixed in listOf(0, 1, n / 2, n - 1)) {
                assertEquals(n, (0 until n).map { CheckWord.checkIndex(it, fixed, n) }.toSet().size)
                assertEquals(n, (0 until n).map { CheckWord.checkIndex(fixed, it, n) }.toSet().size)
            }
        }
    }

    @Test
    fun unknownWordNamesThePosition() {
        assertEquals(CheckResult.UnknownWord(2), CheckWord.verify(listOf("copper", "hello", "sky"), list))
    }
}
