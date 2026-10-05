package com.zer0state.zzthat.grammar

/**
 * The v1 text grammar, zzThis docs/SPEC.md Section 2.2a (G1 to G7) at the pin.
 *
 * A pure function: no I/O, no wordlist, no network (spec 003 FR-015). The
 * character sets in G2 steps 1, 2, 4, and 6 are the exact ones the spec names
 * (D-2026-10-04-10), so this parser and the Swift one agree.
 */
enum class CodeKind(val wire: String) {
    PLAIN("plain"),
    HANDLE("handle"),
    NAME("name"),
    BARE("bare"),
}

enum class CodeVariant { DASH, CIRCLED }

/** G6, in the order the parser checks them. */
enum class ParseFailure(val wire: String) {
    EMPTY("empty"),
    TOO_LONG("too-long"),
    NO_MARKER("no-marker"),
    NO_CLOSING_MARKER("no-closing-marker"),
    NO_CONTENT("no-content"),
    MARKER_IN_BODY("marker-in-body"),
    UNSUPPORTED_SCRIPT("unsupported-script"),
    RESERVED_SYMBOL("reserved-symbol"),
    MISPLACED_AT("misplaced-at"),
    INVALID_HANDLE("invalid-handle"),
    INVALID_CHARACTER("invalid-character"),
}

sealed interface ParseResult {
    data class Code(
        val kind: CodeKind,
        val parts: List<String>,
        val variant: CodeVariant,
        val canonical: String,
        val tag: String? = null,
        val handle: String? = null,
        val qualifiers: List<String> = emptyList(),
    ) : ParseResult

    data class Failure(val reason: ParseFailure) : ParseResult
}

object Grammar {
    /** G2 step 1: the input guard, in UTF-16 code units. A parameter. */
    const val MAX_INPUT_UTF16 = 256

    private val PLAIN = Regex("^[a-z0-9]+$")
    private val NAME = Regex("^[a-z0-9]+(?:\\.[a-z0-9]+)*\\.eth$")
    private val HANDLE_BODY = Regex("^[a-z0-9._]{1,32}$")
    private val HAS_ALNUM = Regex("[a-z0-9]")
    private val RESERVED = Regex("[#$/:]")
    private val SEPARATORS = Regex("[- ]+")
    private val TAG_AT = Regex("^zz-([a-z0-9]+)@[ -]*")

    /** G2 steps 1 and 2: whitespace is U+0020, tab, CR, and LF only. */
    internal fun isGrammarSpace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\r' || c == '\n'

    /** G2 step 3: U+2010 to U+2015 and U+2212 read as a hyphen. */
    fun isDash(c: Char): Boolean = c in '‐'..'―' || c == '−'

    fun parse(input: String): ParseResult {
        if (input.all(::isGrammarSpace)) return ParseResult.Failure(ParseFailure.EMPTY)
        if (input.length > MAX_INPUT_UTF16) return ParseResult.Failure(ParseFailure.TOO_LONG)
        val text = normalize(input)
        if (text == "zz") return bare(CodeVariant.DASH)
        if (text == "(zz)") return bare(CodeVariant.CIRCLED)
        val (variant, rest) = opening(text) ?: return ParseResult.Failure(ParseFailure.NO_MARKER)
        val body = when (val closed = closing(rest)) {
            is Closing.Body -> closed.text
            is Closing.Fails -> return ParseResult.Failure(closed.reason)
        }
        val parts = body.split(SEPARATORS).filter { it.isNotEmpty() }
        earlyFault(parts)?.let { return ParseResult.Failure(it) }
        return classify(parts, variant)
    }

    fun canonicalOf(parts: List<String>): String = "zz-${parts.joinToString("-")}-zz"

    private fun bare(variant: CodeVariant) =
        ParseResult.Code(CodeKind.BARE, emptyList(), variant, "zz")

    private fun normalize(input: String): String {
        val spaced = buildString(input.length) {
            for (c in input) append(if (c == '\t' || c == '\r' || c == '\n') ' ' else c)
        }.trim { it == ' ' }
        val lowered = buildString(spaced.length) {
            for (c in spaced) {
                append(
                    when {
                        isDash(c) -> '-'
                        c in 'A'..'Z' -> c + ('a' - 'A')
                        else -> c
                    },
                )
            }
        }
        val opened = if (lowered.startsWith("zz@-")) {
            "zz-@" + lowered.substring(4)
        } else if (lowered.startsWith("zz@")) {
            "zz-@" + lowered.substring(3)
        } else {
            lowered
        }
        // Regex.replaceFirst takes only a String replacement, so the first
        // match is replaced by its range.
        val tagAt = TAG_AT.find(opened) ?: return opened
        return opened.replaceRange(tagAt.range, "zz-${tagAt.groupValues[1]}-@")
    }

    private fun opening(text: String): Pair<CodeVariant, String>? {
        if (text.startsWith("(zz)")) return CodeVariant.CIRCLED to text.substring(4)
        if (text.length > 2 && text.startsWith("zz") && (text[2] == '-' || text[2] == ' ')) {
            return CodeVariant.DASH to text.substring(2).trimStart { it == '-' || it == ' ' }
        }
        return null
    }

    private sealed interface Closing {
        data class Body(val text: String) : Closing
        data class Fails(val reason: ParseFailure) : Closing
    }

    private fun closing(rest: String): Closing {
        if (rest == "zz" || rest == "(zz)") return Closing.Fails(ParseFailure.NO_CONTENT)
        if (rest.endsWith("(zz)")) return Closing.Body(rest.dropLast(4))
        if (rest.length >= 3 && rest.endsWith("zz") && (rest[rest.length - 3] == '-' || rest[rest.length - 3] == ' ')) {
            return Closing.Body(rest.dropLast(2).trimEnd { it == '-' || it == ' ' })
        }
        return Closing.Fails(ParseFailure.NO_CLOSING_MARKER)
    }

    private fun hasNonAsciiLetter(part: String): Boolean {
        var i = 0
        while (i < part.length) {
            val cp = part.codePointAt(i)
            if (Character.isLetter(cp) && cp !in 'a'.code..'z'.code) return true
            i += Character.charCount(cp)
        }
        return false
    }

    private fun earlyFault(parts: List<String>): ParseFailure? {
        if (parts.any { it == "zz" }) return ParseFailure.MARKER_IN_BODY
        if (parts.any(::hasNonAsciiLetter)) return ParseFailure.UNSUPPORTED_SCRIPT
        if (parts.any { RESERVED.containsMatchIn(it) }) return ParseFailure.RESERVED_SYMBOL
        return atProblem(parts)
    }

    private sealed interface At {
        data object None : At
        data object Misplaced : At
        data class Found(val index: Int, val part: String) : At
    }

    private fun soleAt(parts: List<String>): At {
        var found: At = At.None
        parts.forEachIndexed { index, part ->
            if ('@' !in part) return@forEachIndexed
            if (found != At.None) return At.Misplaced
            found = At.Found(index, part)
        }
        return found
    }

    private fun atProblem(parts: List<String>): ParseFailure? {
        val found = when (val at = soleAt(parts)) {
            At.None -> return null
            At.Misplaced -> return ParseFailure.MISPLACED_AT
            is At.Found -> at
        }
        val oneLeadingAt = found.part.startsWith("@") && found.part.indexOf('@', 1) == -1
        if (!oneLeadingAt) return ParseFailure.MISPLACED_AT
        val tagPosition = found.index == 0 || (found.index == 1 && PLAIN.matches(parts[0]))
        if (!tagPosition) return ParseFailure.MISPLACED_AT
        if (!validHandle(found.part)) return ParseFailure.INVALID_HANDLE
        return null
    }

    private fun validHandle(part: String): Boolean {
        val body = part.substring(1)
        if (!HANDLE_BODY.matches(body)) return false
        if (!HAS_ALNUM.containsMatchIn(body)) return false
        return !(body.startsWith(".") || body.endsWith(".") || body.contains(".."))
    }

    private fun qualifiersPlain(parts: List<String>, skip: Int): Boolean =
        parts.withIndex().all { (index, part) -> index == skip || PLAIN.matches(part) }

    private fun classify(parts: List<String>, variant: CodeVariant): ParseResult {
        val at = soleAt(parts)
        if (at is At.Found) {
            if (!qualifiersPlain(parts, at.index)) return ParseResult.Failure(ParseFailure.INVALID_CHARACTER)
            return ParseResult.Code(
                kind = CodeKind.HANDLE,
                parts = parts,
                variant = variant,
                canonical = canonicalOf(parts),
                tag = if (at.index == 1) parts[0] else null,
                handle = at.part,
                qualifiers = parts.drop(at.index + 1),
            )
        }
        val first = parts.firstOrNull() ?: return ParseResult.Failure(ParseFailure.NO_CONTENT)
        if (NAME.matches(first)) {
            if (!qualifiersPlain(parts, 0)) return ParseResult.Failure(ParseFailure.INVALID_CHARACTER)
            return ParseResult.Code(CodeKind.NAME, parts, variant, canonicalOf(parts), qualifiers = parts.drop(1))
        }
        if (!parts.all { PLAIN.matches(it) }) return ParseResult.Failure(ParseFailure.INVALID_CHARACTER)
        return ParseResult.Code(CodeKind.PLAIN, parts, variant, canonicalOf(parts))
    }
}
