package com.zer0state.zzthat.grammar

import kotlin.math.abs

/** A box normalized to the image, with the origin at the top left. */
data class NormRect(val x: Double, val y: Double, val width: Double, val height: Double) {
    val minX get() = x
    val minY get() = y
    val maxX get() = x + width
    val maxY get() = y + height
    val midY get() = y + height / 2

    fun union(other: NormRect): NormRect {
        val left = minOf(minX, other.minX)
        val top = minOf(minY, other.minY)
        return NormRect(left, top, maxOf(maxX, other.maxX) - left, maxOf(maxY, other.maxY) - top)
    }

    companion object {
        val ZERO = NormRect(0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * One recognized line (runtime.md, Read pipeline step 2). The reader keeps the
 * line's own confidence: on Android `Text.Line.getConfidence()`, never an
 * Element or Symbol confidence.
 */
data class RecognizedLine(
    val text: String,
    /** The line confidence, from 0 to 1. */
    val confidence: Double,
    val box: NormRect,
    /** Boxes for the line's words, when the reader can give them. */
    val pieces: List<Piece> = emptyList(),
) {
    /** A run of non-space text: its code point offsets in [text], and its box. */
    data class Piece(val range: IntRange, val box: NormRect)
}

/**
 * A candidate after the pipeline: the scanner result, its class, the local
 * check word when it ran, the band, and where it sits on the photo.
 */
data class ReadCandidate(
    val id: Int,
    val kind: ScanKind,
    val codeClass: CodeClass?,
    val check: CheckResult?,
    val band: Band,
    val rows: List<BandRow>,
    val boxes: List<NormRect>,
) {
    val canonical: String? get() = (kind as? ScanKind.Code)?.code?.canonical
}

data class ReadResult(val text: String, val candidates: List<ReadCandidate>) {
    /**
     * Client read pipeline step 6: open at once only when there is one
     * candidate and it is in Accept. Otherwise the person picks.
     */
    val autoOpen: ReadCandidate? get() = candidates.singleOrNull()?.takeIf { it.band == Band.ACCEPT }
}

/** Typed entry: one candidate, or the parser reason. */
sealed interface TypedRead {
    data class Read(val candidate: ReadCandidate) : TypedRead

    data class Refused(val reason: ParseFailure) : TypedRead
}

/** The zzThis spec 004 Client read pipeline, steps 3 to 5 (runtime.md, Read pipeline). */
class ReadPipeline(
    /** The list to classify against (the bundled list). */
    val classifyList: WordList?,
    /** The list for a local check word: only the bundled list whose version equals the server's `wordlist_version` (FR-044). */
    val checkList: WordList?,
) {
    /** Steps 3 to 5 on recognized lines. */
    fun read(lines: List<RecognizedLine>): ReadResult {
        val ordered = readingOrder(lines)
        val text = StringBuilder()
        val lineRanges = mutableListOf<IntRange>()
        var length = 0
        ordered.forEachIndexed { index, line ->
            if (index > 0) {
                text.append(' ')
                length++
            }
            val start = length
            text.append(line.text)
            length += line.text.codePointCount(0, line.text.length)
            lineRanges += start until length
        }
        val joined = text.toString()
        val points = joined.codePoints().toArray()
        val candidates = Scanner.scan(joined).mapIndexed { offset, candidate ->
            val rows = candidate.wordRanges.map { range ->
                val line = lineRanges.indexOfFirst { range.first in it }.coerceAtLeast(0)
                BandRow(
                    token = String(points, range.first, range.last - range.first + 1),
                    lineIndex = line,
                    lineConfidence = if (ordered.isEmpty()) 0.0 else ordered[line].confidence,
                )
            }
            evaluate(candidate.kind, offset, rows, boxes(candidate.range, ordered, lineRanges), ReadSource.CAMERA)
        }
        return ReadResult(joined, candidates)
    }

    /**
     * Typed entry (US2a): the whole input must be one code or one bare mark
     * (Section 2.2a G8). Returns the parser reason on failure.
     */
    fun readTyped(input: String): TypedRead = when (val parsed = Grammar.parse(input)) {
        is ParseResult.Failure -> TypedRead.Refused(parsed.reason)
        is ParseResult.Success -> {
            val kind = if (parsed.code.kind == CodeKind.BARE) ScanKind.Bare else ScanKind.Code(parsed.code)
            TypedRead.Read(evaluate(kind, 0, emptyList(), emptyList(), ReadSource.TYPED))
        }
    }

    fun evaluate(kind: ScanKind, id: Int, rows: List<BandRow>, boxes: List<NormRect>, source: ReadSource): ReadCandidate {
        var codeClass: CodeClass? = null
        var check: CheckResult? = null
        if (kind is ScanKind.Code) {
            val code = kind.code
            codeClass = Classifier.classify(code, classifyList?.words.orEmpty())
            val list = checkList
            if (codeClass == CodeClass.Word && list != null && code.words.all { it in list }) {
                check = CheckWord.verify(code, list)
            }
        }
        val band = Bands.band(kind, codeClass, check, rows, source)
        return ReadCandidate(id, kind, codeClass, check, band, rows, boxes)
    }

    companion object {
        /** The bundled lists and the server's list version pick both lists (FR-044). */
        fun of(bundled: List<WordList>, serverVersion: String?): ReadPipeline {
            val check = WordListLoader.matching(bundled, serverVersion)
            return ReadPipeline(classifyList = check ?: bundled.firstOrNull(), checkList = check)
        }

        /**
         * Step 3: top to bottom, then left to right. Lines whose vertical
         * centers fall inside one another's height share a row.
         */
        fun readingOrder(lines: List<RecognizedLine>): List<RecognizedLine> {
            val byTop = lines.sortedWith(compareBy({ it.box.minY }, { it.box.minX }))
            val rows = mutableListOf<MutableList<RecognizedLine>>()
            for (line in byTop) {
                val last = rows.lastOrNull()?.first()
                if (last != null &&
                    abs(line.box.midY - last.box.midY) < maxOf(minOf(line.box.height, last.box.height) / 2, 0.0001)
                ) {
                    rows.last() += line
                } else {
                    rows += mutableListOf(line)
                }
            }
            return rows.flatMap { row -> row.sortedBy { it.box.minX } }
        }

        internal fun boxes(range: IntRange, lines: List<RecognizedLine>, lineRanges: List<IntRange>): List<NormRect> {
            val boxes = mutableListOf<NormRect>()
            lineRanges.forEachIndexed { index, lineRange ->
                // A candidate on another line has no overlap with this one.
                val lower = maxOf(range.first, lineRange.first)
                val upper = minOf(range.last + 1, lineRange.last + 1)
                if (lower >= upper) return@forEachIndexed
                val local = (lower - lineRange.first) until (upper - lineRange.first)
                val line = lines[index]
                val pieces = line.pieces.filter { it.range.first <= local.last && local.first <= it.range.last }
                boxes += if (pieces.isEmpty()) line.box else pieces.drop(1).fold(pieces[0].box) { box, piece -> box.union(piece.box) }
            }
            return boxes
        }
    }
}
