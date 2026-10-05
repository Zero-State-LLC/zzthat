package com.zer0state.zzthat.grammar

// Word code, field code, or confirm: zzThis Section 2.2a G1 and spec 003
// FR-021, as zzThis packages/zz-core/src/classify.ts does it. The classifier
// runs after the parser and takes the wordlist as input (spec 003 FR-015).

sealed interface CodeClass {
    /** The class name the vectors use. */
    val wire: String

    /** Every part is a wordlist word. */
    data object Word : CodeClass {
        override val wire = "word"
    }

    /** A plain code that is not a word code and has no near-word. */
    data object Field : CodeClass {
        override val wire = "field"
    }

    /**
     * A plain code with at least one near-word. Keys are 1-based part
     * positions. Candidates are sorted by distance, then by list index.
     */
    data class Confirm(val nearWords: Map<Int, List<String>>) : CodeClass {
        override val wire = "confirm"
    }

    data object Handle : CodeClass {
        override val wire = "handle"
    }

    data object Name : CodeClass {
        override val wire = "name"
    }

    data object Bare : CodeClass {
        override val wire = "bare"
    }
}

val CodeClass.nearWords: Map<Int, List<String>> get() = (this as? CodeClass.Confirm)?.nearWords.orEmpty()

object Classifier {
    /**
     * G1: a part within this Levenshtein distance of a list word is a
     * near-word. A parameter until the real-photo test set measures it.
     */
    const val MAX_NEAR_DISTANCE = 2

    /** The number words zero to nine. G1 treats them as parts with a digit (D-2026-10-04-08). */
    val NUMBER_WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

    fun classify(code: ParsedCode, wordlist: List<String>, maxNearDistance: Int = MAX_NEAR_DISTANCE): CodeClass {
        when (code.kind) {
            CodeKind.BARE -> return CodeClass.Bare
            CodeKind.HANDLE -> return CodeClass.Handle
            CodeKind.NAME -> return CodeClass.Name
            CodeKind.PLAIN -> Unit
        }
        val listed = wordlist.toHashSet()
        val nearWords = linkedMapOf<Int, List<String>>()
        var allListed = true
        code.words.forEachIndexed { index, part ->
            if (part in listed) return@forEachIndexed
            allListed = false
            if (!isLettersOnly(part) || part in NUMBER_WORDS) return@forEachIndexed
            val candidates = nearCandidates(part, wordlist, maxNearDistance)
            if (candidates.isNotEmpty()) nearWords[index + 1] = candidates
        }
        if (nearWords.isNotEmpty()) return CodeClass.Confirm(nearWords)
        return if (allListed) CodeClass.Word else CodeClass.Field
    }

    /** Every list word within the limit, by distance, then by list index. */
    fun nearCandidates(part: String, wordlist: List<String>, limit: Int = MAX_NEAR_DISTANCE): List<String> =
        wordlist.asSequence()
            .filter { kotlin.math.abs(it.length - part.length) <= limit }
            .map { it to Levenshtein.distance(part, it) }
            .filter { it.second <= limit }
            .toList()
            .sortedBy { it.second }
            .map { it.first }

    private fun isLettersOnly(part: String): Boolean = part.isNotEmpty() && part.all { it in 'a'..'z' }
}

/**
 * Levenshtein distance over lowercase ASCII letters (spec 003 FR-003): insert,
 * delete, or substitute one letter, each costing 1, so a swap of two letters
 * costs 2.
 */
object Levenshtein {
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
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
