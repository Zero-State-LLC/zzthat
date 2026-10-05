package com.zer0state.zzthat.grammar

// The zzThis spec 004 scanner (Client read pipeline, Scanner rules 1 to 8).
// It finds every marker pair, partial code, and bare mark in running text and
// never picks one (FR-013). Rules 4 to 8 follow zzThis
// packages/zz-core/src/scanner.ts at the pin step for step, so every platform
// finds the same candidates. Positions are kept alongside, in code point
// offsets, for the boxes and the band rows.

sealed interface ScanKind {
    data class Code(val code: ParsedCode) : ScanKind

    data object Bare : ScanKind

    data class Partial(val text: String) : ScanKind

    data class Invalid(val reason: ParseFailure, val text: String) : ScanKind
}

data class ScanCandidate(
    val kind: ScanKind,
    /** Code point offsets in the scanned text, from the first character to the last one the candidate covers. */
    val range: IntRange,
    /**
     * Code point ranges of the candidate's words: every part between the
     * markers, the check word included, and never a marker. A bare mark
     * lists itself.
     */
    val wordRanges: List<IntRange>,
)

object Scanner {
    fun scan(text: String): List<ScanCandidate> {
        // Rule 1: every White_Space character becomes U+0020.
        val s = text.codePoints().map { if (Unicode.isWhiteSpace(it)) ' '.code else it }.toArray()
        return candidates(tokenize(s), s)
    }

    /** Rule 2: tokens are split at spaces, hyphens, and the dashes. */
    private fun isSeparator(cp: Int): Boolean = cp == ' '.code || isHyphen(cp)

    private fun isHyphen(cp: Int): Boolean = cp == '-'.code || Unicode.isDash(cp)

    private val LEADING = setOf('('.code, '['.code, '"'.code, '\''.code, 0x201C, 0x2018)
    private val TRAILING = setOf(
        ')'.code, ']'.code, '.'.code, ','.code, ';'.code, '!'.code, '?'.code, '"'.code, '\''.code, 0x201D, 0x2019,
    )

    private class Token(
        /** The whole token, set-aside characters included. */
        val start: Int,
        val end: Int,
        val circled: Boolean,
    ) {
        /** The token after rule 3 set its punctuation aside. */
        var coreStart = start
        var coreEnd = end
        var endsClause = false
        var isMarker = false

        /** A token that starts with zz@ (rule 4). It opens a pair and never closes one. */
        var isOpener = false

        val isEmpty get() = coreStart == coreEnd
        val isWord get() = !isEmpty && !isMarker

        /** An opener with text after its @ (rule 4: that text is content). */
        val holdsContent get() = isOpener && coreEnd - coreStart > 3
    }

    private fun tokenize(s: IntArray): List<Token> {
        val tokens = mutableListOf<Token>()
        var index = 0
        while (index < s.size) {
            if (isSeparator(s[index])) {
                index++
                continue
            }
            var end = index
            while (end < s.size && !isSeparator(s[end])) end++
            tokens += splitCircled(s, index, end)
            index = end
        }
        tokens.forEach { setAside(it, s) }
        return tokens
    }

    /** Rule 2: a circled marker (zz) is its own token wherever it appears. */
    private fun splitCircled(s: IntArray, start: Int, end: Int): List<Token> {
        val pieces = mutableListOf<Token>()
        var pieceStart = start
        var index = start
        while (index + 4 <= end) {
            if (s[index] == '('.code && lower(s[index + 1]) == 'z'.code && lower(s[index + 2]) == 'z'.code &&
                s[index + 3] == ')'.code
            ) {
                if (pieceStart < index) pieces += Token(pieceStart, index, circled = false)
                pieces += Token(index, index + 4, circled = true)
                index += 4
                pieceStart = index
            } else {
                index++
            }
        }
        if (pieceStart < end) pieces += Token(pieceStart, end, circled = false)
        return pieces
    }

    /**
     * Rule 3: set aside punctuation around a token. A trailing colon is set
     * aside only from a bare zz, so the grammar still reports reserved-symbol.
     */
    private fun setAside(token: Token, s: IntArray) {
        if (token.circled) {
            token.isMarker = true
            return
        }
        while (token.coreStart < token.coreEnd && s[token.coreStart] in LEADING) token.coreStart++
        while (token.coreStart < token.coreEnd && s[token.coreEnd - 1] in TRAILING) {
            token.coreEnd--
            token.endsClause = true
        }
        if (token.coreEnd - token.coreStart == 3 && s[token.coreEnd - 1] == ':'.code &&
            lowerText(s, token.coreStart, token.coreEnd - 1) == "zz"
        ) {
            token.coreEnd--
            token.endsClause = true
        }
        val core = lowerText(s, token.coreStart, token.coreEnd)
        if (core == "zz") {
            token.isMarker = true
        } else if (core.startsWith("zz@")) {
            token.isMarker = true
            token.isOpener = true
        }
    }

    private fun lower(cp: Int): Int = if (cp in 'A'.code..'Z'.code) cp + 32 else cp

    private fun lowerText(s: IntArray, start: Int, end: Int): String {
        if (start >= end) return ""
        val builder = StringBuilder()
        for (i in start until end) builder.appendCodePoint(lower(s[i]))
        return builder.toString()
    }

    private class Found(val start: Int, val candidate: ScanCandidate)

    /** Rules 4 to 8. */
    private fun candidates(tokens: List<Token>, s: IntArray): List<ScanCandidate> {
        /** The separators written before a token. Pieces of one token split around a circled marker have none. */
        fun before(index: Int): IntArray =
            if (index > 0) s.copyOfRange(tokens[index - 1].end, tokens[index].start) else IntArray(0)

        fun hyphenRun(index: Int): Boolean {
            val gap = before(index)
            return gap.isNotEmpty() && gap.all(::isHyphen)
        }

        /** A token joined by a hyphen to the word just before it. */
        fun joinedToWordBefore(index: Int): Boolean =
            index > 0 && index < tokens.size && hyphenRun(index) && tokens[index - 1].isWord

        /** A token joined by a hyphen to the word just after it. */
        fun joinedToWordAfter(index: Int): Boolean =
            index + 1 < tokens.size && hyphenRun(index + 1) && tokens[index + 1].isWord

        /** Rule 5: two markers in a row form a pair unless one of its four exceptions holds. */
        fun formsPair(a: Int, b: Int): Boolean {
            if (tokens[b].isOpener || tokens[a].endsClause) return false
            if ((a + 1 until b).any { tokens[it].endsClause }) return false
            val outerBefore = joinedToWordBefore(a)
            val outerAfter = joinedToWordAfter(b)
            if (outerBefore && outerAfter) return false
            val empty = b == a + 1 && !tokens[a].holdsContent
            return !(empty && (outerBefore || outerAfter))
        }

        /**
         * Rule 6: the grammar input drops the set-aside characters and keeps
         * the separators as written. [to] is exclusive.
         */
        fun spanText(from: Int, to: Int): String {
            val builder = StringBuilder()
            for (index in from until to) {
                if (index > from) before(index).forEach { builder.appendCodePoint(it) }
                for (i in tokens[index].coreStart until tokens[index].coreEnd) builder.appendCodePoint(s[i])
            }
            return builder.toString()
        }

        fun span(from: Int, to: Int): IntRange =
            tokens[from].coreStart until maxOf(tokens[from].coreStart, tokens[to - 1].coreEnd)

        /** Every part between the markers, the check word included; an opener's text after its @ counts. */
        fun words(from: Int, to: Int): List<IntRange> = (from until to).mapNotNull { index ->
            val token = tokens[index]
            when {
                token.holdsContent -> (token.coreStart + 3) until token.coreEnd
                token.isWord -> token.coreStart until token.coreEnd
                else -> null
            }
        }

        /** Rule 7, forward: to the clause end, the next marker, or the end. */
        fun forwardEnd(start: Int): Int {
            for (index in start until tokens.size) {
                if (index > start && tokens[index].isMarker) return index
                if (tokens[index].endsClause) return index + 1
            }
            return tokens.size
        }

        /** Rule 7, backward: to the clause start, the previous marker, or the start. */
        fun backwardStart(end: Int): Int {
            var start = end
            while (start > 0 && !tokens[start - 1].isMarker && !tokens[start - 1].endsClause) start--
            return start
        }

        fun partial(from: Int, to: Int) =
            Found(from, ScanCandidate(ScanKind.Partial(spanText(from, to)), span(from, to), words(from, to)))

        val markers = tokens.indices.filter { tokens[it].isMarker }
        val found = mutableListOf<Found>()
        val paired = mutableSetOf<Int>()
        for ((a, b) in markers.zipWithNext()) {
            if (!formsPair(a, b)) continue
            val input = spanText(a, b + 1)
            val kind = when (val parsed = Grammar.parse(input)) {
                is ParseResult.Success ->
                    if (parsed.code.kind != CodeKind.BARE) {
                        ScanKind.Code(parsed.code)
                    } else {
                        ScanKind.Invalid(ParseFailure.NO_CONTENT, input)
                    }
                is ParseResult.Failure -> ScanKind.Invalid(parsed.reason, input)
            }
            found += Found(a, ScanCandidate(kind, span(a, b + 1), words(a, b + 1)))
            paired += a
            paired += b
        }
        for (m in markers) {
            if (m in paired) continue
            val token = tokens[m]
            // A zz or (zz) joined by a hyphen to a word before it reads back only.
            if (!token.isOpener && joinedToWordBefore(m)) {
                found += partial(backwardStart(m), m + 1)
                continue
            }
            // A zz@ token holds its own word, which comes before any clause end.
            val wordFollows = !token.endsClause && m + 1 < tokens.size && tokens[m + 1].isWord
            if (token.holdsContent || wordFollows) {
                found += partial(m, forwardEnd(m))
                continue
            }
            val core = token.coreStart until token.coreEnd
            found += Found(m, ScanCandidate(ScanKind.Bare, core, listOf(core)))
        }

        // Rule 8: reading order, and each canonical code once. Bare marks,
        // partials, and invalid candidates each keep their own entry.
        val seen = mutableSetOf<String>()
        return found.sortedBy { it.start }.mapNotNull { item ->
            val kind = item.candidate.kind
            if (kind is ScanKind.Code && !seen.add(kind.code.canonical)) null else item.candidate
        }
    }
}
