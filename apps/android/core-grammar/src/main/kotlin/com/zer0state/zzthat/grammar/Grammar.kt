package com.zer0state.zzthat.grammar

import java.text.Normalizer

// The v1 text grammar, zzThis docs/SPEC.md Section 2.2a, at the pin.
//
// The structure follows zzThis packages/zz-core/src/grammar.ts at the pin,
// the parser the server runs, step for step. G2 steps 1, 2, 4, and 6 use the
// character sets of D-2026-10-04-10: the 256 guard counts the raw input in
// UTF-16 code units, the fullwidth forms U+FF01 to U+FF5E map to ASCII, the
// text is normalized to NFC, whitespace is the Unicode White_Space set,
// lowercasing has no locale rules, and a letter that is still not ASCII fails
// with unsupported-script. G2 step 5 runs on the split parts
// (D-2026-10-04-04): an `@` touching the opening zz is read as separate, a
// first part `tag@rest` becomes `tag` and `@rest`, and a part that is exactly
// `@` joins the part after it.

enum class CodeKind(val wire: String) {
    PLAIN("plain"),
    HANDLE("handle"),
    NAME("name"),
    BARE("bare"),
}

enum class CodeVariant(val wire: String) {
    DASH("dash"),
    CIRCLED("circled"),
}

/** The parser reasons, in the fixed order of Section 2.2a G6. */
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

data class ParsedCode(
    val kind: CodeKind,
    /** The parts between the markers. Empty for a bare mark. */
    val words: List<String>,
    val variant: CodeVariant,
    val canonical: String,
    val tag: String? = null,
    val handle: String? = null,
    val qualifiers: List<String> = emptyList(),
)

sealed interface ParseResult {
    data class Success(val code: ParsedCode) : ParseResult

    data class Failure(val reason: ParseFailure) : ParseResult

    fun codeOrNull(): ParsedCode? = (this as? Success)?.code
}

object Grammar {
    /** G2 step 1: the input guard, in UTF-16 code units. */
    const val MAX_INPUT_UTF16 = 256

    fun parse(input: String): ParseResult {
        // G2 step 1: the guard counts the raw input, before any mapping. G6
        // still ranks empty ahead of too-long, and the mapping never turns
        // whitespace into anything else or anything else into whitespace, so
        // a long input that is only whitespace is empty.
        if (input.length > MAX_INPUT_UTF16) {
            return fail(if (Unicode.isBlank(input)) ParseFailure.EMPTY else ParseFailure.TOO_LONG)
        }
        val prepared = map(input)
        if (Unicode.isBlank(prepared)) return fail(ParseFailure.EMPTY)
        val text = normalize(prepared)
        if (text == "zz") return ParseResult.Success(bare(CodeVariant.DASH))
        if (text == "(zz)") return ParseResult.Success(bare(CodeVariant.CIRCLED))
        val opened = opening(text) ?: return fail(ParseFailure.NO_MARKER)
        val body = when (val closed = closing(opened.rest)) {
            is Closed.Body -> closed.text
            is Closed.Fails -> return fail(closed.reason)
        }
        // G2 step 6: after step 3 every dash is a hyphen and after step 2
        // every whitespace character is a space, so these are the separators.
        val split = body.split(' ', '-').filter { it.isNotEmpty() }
        // G2 step 5 on the split parts (D-2026-10-04-04).
        val parts = joinLoneAt(splitTagAt(split))
        earlyFault(parts)?.let { return fail(it) }
        return classify(parts, opened.variant)
    }

    /** The canonical form of a list of parts. */
    fun format(words: List<String>): String = "zz-" + words.joinToString("-") + "-zz"

    /** NAME: labels of a to z and 0 to 9 joined by single dots, ending in .eth. */
    fun isName(part: String): Boolean = NAME.matches(part)

    /** PLAIN: one or more of a to z and 0 to 9. */
    fun isPlain(part: String): Boolean = part.isNotEmpty() && part.all { it in 'a'..'z' || it in '0'..'9' }

    private val NAME = Regex("^[a-z0-9]+(?:\\.[a-z0-9]+)*\\.eth$")
    private val HANDLE_BODY = Regex("^[a-z0-9._]{1,32}$")
    private val HAS_PLAIN = Regex("[a-z0-9]")

    private fun fail(reason: ParseFailure): ParseResult = ParseResult.Failure(reason)

    private fun bare(variant: CodeVariant) = ParsedCode(CodeKind.BARE, emptyList(), variant, "zz")

    // MARK: Normalization (Section 2.2a G2)

    /**
     * G2 step 1, after the guard: map the way the PRECIS UsernameCaseMapped
     * profile does, for an ASCII result. Fullwidth forms become ASCII, then
     * NFC, so the Kelvin sign U+212A becomes K.
     */
    internal fun map(input: String): String {
        val narrowed = buildString(input.length) {
            for (c in input) append(if (c in '！'..'～') (c.code - 0xFEE0).toChar() else c)
        }
        return Normalizer.normalize(narrowed, Normalizer.Form.NFC)
    }

    /**
     * G2 steps 2 to 4: every whitespace character is a space and the ends are
     * trimmed, dashes are hyphens, and letters take the Unicode lowercase
     * mapping without locale rules. A letter that is still not ASCII fails
     * later with unsupported-script (earlyFault).
     */
    internal fun normalize(text: String): String {
        val spaced = Unicode.plainSpaces(text).trim(' ')
        val hyphens = buildString(spaced.length) {
            for (c in spaced) append(if (Unicode.isDash(c.code)) '-' else c)
        }
        return hyphens.lowercase()
    }

    private class Opened(val variant: CodeVariant, val rest: String)

    private fun opening(text: String): Opened? {
        if (text.startsWith("(zz)")) return Opened(CodeVariant.CIRCLED, text.substring(4))
        // An @ touching the opening zz is read as separate (D-2026-10-04-04).
        if (text.startsWith("zz@")) return Opened(CodeVariant.DASH, text.substring(2))
        if (text.length >= 3 && text.startsWith("zz") && (text[2] == '-' || text[2] == ' ')) {
            return Opened(CodeVariant.DASH, text.substring(3))
        }
        return null
    }

    private sealed interface Closed {
        class Body(val text: String) : Closed

        class Fails(val reason: ParseFailure) : Closed
    }

    private fun closing(rest: String): Closed {
        if (rest == "zz" || rest == "(zz)") return Closed.Fails(ParseFailure.NO_CONTENT)
        if (rest.endsWith("(zz)")) return Closed.Body(rest.dropLast(4))
        if (rest.length >= 3 && rest.endsWith("zz") && (rest[rest.length - 3] == '-' || rest[rest.length - 3] == ' ')) {
            return Closed.Body(rest.dropLast(2))
        }
        return Closed.Fails(ParseFailure.NO_CLOSING_MARKER)
    }

    /**
     * G2 step 5 (D-2026-10-04-04): a first part `tag@rest`, where the tag is
     * a plain part, becomes `tag` and `@rest`.
     */
    internal fun splitTagAt(parts: List<String>): List<String> {
        val first = parts.firstOrNull() ?: return parts
        val at = first.indexOf('@')
        if (at < 1 || !isPlain(first.substring(0, at))) return parts
        return listOf(first.substring(0, at), first.substring(at)) + parts.drop(1)
    }

    /**
     * G2 step 5 (D-2026-10-04-04): a part that is exactly `@` joins the part
     * after it. Alone, it stays `@` and fails with invalid-handle.
     */
    internal fun joinLoneAt(parts: List<String>): List<String> {
        val joined = mutableListOf<String>()
        for (part in parts) {
            if (joined.lastOrNull() == "@") joined[joined.size - 1] = "@$part" else joined += part
        }
        return joined
    }

    // MARK: Parts (grammar.ts earlyFault, atProblem, classify)

    private sealed interface At {
        data object None : At

        data object Misplaced : At

        class Found(val index: Int, val part: String) : At
    }

    private fun soleAt(parts: List<String>): At {
        var found: At = At.None
        parts.forEachIndexed { index, part ->
            if ('@' !in part) return@forEachIndexed
            if (found !== At.None) return At.Misplaced
            found = At.Found(index, part)
        }
        return found
    }

    private fun oneLeadingAt(part: String): Boolean = part.startsWith("@") && part.indexOf('@', 1) == -1

    private fun tagPosition(parts: List<String>, index: Int): Boolean = when (index) {
        0 -> true
        1 -> isPlain(parts[0])
        else -> false
    }

    private fun validHandle(part: String): Boolean {
        val body = part.substring(1)
        if (!HANDLE_BODY.matches(body)) return false
        if (!HAS_PLAIN.containsMatchIn(body)) return false
        return !(body.startsWith(".") || body.endsWith(".") || body.contains(".."))
    }

    private fun atProblem(parts: List<String>): ParseFailure? = when (val found = soleAt(parts)) {
        At.None -> null
        At.Misplaced -> ParseFailure.MISPLACED_AT
        is At.Found -> when {
            !oneLeadingAt(found.part) -> ParseFailure.MISPLACED_AT
            !tagPosition(parts, found.index) -> ParseFailure.MISPLACED_AT
            !validHandle(found.part) -> ParseFailure.INVALID_HANDLE
            else -> null
        }
    }

    private fun earlyFault(parts: List<String>): ParseFailure? {
        if (parts.any { it == "zz" }) return ParseFailure.MARKER_IN_BODY
        if (parts.any(Unicode::hasNonAsciiLetter)) return ParseFailure.UNSUPPORTED_SCRIPT
        if (parts.any { part -> part.any { it == '#' || it == '$' || it == '/' || it == ':' } }) {
            return ParseFailure.RESERVED_SYMBOL
        }
        return atProblem(parts)
    }

    private fun qualifiersPlain(parts: List<String>, skip: Int): Boolean =
        parts.withIndex().all { (index, part) -> index == skip || isPlain(part) }

    private fun classify(parts: List<String>, variant: CodeVariant): ParseResult {
        val found = soleAt(parts) as? At.Found ?: return classifyPlain(parts, variant)
        if (!qualifiersPlain(parts, found.index)) return fail(ParseFailure.INVALID_CHARACTER)
        return ParseResult.Success(
            ParsedCode(
                kind = CodeKind.HANDLE,
                words = parts,
                variant = variant,
                canonical = format(parts),
                tag = if (found.index == 1) parts[0] else null,
                handle = found.part,
                qualifiers = parts.drop(found.index + 1),
            ),
        )
    }

    private fun classifyPlain(parts: List<String>, variant: CodeVariant): ParseResult {
        val first = parts.firstOrNull() ?: return fail(ParseFailure.NO_CONTENT)
        if (isName(first)) {
            if (!qualifiersPlain(parts, 0)) return fail(ParseFailure.INVALID_CHARACTER)
            return ParseResult.Success(ParsedCode(CodeKind.NAME, parts, variant, format(parts), qualifiers = parts.drop(1)))
        }
        if (!parts.all(::isPlain)) return fail(ParseFailure.INVALID_CHARACTER)
        return ParseResult.Success(ParsedCode(CodeKind.PLAIN, parts, variant, format(parts)))
    }
}

/** The Section 2.2a character sets, by code point. */
object Unicode {
    /**
     * G2 step 1 (D-2026-10-04-10): the characters with the Unicode White_Space
     * property, written out as the spec lists them so every platform agrees on
     * any Unicode version. U+FEFF is not one of them.
     */
    fun isWhiteSpace(cp: Int): Boolean = when (cp) {
        in 0x09..0x0D, 0x20, 0x85, 0xA0, 0x1680, in 0x2000..0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true
        else -> false
    }

    /** G2 step 3: U+2010 to U+2015 and U+2212 read as a hyphen. */
    fun isDash(cp: Int): Boolean = cp in 0x2010..0x2015 || cp == 0x2212

    /** True when the text is empty or only whitespace. */
    fun isBlank(text: String): Boolean = text.all { isWhiteSpace(it.code) }

    /** Every whitespace character becomes U+0020 (G2 step 2, spec 004 Scanner rule 1). */
    fun plainSpaces(text: String): String = buildString(text.length) {
        for (c in text) append(if (isWhiteSpace(c.code)) ' ' else c)
    }

    /** NON_ASCII_LETTER: a Unicode letter (general category L) that is not a to z. */
    fun hasNonAsciiLetter(part: String): Boolean {
        var i = 0
        while (i < part.length) {
            val cp = part.codePointAt(i)
            if (cp !in 'a'.code..'z'.code && Character.isLetter(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }
}
