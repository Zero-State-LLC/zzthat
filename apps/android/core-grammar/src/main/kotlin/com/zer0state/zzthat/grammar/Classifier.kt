package com.zer0state.zzthat.grammar

/** Section 2.2a G1 classes. `confirm` means a near-word needs the person. */
enum class CodeClass(val wire: String) {
    WORD("word"),
    FIELD("field"),
    CONFIRM("confirm"),
    HANDLE("handle"),
    NAME("name"),
    BARE("bare"),
}

data class Classification(
    val codeClass: CodeClass,
    /** 1-based part position to its candidates, by distance and then list order. */
    val nearWords: Map<Int, List<String>> = emptyMap(),
)

/**
 * The classifier (zzThis spec 003 FR-021, Section 2.2a G1). It runs after the
 * parser and takes the wordlist as input. It never snaps a part on its own.
 */
object Classifier {
    private val LETTERS = Regex("^[a-z]+$")

    /** Number words count as parts with a digit (D-2026-10-04-08). */
    internal val NUMBER_WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

    fun classify(code: ParseResult.Code, list: WordList?, maxDistance: Int = Parameters.NEAR_WORD_MAX_DISTANCE): Classification =
        when (code.kind) {
            CodeKind.BARE -> Classification(CodeClass.BARE)
            CodeKind.HANDLE -> Classification(CodeClass.HANDLE)
            CodeKind.NAME -> Classification(CodeClass.NAME)
            CodeKind.PLAIN -> classifyPlain(code.parts, list, maxDistance)
        }

    private fun classifyPlain(parts: List<String>, list: WordList?, maxDistance: Int): Classification {
        if (list != null && parts.all { it in list }) return Classification(CodeClass.WORD)
        if (list == null) return Classification(CodeClass.FIELD)
        val near = linkedMapOf<Int, List<String>>()
        parts.forEachIndexed { i, part ->
            if (part in list || !LETTERS.matches(part) || part in NUMBER_WORDS) return@forEachIndexed
            val candidates = list.words.withIndex()
                .map { (index, word) -> Triple(word, levenshtein(part, word), index) }
                .filter { it.second <= maxDistance }
                .sortedWith(compareBy({ it.second }, { it.third }))
                .map { it.first }
            if (candidates.isNotEmpty()) near[i + 1] = candidates
        }
        return if (near.isEmpty()) Classification(CodeClass.FIELD) else Classification(CodeClass.CONFIRM, near)
    }

    /** Levenshtein distance over lowercase ASCII letters (spec 003 FR-003). A swap costs 2. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}

/** The matching key for field codes (Section 2.2a G10, spec 003 FR-024). */
object MatchKey {
    private val ALL_DIGITS = Regex("^[0-9]+$")
    private val FOLD = mapOf('o' to '0', 'i' to '1', 'l' to '1', 's' to '5', 'z' to '2', 'b' to '8')

    /** The key of a parsed code: number words to digits, the fold, then digit runs joined. Parts only. */
    fun of(code: ParseResult.Code): String {
        val numbered = code.parts.map { part ->
            val n = Classifier.NUMBER_WORDS.indexOf(part)
            if (n >= 0) n.toString() else part
        }
        val folded = numbered.map { part -> part.map { FOLD[it] ?: it }.joinToString("") }
        val joined = mutableListOf<String>()
        for (part in folded) {
            val last = joined.lastOrNull()
            if (last != null && ALL_DIGITS.matches(last) && ALL_DIGITS.matches(part)) {
                joined[joined.size - 1] = last + part
            } else {
                joined += part
            }
        }
        return joined.joinToString("-")
    }

    /** The key of any input, or null when it does not parse to a code with content. */
    fun of(input: String): String? = (Grammar.parse(input) as? ParseResult.Code)
        ?.takeIf { it.kind != CodeKind.BARE }
        ?.let(::of)
}

/** Check-word results (zzThis spec 003 FR-018). */
sealed interface Verify {
    data object Ok : Verify
    data object CheckMismatch : Verify
    data object WrongLength : Verify
    data class UnknownWord(val position: Int) : Verify
}

/**
 * The spec 003 check word: check index = (index(word1) + 2 * index(word2)) mod N,
 * with N the list length, which is prime. Two data words and one check word.
 */
object CheckWord {
    const val DATA_WORDS = 2

    fun checkIndex(d1: Int, d2: Int, n: Int): Int = ((d1.toLong() + 2L * d2) % n).toInt()

    /** Verifies the parts of a plain code against the list. Null when the list cannot carry a check word. */
    fun verify(parts: List<String>, list: WordList): Verify? {
        if (!list.supportsCheckWord) return null
        parts.forEachIndexed { i, part -> if (part !in list) return Verify.UnknownWord(i + 1) }
        if (parts.size != DATA_WORDS + 1) return Verify.WrongLength
        val d1 = list.indexOf(parts[0])!!
        val d2 = list.indexOf(parts[1])!!
        val check = list.indexOf(parts[2])!!
        return if (checkIndex(d1, d2, list.size) == check) Verify.Ok else Verify.CheckMismatch
    }

    fun verify(code: ParseResult.Code, list: WordList): Verify? =
        if (code.kind == CodeKind.PLAIN) verify(code.parts, list) else null
}
