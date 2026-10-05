package com.zer0state.zzthat.grammar

// The spec 003 check word (Prototype defaults, Q30), as zzThis
// packages/zz-core/src/checkword.ts does it: with word indexes d1 and d2
// (0-based, in list order) and N the list length, which is prime, the check
// word is the word at index (d1 + 2 * d2) mod N. The client only verifies.
// The server chooses the words (ZQ11).

sealed interface CheckResult {
    /** The result name the vectors and the server use. */
    val wire: String

    data object Ok : CheckResult {
        override val wire = "ok"
    }

    data object CheckMismatch : CheckResult {
        override val wire = "check-mismatch"
    }

    /** A part that is not on the list, by 1-based position. */
    data class UnknownWord(val position: Int) : CheckResult {
        override val wire = "unknown-word"
    }

    data object WrongLength : CheckResult {
        override val wire = "wrong-length"
    }
}

object CheckWord {
    /** Issued format (Q27): two data words and one check word. */
    const val DATA_WORDS = 2

    fun checkIndex(d1: Int, d2: Int, listSize: Int): Int = ((d1.toLong() + 2L * d2) % listSize).toInt()

    /** Verify a plain code against a list (spec 003 FR-018, Verify). */
    fun verify(code: ParsedCode, list: WordList): CheckResult = verify(code.words, list)

    fun verify(parts: List<String>, list: WordList): CheckResult {
        val indexes = ArrayList<Int>(parts.size)
        parts.forEachIndexed { offset, part ->
            val index = list.indexOf(part) ?: return CheckResult.UnknownWord(offset + 1)
            indexes += index
        }
        if (indexes.size != DATA_WORDS + 1) return CheckResult.WrongLength
        val expected = checkIndex(indexes[0], indexes[1], list.size)
        return if (indexes[2] == expected) CheckResult.Ok else CheckResult.CheckMismatch
    }

    /**
     * The issuer rule's allowed codes, in list order (spec 003, Issuer rule).
     * The server draws from these at random; tests use this to check the
     * vectors' issuable codes.
     */
    fun issuableCodes(list: WordList): List<String> {
        val n = list.size
        val codes = mutableListOf<String>()
        for (d1 in 0 until n) {
            for (d2 in 0 until n) {
                val check = checkIndex(d1, d2, n)
                if (d1 == d2 || d2 == 0 || (d1 + d2) % n == 0 || check == d1 || check == d2) continue
                codes += Grammar.format(listOf(list.words[d1], list.words[d2], list.words[check]))
            }
        }
        return codes
    }
}

// The field-code matching key, zzThis Section 2.2a G10 and spec 003 FR-024,
// as packages/zz-core/src/matchkey.ts does it. The key covers the parts only
// (D-2026-10-04-09): number words become digits, then lookalikes fold, then
// runs of all-digit parts join.
object MatchKey {
    private val FOLD = mapOf('o' to '0', 'i' to '1', 'l' to '1', 's' to '5', 'z' to '2', 'b' to '8')

    /** The key of a canonical code, or null when the text is not a code with parts. */
    fun key(canonical: String): String? {
        val code = Grammar.parse(canonical).codeOrNull() ?: return null
        return if (code.words.isEmpty()) null else key(code.words)
    }

    fun key(parts: List<String>): String {
        val folded = parts.map { part ->
            val number = Classifier.NUMBER_WORDS.indexOf(part)
            val digits = if (number >= 0) number.toString() else part
            digits.map { FOLD[it] ?: it }.joinToString("")
        }
        val joined = mutableListOf<String>()
        var run = ""
        for (part in folded) {
            if (part.isNotEmpty() && part.all { it in '0'..'9' }) {
                run += part
                continue
            }
            if (run.isNotEmpty()) joined += run
            run = ""
            joined += part
        }
        if (run.isNotEmpty()) joined += run
        return joined.joinToString("-")
    }
}
