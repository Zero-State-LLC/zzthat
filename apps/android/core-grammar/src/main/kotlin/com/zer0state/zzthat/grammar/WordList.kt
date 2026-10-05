package com.zer0state.zzthat.grammar

import java.io.File

/** One versioned wordlist (spec 003 FR-016, FR-017). Line order is the index. */
class WordList(val version: String, val words: List<String>) {
    private val indexes: Map<String, Int>

    init {
        if (words.isEmpty()) throw WordListException(version, "is empty")
        val map = HashMap<String, Int>(words.size * 2)
        words.forEachIndexed { offset, word ->
            val line = offset + 1
            if (word.isEmpty() || !word.all { it in 'a'..'z' }) throw WordListException(version, "line $line is not a to z")
            if (word in RESERVED) throw WordListException(version, "line $line is a marker or macro prefix")
            if (map.put(word, offset) != null) throw WordListException(version, "line $line repeats a word")
        }
        indexes = map
    }

    val size: Int get() = words.size

    fun indexOf(word: String): Int? = indexes[word]

    operator fun contains(word: String): Boolean = word in indexes

    override fun equals(other: Any?): Boolean = other is WordList && other.version == version && other.words == words

    override fun hashCode(): Int = 31 * version.hashCode() + words.hashCode()

    override fun toString(): String = "WordList($version, ${words.size} words)"

    private companion object {
        val RESERVED = setOf("zz", "fn", "run")
    }
}

/** A list file that cannot be used. A bad file is refused, never fixed up. */
class WordListException(val version: String, val problem: String) : Exception("$version: $problem")

/**
 * Loads the bundled lists from contracts/wordlists/ (spec 003 Version id:
 * `<version>.txt`, one word per line). Reports and other files are skipped.
 */
object WordListLoader {
    /** Every list in a folder, by file name. A missing folder has none. */
    fun load(directory: File): List<WordList> {
        val files = directory.listFiles() ?: return emptyList()
        return files.map { it.name }.filter(::isListFile).sorted().map { name ->
            parse(versionOf(name), File(directory, name).readText(Charsets.UTF_8))
        }
    }

    /** A bundled list file: `<version>.txt`, not a `.report.` file. */
    fun isListFile(name: String): Boolean = name.endsWith(".txt") && name.length > 4 && ".report." !in name

    fun versionOf(name: String): String = name.removeSuffix(".txt")

    fun parse(version: String, text: String): WordList {
        val lines = text.split('\n').map { it.removeSuffix("\r") }.toMutableList()
        if (lines.lastOrNull() == "") lines.removeAt(lines.lastIndex)
        return WordList(version, lines)
    }

    /** FR-044: the list for a local check word, only when its version equals the server's `wordlist_version`. */
    fun matching(lists: List<WordList>, serverVersion: String?): WordList? =
        serverVersion?.let { version -> lists.firstOrNull { it.version == version } }
}
