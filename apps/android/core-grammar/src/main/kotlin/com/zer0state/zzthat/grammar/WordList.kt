package com.zer0state.zzthat.grammar

/**
 * A versioned closed wordlist (zzThis spec 003 FR-016, FR-017). Line order is
 * the index. The list loader (tasks.md T021) reads the bundled files from
 * contracts/wordlists/, one file per version, named `<version>.txt`.
 */
class WordList private constructor(
    val version: String,
    val words: List<String>,
) {
    private val index: Map<String, Int> = words.withIndex().associate { (i, w) -> w to i }

    val size: Int get() = words.size

    /** The spec 003 check word needs a prime list length (Q30). */
    val supportsCheckWord: Boolean get() = isPrime(words.size)

    fun indexOf(word: String): Int? = index[word]

    operator fun contains(word: String): Boolean = word in index

    sealed interface Load {
        data class Loaded(val list: WordList) : Load
        data class Rejected(val reason: String) : Load
    }

    companion object {
        private val ENTRY = Regex("^[a-z]+$")
        private val FORBIDDEN = setOf("zz", "fn", "run")

        /** Builds a list in memory, for fixtures such as fixture-7 in the vectors. */
        fun of(version: String, words: List<String>): WordList = when (val loaded = validate(version, words)) {
            is Load.Loaded -> loaded.list
            is Load.Rejected -> throw IllegalArgumentException(loaded.reason)
        }

        /** Parses one bundled list file. A bad file is rejected, never fixed up. */
        fun parse(version: String, text: String): Load {
            val lines = text.split('\n').map { it.removeSuffix("\r") }
            val words = if (lines.lastOrNull() == "") lines.dropLast(1) else lines
            return validate(version, words)
        }

        /** The version id of a bundled file name such as `proto-v0.txt`. */
        fun versionOf(fileName: String): String? =
            fileName.takeIf { it.endsWith(".txt") && it.length > 4 }?.removeSuffix(".txt")

        private fun validate(version: String, words: List<String>): Load {
            if (version.isBlank()) return Load.Rejected("no version")
            if (words.isEmpty()) return Load.Rejected("$version is empty")
            words.forEachIndexed { i, word ->
                if (!ENTRY.matches(word)) return Load.Rejected("$version line ${i + 1} is not lowercase a to z")
                if (word in FORBIDDEN) return Load.Rejected("$version line ${i + 1} is a marker or macro prefix")
            }
            if (words.toSet().size != words.size) return Load.Rejected("$version repeats a word")
            return Load.Loaded(WordList(version, words))
        }

        internal fun isPrime(n: Int): Boolean {
            if (n < 2) return false
            var d = 2
            while (d.toLong() * d <= n) {
                if (n % d == 0) return false
                d++
            }
            return true
        }
    }
}
