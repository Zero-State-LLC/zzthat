package com.zer0state.zzthat.grammar

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

// recognition-adapter.md, Runtime sequence steps 2 to 4: terminal `zz` marker
// observations, same-baseline pairing from geometry and reading order only,
// and the validated interior ROI of one pair. Nothing here reads payload
// text, grammar validity, wordlist proximity, or resolver state. The
// detector looks at each reader token only to ask "is this a marker?"; the
// text of every other token is dropped here and never leaves this file.

/** Step 2: find terminal marker tokens in the reader's lines and box each one. */
object MarkerDetector {
    data class Output(val markers: List<MarkerObservation>, val reasonCodes: List<String>)

    private val LEADING = setOf('('.code, '['.code, '"'.code, '\''.code, 0x201C, 0x2018)
    private val TRAILING = setOf(')'.code, ']'.code, '.'.code, ','.code, ';'.code, '!'.code, '?'.code, '"'.code, '\''.code, 0x201D, 0x2019)

    fun isHyphen(cp: Int): Boolean = cp == '-'.code || Unicode.isDash(cp)

    /** Fullwidth forms to ASCII, then the Unicode lowercase mapping, one code point for one, so offsets stay put. */
    private fun fold(cp: Int): Int = Character.toLowerCase(if (cp in 0xFF01..0xFF5E) cp - 0xFEE0 else cp)

    private class Draft(
        val role: MarkerRole,
        val box: NormRect?,
        val literal: String,
        val edgeBefore: Double?,
        val edgeAfter: Double?,
        val angle: Double?,
        val aloneOnLine: Boolean,
    )

    fun detect(lines: List<RecognizedLine>): Output {
        val drafts = mutableListOf<Draft>()
        for (line in lines) {
            val points = line.text.codePoints().toArray()
            val pieces = line.pieces.ifEmpty { spacePieces(points, line.box) }
            for (piece in pieces) {
                if (piece.range.first < 0 || piece.range.last >= points.size || piece.range.isEmpty()) continue
                drafts += markersIn(points.copyOfRange(piece.range.first, piece.range.last + 1), piece, line, alone = pieces.size == 1)
            }
        }
        val unlocalized = drafts.any { it.box == null }
        val ordered = ReadPipeline.readingOrder(drafts.filter { it.box != null }) { it.box!! }
        val markers = ordered.mapIndexed { index, draft ->
            MarkerObservation(
                detectionId = "m${index + 1}",
                baselineId = null,
                role = draft.role,
                pairId = null,
                score = null,
                box = draft.box!!,
                literal = draft.literal,
                edgeBefore = draft.edgeBefore,
                edgeAfter = draft.edgeAfter,
                angleDegrees = draft.angle,
                aloneOnLine = draft.aloneOnLine,
            )
        }
        return Output(markers, if (unlocalized) listOf(ReasonCodes.UNLOCALIZED_MARKER) else emptyList())
    }

    /** A line with no word boxes: its space-separated runs, boxed only when the line holds one run. */
    private fun spacePieces(points: IntArray, lineBox: NormRect): List<RecognizedLine.Piece> {
        val runs = mutableListOf<IntRange>()
        var index = 0
        while (index < points.size) {
            if (Unicode.isWhiteSpace(points[index])) {
                index++
                continue
            }
            val start = index
            while (index < points.size && !Unicode.isWhiteSpace(points[index])) index++
            runs += start until index
        }
        return runs.map { RecognizedLine.Piece(it, if (runs.size == 1) lineBox else NormRect.ZERO) }
    }

    private fun markersIn(points: IntArray, piece: RecognizedLine.Piece, line: RecognizedLine, alone: Boolean): List<Draft> {
        val folded = points.map(::fold)
        val glyphs = piece.glyphs.takeIf { it.size == points.size }
        val pieceBoxed = piece.box.width > 0 && piece.box.height > 0
        val found = mutableListOf<Draft>()
        var start = 0
        while (start <= points.size) {
            var end = start
            while (end < points.size && !isHyphen(points[end])) end++
            if (end > start) {
                val hyphenBefore = start > 0
                val hyphenAfter = end < points.size
                for (hit in hits(folded, start, end)) {
                    val atStart = hit.from == start || !hasContent(folded, start, hit.from)
                    val atEnd = hit.to == end || !hasContent(folded, hit.to, end)
                    // Glued on a side: payload in the same token, or a hyphen to the next segment.
                    val gluedLeft = hit.contentBefore || (atStart && hyphenBefore)
                    val gluedRight = hit.contentAfter || (atEnd && hyphenAfter)
                    val role = when {
                        hit.opensOnly -> MarkerRole.OPENING
                        gluedRight && !gluedLeft -> MarkerRole.OPENING
                        gluedLeft && !gluedRight -> MarkerRole.CLOSING
                        else -> MarkerRole.UNKNOWN
                    }
                    // The marker's own box: its glyphs, or the whole token when
                    // the token is the marker and set-aside punctuation only.
                    val whole = !gluedLeft && !gluedRight
                    val box = when {
                        glyphs != null -> glyphs.subList(hit.from, hit.to).reduce(NormRect::union)
                        whole && pieceBoxed -> piece.box
                        else -> null
                    }
                    found += Draft(
                        role = role,
                        box = box,
                        literal = String(points, hit.from, hit.to - hit.from),
                        edgeBefore = glyphs?.let {
                            when {
                                hit.contentBefore -> (it[hit.from - 1].maxX + it[hit.from].minX) / 2
                                // Past the joining hyphen, so neither it nor the marker is cut.
                                atStart && hyphenBefore -> it.getOrNull(start - 2)?.let { prev -> (prev.maxX + it[start - 1].minX) / 2 } ?: it[start - 1].minX
                                else -> null
                            }
                        },
                        edgeAfter = glyphs?.let {
                            when {
                                hit.contentAfter -> (it[hit.to - 1].maxX + it[hit.to].minX) / 2
                                atEnd && hyphenAfter -> it.getOrNull(end + 1)?.let { next -> (it[end].maxX + next.minX) / 2 } ?: it[end].maxX
                                else -> null
                            }
                        },
                        angle = line.angleDegrees,
                        aloneOnLine = alone && whole && !hyphenBefore && !hyphenAfter,
                    )
                }
            }
            start = end + 1
        }
        return found
    }

    private class Hit(
        val from: Int,
        val to: Int,
        val literal: String,
        val contentBefore: Boolean,
        val contentAfter: Boolean,
        /** A `zz@` token: it opens a pair and never closes one (Scanner rule 4). */
        val opensOnly: Boolean = false,
    )

    /** True when the span holds anything besides set-aside punctuation. */
    private fun hasContent(folded: List<Int>, from: Int, to: Int): Boolean =
        (from until to).any { folded[it] !in LEADING && folded[it] !in TRAILING && folded[it] != ':'.code }

    private val CIRCLED = "(zz)".codePoints().toArray().toList()

    /**
     * The markers inside one hyphen-separated segment (spec 004 Scanner rules
     * 2 to 4): a circled `(zz)` split out of the segment wherever it sits;
     * otherwise, after rule 3 sets punctuation aside, `zz`, or the zz of a
     * `zz@` opener.
     */
    private fun hits(folded: List<Int>, start: Int, end: Int): List<Hit> {
        val circled = mutableListOf<Hit>()
        var index = start
        while (index + 4 <= end) {
            if (folded.subList(index, index + 4) == CIRCLED) {
                circled += Hit(index, index + 4, "(zz)", hasContent(folded, start, index), hasContent(folded, index + 4, end))
                index += 4
            } else {
                index++
            }
        }
        if (circled.isNotEmpty()) {
            // Content between two circled markers belongs to both sides.
            return circled
        }
        var from = start
        var to = end
        while (from < to && folded[from] in LEADING) from++
        while (to > from && folded[to - 1] in TRAILING) to--
        if (to - from == 3 && folded[to - 1] == ':'.code && folded[from] == 'z'.code && folded[from + 1] == 'z'.code) to--
        val core = folded.subList(from, to)
        return when {
            core == listOf('z'.code, 'z'.code) -> listOf(Hit(from, to, "zz", contentBefore = false, contentAfter = false))
            // In a `zz@` opener the mark is the zz; the @ and what follows are payload (zz-@name-zz).
            core.size >= 3 && core[0] == 'z'.code && core[1] == 'z'.code && core[2] == '@'.code ->
                listOf(Hit(from, from + 2, "zz", contentBefore = false, contentAfter = true, opensOnly = true))
            else -> emptyList()
        }
    }
}

/** Step 2 to 3: baselines, pairs, and the visible candidates, under the frozen limits. */
object MarkerPairing {
    data class Output(
        val markers: List<MarkerObservation>,
        val pairs: List<PairHypothesis>,
        val visual: List<VisualCandidate>,
        /** Each partial or bare candidate's reason, by detection id. They are not the selected pair's reasons. */
        val candidateReasons: Map<String, String>,
        val reasonCodes: List<String>,
        val failedClosed: Boolean,
    )

    private fun failClosed(markers: List<MarkerObservation>, reason: String) =
        Output(markers, emptyList(), emptyList(), emptyMap(), listOf(reason), failedClosed = true)

    /** Pixel geometry of a marker box. */
    private class Px(marker: MarkerObservation, image: PixelSize) {
        val minX = marker.box.minX * image.width
        val maxX = marker.box.maxX * image.width
        val bottom = marker.box.maxY * image.height
        val height = marker.box.height * image.height
    }

    fun sameBaseline(a: MarkerObservation, b: MarkerObservation, image: PixelSize, config: CaptureConfig): Boolean {
        val pa = Px(a, image)
        val pb = Px(b, image)
        val tall = maxOf(pa.height, pb.height)
        val short = minOf(pa.height, pb.height)
        if (short <= 0 || tall / short > config.maxMarkerHeightRatio) return false
        return abs(pa.bottom - pb.bottom) <= config.baselineTolerance * tall
    }

    fun pair(detected: List<MarkerObservation>, image: PixelSize, config: CaptureConfig): Output {
        if (!config.isValid) return failClosed(detected, ReasonCodes.INVALID_CONFIGURATION)
        if (detected.size > config.maxMarkers) return failClosed(detected, ReasonCodes.MARKER_LIMIT_EXCEEDED)
        if (detected.isEmpty()) return Output(detected, emptyList(), emptyList(), emptyMap(), listOf(ReasonCodes.NO_FIDUCIAL), failedClosed = false)
        // V1 does not rectify: skew past the limit, or no angle evidence, is unsupported geometry.
        if (detected.any { val angle = it.angleDegrees; angle == null || !angle.isFinite() || abs(angle) > config.maxSkewDegrees }) {
            return failClosed(detected, ReasonCodes.UNSUPPORTED_GEOMETRY)
        }

        // Baselines: groups where every two markers share a baseline. A chain
        // that does not close (a near b, b near c, a not near c) is ambiguous
        // geometry, and the frame fails closed rather than guessing.
        val parent = IntArray(detected.size) { it }
        fun root(i: Int): Int = if (parent[i] == i) i else root(parent[i]).also { parent[i] = it }
        for (i in detected.indices) for (j in i + 1 until detected.size) {
            if (sameBaseline(detected[i], detected[j], image, config)) parent[root(i)] = root(j)
        }
        val groups = detected.indices.groupBy(::root).values
            .map { members -> members.map { detected[it] }.sortedBy { it.box.minX } }
        for (group in groups) {
            for (i in group.indices) for (j in i + 1 until group.size) {
                if (!sameBaseline(group[i], group[j], image, config)) return failClosed(detected, ReasonCodes.UNSUPPORTED_GEOMETRY)
            }
            // Markers that overlap on one baseline have no reading order.
            for (k in 0 until group.size - 1) {
                if (Px(group[k], image).maxX > Px(group[k + 1], image).minX) return failClosed(detected, ReasonCodes.UNSUPPORTED_GEOMETRY)
            }
        }
        val baselineOf = HashMap<String, String>()
        ReadPipeline.readingOrder(groups.filter { it.size > 1 }) { group -> group.map { it.box }.reduce(NormRect::union) }
            .forEachIndexed { index, group -> group.forEach { baselineOf[it.detectionId] = "b${index + 1}" } }

        // Pairs: two markers in a row on one baseline, the left one able to
        // open and the right one able to close. Every plausible pair is kept;
        // a marker may sit in two of them.
        val drafts = mutableListOf<Pair<MarkerObservation, MarkerObservation>>()
        for (group in groups) {
            for (k in 0 until group.size - 1) {
                val left = group[k]
                val right = group[k + 1]
                if (left.role != MarkerRole.CLOSING && right.role != MarkerRole.OPENING) drafts += left to right
            }
        }
        if (drafts.size > config.maxPairs) return failClosed(detected, ReasonCodes.PAIR_LIMIT_EXCEEDED)
        val pairs = ReadPipeline.readingOrder(drafts) { it.first.box.union(it.second.box) }.mapIndexed { index, (open, close) ->
            PairHypothesis(
                pairId = "p${index + 1}",
                openingDetectionId = open.detectionId,
                closingDetectionId = close.detectionId,
                baselineId = baselineOf.getValue(open.detectionId),
                box = open.box.union(close.box),
            )
        }
        val memberships = pairs.flatMap { listOf(it.openingDetectionId to it.pairId, it.closingDetectionId to it.pairId) }
            .groupBy({ it.first }, { it.second })
        val markers = detected.map { marker ->
            marker.copy(baselineId = baselineOf[marker.detectionId], pairId = memberships[marker.detectionId]?.singleOrNull())
        }
        val reasons = LinkedHashSet<String>()
        val candidateReasons = LinkedHashMap<String, String>()
        val visual = mutableListOf<VisualCandidate>()
        pairs.forEach { visual += VisualCandidate.Complete(it) }
        for (marker in markers) {
            if (memberships.containsKey(marker.detectionId)) continue
            val reason = when {
                marker.role == MarkerRole.OPENING -> ReasonCodes.MISSING_CLOSING_FIDUCIAL
                marker.role == MarkerRole.CLOSING -> ReasonCodes.MISSING_OPENING_FIDUCIAL
                marker.aloneOnLine -> ReasonCodes.BARE_MARK
                else -> ReasonCodes.PARTIAL_FIDUCIAL
            }
            visual += if (reason == ReasonCodes.BARE_MARK) VisualCandidate.Bare(marker) else VisualCandidate.Partial(marker)
            candidateReasons[marker.detectionId] = reason
            reasons += reason
        }
        return Output(markers, pairs, ReadPipeline.readingOrder(visual) { it.box }, candidateReasons, reasons.toList(), failedClosed = false)
    }
}

/** Step 4: the validated interior ROI of one complete pair. */
object InteriorRoi {
    sealed interface Outcome {
        data class Safe(val roi: PayloadRoi) : Outcome

        data class Unsafe(val reason: String) : Outcome
    }

    fun derive(result: RecognitionResult, pairId: String, config: CaptureConfig): Outcome {
        if (!config.isValid) return Outcome.Unsafe(ReasonCodes.INVALID_CONFIGURATION)
        val pair = result.pair(pairId) ?: return Outcome.Unsafe(ReasonCodes.UNSAFE_ROI)
        val open = result.marker(pair.openingDetectionId) ?: return Outcome.Unsafe(ReasonCodes.UNSAFE_ROI)
        val close = result.marker(pair.closingDetectionId) ?: return Outcome.Unsafe(ReasonCodes.UNSAFE_ROI)
        val w = result.image.width.toDouble()
        val h = result.image.height.toDouble()
        val markerHeight = maxOf(open.box.height, close.box.height) * h
        // The interior edge on each side: where the marker's token says the
        // payload starts (past a joining hyphen, or halfway to a glued glyph),
        // so the marker's ink is a whole hyphen away; with nothing glued, the
        // configured inset past the marker's box. Only glyph boxes are used.
        val leftEdge = open.edgeAfter?.let { maxOf(it, open.box.maxX) * w } ?: (open.box.maxX * w + config.roiInset * markerHeight)
        val rightEdge = close.edgeBefore?.let { minOf(it, close.box.minX) * w } ?: (close.box.minX * w - config.roiInset * markerHeight)
        val left = ceil(leftEdge).toInt()
        val right = floor(rightEdge).toInt()
        val top = floor(minOf(open.box.minY, close.box.minY) * h - config.roiVerticalExtent * markerHeight).toInt().coerceAtLeast(0)
        val bottom = ceil(maxOf(open.box.maxY, close.box.maxY) * h + config.roiVerticalExtent * markerHeight).toInt()
            .coerceAtMost(result.image.height)
        val crop = PixelRect(left.coerceAtLeast(0), top, right.coerceAtMost(result.image.width), bottom)
        if (crop.width < config.minRoiPixels || crop.height < config.minRoiPixels) return Outcome.Unsafe(ReasonCodes.UNSAFE_ROI)
        // The ROI never holds another marker: that could hide a competing candidate.
        val others = result.fiducials.filter { it.detectionId != open.detectionId && it.detectionId != close.detectionId }
        if (others.any { overlaps(it.box, crop, result.image) }) return Outcome.Unsafe(ReasonCodes.UNSAFE_ROI)
        if (overlaps(open.box, crop, result.image) || overlaps(close.box, crop, result.image)) return Outcome.Unsafe(ReasonCodes.UNSAFE_ROI)
        val x0 = crop.left / w
        val x1 = crop.right / w
        val y0 = crop.top / h
        val y1 = crop.bottom / h
        return Outcome.Safe(
            PayloadRoi(
                pairId = pair.pairId,
                openingDetectionId = open.detectionId,
                closingDetectionId = close.detectionId,
                baselineId = pair.baselineId,
                polygon = listOf(NormPoint(x0, y0), NormPoint(x1, y0), NormPoint(x1, y1), NormPoint(x0, y1)),
                orientationDegrees = 0.0,
                rectificationTransform = null,
                rectificationSucceeded = true,
                crop = crop,
            ),
        )
    }

    private fun overlaps(box: NormRect, crop: PixelRect, image: PixelSize): Boolean {
        val left = box.minX * image.width
        val right = box.maxX * image.width
        val top = box.minY * image.height
        val bottom = box.maxY * image.height
        return left < crop.right && right > crop.left && top < crop.bottom && bottom > crop.top
    }
}
