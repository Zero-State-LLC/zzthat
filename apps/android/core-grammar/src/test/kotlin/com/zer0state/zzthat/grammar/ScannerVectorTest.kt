package com.zer0state.zzthat.grammar

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Every `scanner` row of contracts/vectors.json (spec 004 Scanner rules). */
@RunWith(Parameterized::class)
class ScannerVectorTest(@Suppress("unused") private val label: String, private val row: JsonObject) {
    @Test
    fun row() {
        val input = row.string("input") ?: error("row has no input")
        val expected = row["candidates"]?.let { (it as kotlinx.serialization.json.JsonArray).map { c -> describe(c.jsonObject) } }
            ?: error("row has no candidates")
        assertEquals(expected, Scanner.scan(input).map(::describe))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun rows(): List<Array<Any>> {
            val rows = Vectors.rows("scanner")
            assertTrue("no scanner rows", rows.isNotEmpty())
            return Vectors.cases(rows)
        }

        fun describe(candidate: ScanCandidate): String = when (val kind = candidate.kind) {
            is ScanKind.Code -> "code ${kind.code.canonical} ${kind.code.kind.wire}"
            ScanKind.Bare -> "bare"
            is ScanKind.Partial -> "partial ${kind.text}"
            is ScanKind.Invalid -> "invalid ${kind.reason.wire} ${kind.text}"
        }

        fun describe(row: JsonObject): String = when (row.string("kind")) {
            "code" -> "code ${row.string("canonical")} ${row.string("code_kind")}"
            "bare" -> "bare"
            "partial" -> "partial ${row.string("text")}"
            "invalid" -> "invalid ${row.string("reason")} ${row.string("text")}"
            else -> "unknown $row"
        }
    }
}
