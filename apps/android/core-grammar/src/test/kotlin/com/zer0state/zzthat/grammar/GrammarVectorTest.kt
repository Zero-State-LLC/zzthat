package com.zer0state.zzthat.grammar

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Every `grammar` row of contracts/vectors.json (Section 2.2a G9, SC-1).
 * zzThis spec 005 T035 moved the former `grammar_pending` rows into
 * `grammar`, so `grammar` is every grammar row.
 */
@RunWith(Parameterized::class)
class GrammarVectorTest(@Suppress("unused") private val label: String, private val row: JsonObject) {
    @Test
    fun row() {
        val input = row.string("input") ?: error("row has no input")
        val expect = row.obj("expect") ?: error("row has no expect")
        val result = Grammar.parse(input)
        val reason = expect.string("fail")
        if (reason != null) {
            val failure = result as? ParseResult.Failure ?: return fail("expected $reason, got $result")
            assertEquals(reason, failure.reason.wire)
            return
        }
        val code = result.codeOrNull() ?: return fail("expected success, got $result")
        assertEquals(expect.string("kind"), code.kind.wire)
        assertEquals(expect.string("canonical"), code.canonical)
        expect.string("handle")?.let { assertEquals(it, code.handle) }
        val tag = expect.string("tag")
        if (tag != null) assertEquals(tag, code.tag) else assertNull(code.tag)
        if (expect.containsKey("qualifiers")) assertEquals(expect.strings("qualifiers"), code.qualifiers)
        // spec 003 US3 acceptance 3: the canonical form parses to itself.
        val again = Grammar.parse(code.canonical).codeOrNull() ?: return fail("canonical ${code.canonical} does not parse")
        assertEquals(code.canonical, again.canonical)
        assertEquals(code.kind, again.kind)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun rows(): List<Array<Any>> {
            val rows = Vectors.rows("grammar")
            assertTrue("no grammar rows", rows.isNotEmpty())
            return Vectors.cases(rows)
        }
    }
}

class GrammarTest {
    /** A row parked outside `grammar` would not run, so a section like `grammar_pending` must not come back unnoticed. */
    @Test
    fun everyGrammarRowIsInTheGrammarSection() {
        assertTrue(Vectors.rows("grammar").isNotEmpty())
        assertNull(Vectors.root["grammar_pending"])
    }

    @Test
    fun longRandomInputIsGuarded() {
        assertEquals(ParseResult.Failure(ParseFailure.TOO_LONG), Grammar.parse("zz-copper-".repeat(1000)))
    }

    /** G2 step 1 lists the White_Space characters by hand so every platform agrees. The list is the Unicode property, over every code point. */
    @Test
    fun theWhiteSpaceListIsTheUnicodeProperty() {
        val property = Regex("\\p{IsWhite_Space}")
        val differ = (0..0x10FFFF).filter { cp ->
            if (cp in 0xD800..0xDFFF) return@filter false
            Unicode.isWhiteSpace(cp) != property.matches(String(Character.toChars(cp)))
        }
        assertTrue(differ.joinToString { "U+%04X".format(it) }, differ.isEmpty())
    }

    /** U+FEFF is not White_Space: it neither trims nor separates. */
    @Test
    fun aByteOrderMarkIsNotASpace() {
        assertEquals(ParseResult.Failure(ParseFailure.NO_MARKER), Grammar.parse("﻿"))
        assertEquals(ParseResult.Failure(ParseFailure.NO_MARKER), Grammar.parse("﻿zz-copper-zz"))
    }

    /** Fullwidth forms map before NFC, so a fullwidth letter with a combining mark composes into a non-ASCII letter. */
    @Test
    fun fullwidthMapsBeforeComposition() {
        assertEquals(ParseResult.Failure(ParseFailure.UNSUPPORTED_SCRIPT), Grammar.parse("zz-cafｅ́-zz"))
        assertEquals("zz-camp-bravo-zz", Grammar.parse("（zz） camp bravo （ZZ）").codeOrNull()?.canonical)
    }

    /** Lowercasing has no locale rules: a Turkish dotted capital I is not read as i. */
    @Test
    fun lowercasingHasNoLocaleRules() {
        assertEquals(ParseResult.Failure(ParseFailure.INVALID_CHARACTER), Grammar.parse("zz-İstanbul-zz"))
    }
}
