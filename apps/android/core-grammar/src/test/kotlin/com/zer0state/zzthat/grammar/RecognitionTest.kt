package com.zer0state.zzthat.grammar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A reader page laid out on a monospace grid: every code point is [CELL]
 * wide and [HEIGHT] tall, so each word and glyph has a box, the way ML Kit
 * gives Element and Symbol boxes. The crop reader keeps the glyphs whose
 * centers fall inside a crop, as a reader shown only that crop would.
 */
object Page {
    val IMAGE = PixelSize(2000, 1000)
    const val CELL = 0.02
    const val HEIGHT = 0.06

    fun line(
        text: String,
        x: Double = 0.1,
        y: Double = 0.1,
        confidence: Double? = 0.95,
        angle: Double? = 0.0,
        glyphs: Boolean = true,
        cell: Double = CELL,
        height: Double = HEIGHT,
    ): RecognizedLine {
        val points = text.codePoints().toArray()
        val boxes = points.indices.map { NormRect(x + it * cell, y, cell, height) }
        val pieces = mutableListOf<RecognizedLine.Piece>()
        var index = 0
        while (index < points.size) {
            if (points[index] == ' '.code) {
                index++
                continue
            }
            val start = index
            while (index < points.size && points[index] != ' '.code) index++
            val range = start until index
            pieces += RecognizedLine.Piece(range, boxes.subList(start, index).reduce(NormRect::union), if (glyphs) boxes.subList(start, index) else emptyList())
        }
        return RecognizedLine(text, confidence, boxes.reduce(NormRect::union), pieces, angle)
    }

    /** What a reader returns for [crop] of a page: the glyphs inside it, regrouped by line, normalized over the crop. */
    fun readCrop(page: List<RecognizedLine>, crop: PixelRect, image: PixelSize = IMAGE): List<RecognizedLine> = page.mapNotNull { line ->
        val points = line.text.codePoints().toArray()
        val glyphBoxes = line.pieces.flatMap { piece -> piece.range.zip(piece.glyphs) }.toMap()
        val kept = points.indices.filter { index ->
            val box = glyphBoxes[index] ?: return@filter false
            val cx = (box.minX + box.width / 2) * image.width
            val cy = (box.minY + box.height / 2) * image.height
            cx > crop.left && cx < crop.right && cy > crop.top && cy < crop.bottom
        }
        if (kept.isEmpty()) return@mapNotNull null
        val text = String(points, kept.first(), kept.last() - kept.first() + 1).trim()
        if (text.isEmpty()) return@mapNotNull null
        val box = kept.mapNotNull { glyphBoxes[it] }.reduce(NormRect::union)
        // A reader shown only the crop boxes only what lies inside it.
        val left = ((box.minX * image.width - crop.left) / crop.width).coerceIn(0.0, 1.0)
        val right = ((box.maxX * image.width - crop.left) / crop.width).coerceIn(0.0, 1.0)
        val top = ((box.minY * image.height - crop.top) / crop.height).coerceIn(0.0, 1.0)
        val bottom = ((box.maxY * image.height - crop.top) / crop.height).coerceIn(0.0, 1.0)
        RecognizedLine(
            text = text,
            confidence = line.confidence,
            box = NormRect(left, top, right - left, bottom - top),
            angleDegrees = line.angleDegrees,
        )
    }

    fun detect(vararg lines: RecognizedLine, config: CaptureConfig = CaptureConfig.DEFAULT, unlocalized: Int = 0): RecognitionResult =
        CameraRead.detect("capture-1", "fixture-engine", "0.0.0", IMAGE, Upright.NONE, lines.toList(), config, unlocalized)

    /** The whole runtime sequence for one pair over a page. */
    fun readPair(page: List<RecognizedLine>, result: RecognitionResult, pairId: String, pipeline: ReadPipeline): Pair<RecognitionResult, PairRead> =
        when (val roi = CameraRead.roi(result, pairId, pipeline.capture)) {
            is InteriorRoi.Outcome.Unsafe -> result to CameraRead.decode(result, pairId, pipeline, roi.reason)
            is InteriorRoi.Outcome.Safe -> {
                val withText = CameraRead.withPayload(result, roi.roi, listOf(readCrop(page, roi.roi.crop)))
                withText to CameraRead.decode(withText, pairId, pipeline)
            }
        }
}

/** recognition-adapter.md steps 2 and 3: marker observations, same-baseline pairing, and selection before OCR (T023). */
class PairingTest {
    @Test
    fun aHyphenatedCodeGivesTwoBoxedMarkersAndOneCompletePair() {
        val result = Page.detect(Page.line("zz-copper-lantern-sky-zz"))
        assertEquals(listOf(MarkerRole.OPENING, MarkerRole.CLOSING), result.fiducials.map { it.role })
        // The interior starts past the joining hyphen and ends before the other one (glyph geometry only).
        assertEquals(listOf(0.16, null), result.fiducials.map { it.edgeAfter?.let { x -> Math.round(x * 1e9) / 1e9 } })
        assertEquals(listOf(null, 0.52), result.fiducials.map { it.edgeBefore?.let { x -> Math.round(x * 1e9) / 1e9 } })
        // Each marker is boxed by its own two glyphs, not by the whole word.
        assertEquals(NormRect(0.1, 0.1, 0.04, 0.06), result.fiducials[0].box.rounded())
        assertEquals(NormRect(0.54, 0.1, 0.04, 0.06), result.fiducials[1].box.rounded())
        assertEquals(1, result.pairs.size)
        val pair = result.pairs.single()
        assertEquals(listOf("p1", "p1"), result.fiducials.map { it.pairId })
        assertEquals(listOf("b1", "b1"), result.fiducials.map { it.baselineId })
        assertEquals(pair, result.soleCompletePair)
        assertFalse(result.failedClosed)
        assertTrue(result.reasonCodes.isEmpty())
    }

    @Test
    fun separateCircledAndHandleMarkersAreFound() {
        val circled = Page.detect(Page.line("(zz) camp bravo (zz)"))
        assertEquals(listOf("(zz)", "(zz)"), circled.fiducials.map { it.literal })
        assertEquals(listOf(MarkerRole.UNKNOWN, MarkerRole.UNKNOWN), circled.fiducials.map { it.role })
        assertNotNull(circled.soleCompletePair)
        val glued = Page.detect(Page.line("(zz)camp bravo(zz)"))
        assertEquals(listOf(MarkerRole.OPENING, MarkerRole.CLOSING), glued.fiducials.map { it.role })
        val handle = Page.detect(Page.line("zz@agentsmith-zz"))
        assertEquals(listOf("zz", "zz"), handle.fiducials.map { it.literal })
        assertEquals(listOf(MarkerRole.OPENING, MarkerRole.CLOSING), handle.fiducials.map { it.role })
        // The mark of a zz@ opener is its zz; the @ is payload (zz-@agentsmith-zz).
        assertEquals(NormRect(0.1, 0.1, 0.04, 0.06), handle.fiducials[0].box.rounded())
        // A zz@ that stands alone still only opens.
        assertEquals(MarkerRole.OPENING, Page.detect(Page.line("zz@ agentsmith zz")).fiducials[0].role)
    }

    @Test
    fun wordsThatHoldZzAreNotMarkers() {
        val result = Page.detect(Page.line("the buzz pizza zzz word"))
        assertTrue(result.fiducials.isEmpty())
        assertEquals(listOf(ReasonCodes.NO_FIDUCIAL), result.reasonCodes)
        assertTrue(result.visualCandidates.isEmpty())
    }

    @Test
    fun anOpeningMarkerAloneIsAPartialCandidateAndNoPair() {
        val result = Page.detect(Page.line("label zz-copper-lantern"))
        assertTrue(result.pairs.isEmpty())
        val partial = result.visualCandidates.single() as VisualCandidate.Partial
        assertEquals(MarkerRole.OPENING, partial.marker.role)
        assertNull(partial.marker.pairId)
        assertEquals(listOf(ReasonCodes.MISSING_CLOSING_FIDUCIAL), result.reasonCodes)
        assertNull(result.soleCompletePair)
    }

    /** A lone mark with words on both sides: a partial candidate, reported as the replay script reads it. */
    @Test
    fun aMarkBetweenWordsIsAPartialFiducial() {
        val result = Page.detect(Page.line("see zz here"))
        val partial = result.visualCandidates.single() as VisualCandidate.Partial
        assertEquals(MarkerRole.UNKNOWN, partial.marker.role)
        assertEquals(listOf(ReasonCodes.PARTIAL_FIDUCIAL), result.reasonCodes)
        assertEquals(Band.RETRY, CaptureConfig.DEFAULT.bandFor(ReasonCodes.PARTIAL_FIDUCIAL))
    }

    @Test
    fun aLoneMarkIsABareCandidate() {
        val result = Page.detect(Page.line("zz"))
        assertTrue(result.visualCandidates.single() is VisualCandidate.Bare)
        assertEquals(listOf(ReasonCodes.BARE_MARK), result.reasonCodes)
        assertNull(result.soleCompletePair)
    }

    /** A complete pair next to a partial one still waits for the person (step 3). */
    @Test
    fun aCompletePairBesideAPartialWaitsForSelection() {
        val result = Page.detect(Page.line("zz-copper-lantern-sky-zz"), Page.line("see zz-river", y = 0.4))
        assertEquals(2, result.visualCandidates.size)
        assertTrue(result.visualCandidates[0] is VisualCandidate.Complete)
        assertTrue(result.visualCandidates[1] is VisualCandidate.Partial)
        assertNull("more than one selectable candidate never proceeds without a tap", result.soleCompletePair)
    }

    /** Three markers on one baseline: both plausible pairs stay, and the shared marker has no single pair id. */
    @Test
    fun ambiguousPairingKeepsEveryPlausiblePair() {
        val result = Page.detect(Page.line("zz copper zz lantern zz"))
        assertEquals(3, result.fiducials.size)
        assertEquals(listOf("m1" to "m2", "m2" to "m3"), result.pairs.map { it.openingDetectionId to it.closingDetectionId })
        assertEquals(listOf("p1", null, "p2"), result.fiducials.map { it.pairId })
        assertEquals(2, result.visualCandidates.count { it is VisualCandidate.Complete })
        assertNull(result.soleCompletePair)
    }

    /** Two codes in reading order, each its own pair. */
    @Test
    fun twoCodesAreTwoCandidatesInReadingOrder() {
        val result = Page.detect(Page.line("zz-b2-4-zz", y = 0.5), Page.line("zz-river-falcon-sky-zz", y = 0.1))
        assertEquals(listOf("b1", "b1", "b2", "b2"), result.fiducials.map { it.baselineId })
        assertEquals(2, result.pairs.size)
        assertTrue(result.pairs[0].box.minY < result.pairs[1].box.minY)
    }

    /** FR-009 as narrowed for v1: markers on two baselines are never paired, so a code wrapped across lines fails closed to Retry. */
    @Test
    fun markersOnDifferentBaselinesDoNotPair() {
        val result = Page.detect(Page.line("zz-copper-lantern"), Page.line("sky-zz", y = 0.25))
        assertTrue(result.pairs.isEmpty())
        assertEquals(2, result.visualCandidates.count { it is VisualCandidate.Partial })
        assertEquals(listOf(ReasonCodes.MISSING_CLOSING_FIDUCIAL, ReasonCodes.MISSING_OPENING_FIDUCIAL), result.reasonCodes)
        assertEquals(listOf(null, null), result.fiducials.map { it.baselineId })
    }

    @Test
    fun baselineToleranceAndHeightRatioGatePairs() {
        val config = CaptureConfig.DEFAULT
        val image = Page.IMAGE
        fun marker(y: Double, height: Double) = Page.line("zz", y = y, height = height).let {
            CameraRead.detect("c", "e", "v", image, Upright.NONE, listOf(it), config).fiducials.single()
        }
        val base = marker(0.1, 0.06)
        // 60 px tall: the tolerance is 0.35 * 60 = 21 px of bottom-edge offset.
        assertTrue(MarkerPairing.sameBaseline(base, marker(0.12, 0.06), image, config))
        assertFalse(MarkerPairing.sameBaseline(base, marker(0.125, 0.06), image, config))
        // Same bottom edge, but more than twice as tall.
        assertFalse(MarkerPairing.sameBaseline(base, marker(0.03, 0.13), image, config))
    }

    @Test
    fun overflowFailsTheWholeFrameClosed() {
        val five = Page.line("zz a zz b zz c zz d zz")
        val markers = Page.detect(five, config = CaptureConfig.DEFAULT.copy(maxMarkers = 4))
        assertTrue(markers.failedClosed)
        assertEquals(listOf(ReasonCodes.MARKER_LIMIT_EXCEEDED), markers.reasonCodes)
        assertTrue("no candidate is truncated or ranked away: none is offered", markers.visualCandidates.isEmpty())
        assertEquals("the evidence keeps every observation", 5, markers.fiducials.size)
        val pairs = Page.detect(five, config = CaptureConfig.DEFAULT.copy(maxPairs = 3))
        assertTrue(pairs.failedClosed)
        assertEquals(listOf(ReasonCodes.PAIR_LIMIT_EXCEEDED), pairs.reasonCodes)
        assertTrue(pairs.pairs.isEmpty())
        // Within the limits the same page offers every pair.
        assertEquals(4, Page.detect(five).pairs.size)
    }

    @Test
    fun skewedOrUnmeasuredGeometryFailsClosed() {
        for (angle in listOf(25.0, -11.0, null, Double.NaN)) {
            val result = Page.detect(Page.line("zz-copper-lantern-sky-zz", angle = angle))
            assertTrue("angle $angle", result.failedClosed)
            assertEquals(listOf(ReasonCodes.UNSUPPORTED_GEOMETRY), result.reasonCodes)
        }
    }

    @Test
    fun aMarkerTheReaderDidNotBoxFailsTheFrameClosed() {
        // No glyph boxes: the markers share their word with payload, so their own patches are unknown.
        val glued = Page.detect(Page.line("zz-copper-lantern-sky-zz", glyphs = false))
        assertTrue(glued.failedClosed)
        assertEquals(listOf(ReasonCodes.UNLOCALIZED_MARKER), glued.reasonCodes)
        // Standalone markers are their own words, so the word box is the marker box.
        val apart = Page.detect(Page.line("zz copper lantern sky zz", glyphs = false))
        assertFalse(apart.failedClosed)
        assertNotNull(apart.soleCompletePair)
        // A reader line with no box could hold a marker.
        val dropped = Page.detect(Page.line("zz copper lantern sky zz"), unlocalized = 1)
        assertTrue(dropped.failedClosed)
    }

    @Test
    fun overlappingMarkersOnOneBaselineFailClosed() {
        val result = Page.detect(Page.line("zz copper"), Page.line("zz", x = 0.11))
        assertTrue(result.failedClosed)
        assertEquals(listOf(ReasonCodes.UNSUPPORTED_GEOMETRY), result.reasonCodes)
    }
}

/** recognition-adapter.md step 4: the validated interior ROI, crop only, no rectification (T023). */
class InteriorRoiTest {
    @Test
    fun theRoiExcludesBothMarkerPatches() {
        val result = Page.detect(Page.line("zz-copper-lantern-sky-zz"))
        val roi = (CameraRead.roi(result, "p1", CaptureConfig.DEFAULT) as InteriorRoi.Outcome.Safe).roi
        // The joining hyphens (280 to 320 px and 1040 to 1080 px) stay outside: the edges sit between each hyphen and the payload letter beside it.
        assertEquals(PixelRect(320, 64, 1040, 196), roi.crop)
        assertEquals(listOf("p1", "m1", "m2", "b1"), listOf(roi.pairId, roi.openingDetectionId, roi.closingDetectionId, roi.baselineId))
        assertEquals(4, roi.polygon.size)
        assertTrue(roi.polygon.all { it.x in 0.0..1.0 && it.y in 0.0..1.0 })
        assertEquals(0.0, roi.orientationDegrees, 0.0)
        assertNull(roi.rectificationTransform)
        assertTrue(roi.rectificationSucceeded)
    }

    @Test
    fun noPerspectiveTransformCanBeRecorded() {
        assertThrows(IllegalArgumentException::class.java) {
            PayloadRoi("p1", "m1", "m2", "b1", emptyList(), 0.0, List(9) { 1.0 }, true, PixelRect(0, 0, 1, 1))
        }
    }

    @Test
    fun aTinyInteriorIsUnsafe() {
        val result = Page.detect(Page.line("zz zz"))
        assertEquals(InteriorRoi.Outcome.Unsafe(ReasonCodes.UNSAFE_ROI), CameraRead.roi(result, "p1", CaptureConfig.DEFAULT))
    }

    @Test
    fun anotherMarkerInsideTheRoiIsUnsafe() {
        // A small zz on its own line sits inside the pair's vertical extent.
        val result = Page.detect(Page.line("zz copper lantern sky zz", y = 0.2), Page.line("zz", x = 0.3, y = 0.15, height = 0.02, cell = 0.01))
        assertNotNull(result.pair("p1"))
        assertEquals(InteriorRoi.Outcome.Unsafe(ReasonCodes.UNSAFE_ROI), CameraRead.roi(result, "p1", CaptureConfig.DEFAULT))
    }

    @Test
    fun aFrameThatFailedClosedHasNoRoi() {
        val result = Page.detect(Page.line("zz a zz b zz c zz d zz"), config = CaptureConfig.DEFAULT.copy(maxPairs = 1))
        assertTrue(CameraRead.roi(result, "p1", CaptureConfig.DEFAULT) is InteriorRoi.Outcome.Unsafe)
    }
}

/** recognition-adapter.md steps 5 to 7: ROI text only, literal markers rebuilt, shared decode, hypothesis order (T022, T023, T102). */
class CameraDecodeTest {
    private val list = Vectors.fixture7
    private val table = CaptureConfig.DEFAULT.copy(confirmOnly = false)

    private fun read(vararg page: RecognizedLine, capture: CaptureConfig = table): PairRead {
        val result = Page.detect(*page, config = capture)
        val pair = requireNotNull(result.soleCompletePair) { "fixture has one complete pair" }
        return Page.readPair(page.toList(), result, pair.pairId, ReadPipeline(list, list, capture)).second
    }

    @Test
    fun theReaderSeesOnlyTheInteriorAndTheMarkersAreRebuilt() {
        val page = listOf(Page.line("zz-copper-lantern-sky-zz"))
        val result = Page.detect(*page.toTypedArray())
        val (withText, read) = Page.readPair(page, result, "p1", ReadPipeline(list, list, table))
        val hypothesis = withText.candidates.single()
        assertEquals("the payload holds no marker and no joining hyphen", "copper-lantern-sky", hypothesis.rawText)
        assertEquals("p1", hypothesis.pairId)
        assertEquals("p1/h1", hypothesis.candidateId)
        assertEquals(Confidence.Available(0.95), hypothesis.confidence)
        assertEquals(listOf("zz-copper-lantern-sky-zz"), read.candidates.map { it.canonical })
        assertEquals(Band.ACCEPT, read.candidates.single().band)
        // Regions come back in source coordinates, inside the ROI.
        val roi = withText.rois.single()
        val eps = 1e-9
        assertTrue(hypothesis.regions.all { it.minX >= roi.polygon[0].x - eps && it.maxX <= roi.polygon[1].x + eps })
    }

    /** Step 6 as zzThis zz-core decideCaptureBand does it: `zz-<payload>-zz`, whatever separator the photo had. */
    @Test
    fun theLiteralMarkersAreRebuiltAsZzCoreDoes() {
        assertEquals("zz-copper-lantern-sky-zz", CameraRead.literal("copper-lantern-sky"))
        for (payload in listOf("copper-lantern-sky", "-copper-lantern-sky-", "copper lantern sky", "copper-lantern-sky--")) {
            assertEquals(payload, "zz-copper-lantern-sky-zz", Grammar.parse(CameraRead.literal(payload)).codeOrNull()?.canonical)
        }
        assertEquals("zz-@agentsmith-zz", Grammar.parse(CameraRead.literal("@agentsmith")).codeOrNull()?.canonical)
    }

    @Test
    fun everyMarkerFormDecodesToTheSameCanonicalCode() {
        for (text in listOf("zz-copper-lantern-sky-zz", "zz copper lantern sky zz", "ZZ COPPER LANTERN SKY ZZ", "(zz) copper lantern sky (zz)", "(zz)copper lantern sky(zz)")) {
            assertEquals(text, listOf("zz-copper-lantern-sky-zz"), read(Page.line(text)).candidates.map { it.canonical })
        }
        assertEquals(listOf("zz-@agentsmith-zz"), read(Page.line("zz@agentsmith-zz")).candidates.map { it.canonical })
        assertEquals(listOf("zz-@agentsmith-zz"), read(Page.line("zz@ agentsmith zz")).candidates.map { it.canonical })
    }

    /** FR-009: a flat two-line payload reads as one code when the markers form one same-baseline pair. */
    @Test
    fun aFlatTwoLinePayloadBetweenTallMarkersIsOneCode() {
        // Tall markers (0.16 high) on a baseline at 0.36; the payload sits in two short lines between them.
        val open = Page.line("zz", x = 0.1, y = 0.2, height = 0.16, cell = 0.03)
        val close = Page.line("zz", x = 0.8, y = 0.2, height = 0.16, cell = 0.03)
        val top = Page.line("copper lantern", x = 0.25, y = 0.21, confidence = 0.9, height = 0.06)
        val bottom = Page.line("sky", x = 0.25, y = 0.29, confidence = 0.7, height = 0.06)
        val page = listOf(open, top, bottom, close)
        val result = Page.detect(*page.toTypedArray())
        val pair = requireNotNull(result.soleCompletePair)
        val (withText, read) = Page.readPair(page, result, pair.pairId, ReadPipeline(list, list, table))
        assertEquals("copper lantern sky", withText.candidates.single().rawText)
        val candidate = read.candidates.single()
        assertEquals("zz-copper-lantern-sky-zz", candidate.canonical)
        assertEquals(listOf(0, 0, 1), candidate.rows.map { it.lineIndex })
        assertEquals("the lowest line decides", Confidence.Available(0.7), Bands.candidateConfidence(candidate.rows))
        assertEquals(Band.CLARIFY, candidate.band)
    }

    @Test
    fun missingConfidenceStaysMissingAndClarifies() {
        val read = read(Page.line("zz-copper-lantern-sky-zz", confidence = null))
        val candidate = read.candidates.single()
        assertEquals(listOf(null, null, null), candidate.rows.map { it.lineConfidence })
        assertEquals(Confidence.NotAvailable, Bands.candidateConfidence(candidate.rows))
        assertEquals("never Accept and never Retry from an invented 0", Band.CLARIFY, candidate.band)
    }

    @Test
    fun anRoiWithNoTextRetries() {
        val page = Page.line("zz-copper-lantern-sky-zz")
        val result = Page.detect(page)
        val roi = (CameraRead.roi(result, "p1", table) as InteriorRoi.Outcome.Safe).roi
        val empty = CameraRead.withPayload(result, roi, listOf(emptyList(), listOf(RecognizedLine("  ", 0.9, NormRect.ZERO))))
        assertTrue(empty.candidates.isEmpty())
        val read = CameraRead.decode(empty, "p1", ReadPipeline(list, list, table))
        assertTrue(read.candidates.isEmpty())
        assertEquals(listOf(ReasonCodes.NO_PAYLOAD_TEXT), read.reasonCodes)
        assertEquals(Band.RETRY, read.emptyBand)
    }

    @Test
    fun anUnsafeRoiNeverDecodes() {
        val page = listOf(Page.line("zz zz"))
        val result = Page.detect(*page.toTypedArray())
        val (_, read) = Page.readPair(page, result, "p1", ReadPipeline(list, list, table))
        assertTrue(read.candidates.isEmpty())
        assertEquals(listOf(ReasonCodes.UNSAFE_ROI), read.reasonCodes)
        assertEquals(Band.RETRY, read.emptyBand)
    }

    /** Step 7: several hypotheses keep the engine's order and all go to Clarify, even a better-scoring valid one. */
    @Test
    fun severalHypothesesKeepTheirOrderAndClarify() {
        val result = Page.detect(Page.line("zz-copper-lantern-sky-zz"))
        val roi = (CameraRead.roi(result, "p1", table) as InteriorRoi.Outcome.Safe).roi
        val readings = listOf(
            listOf(RecognizedLine("coper-lantern-sky", 0.55, NormRect(0.1, 0.3, 0.8, 0.4))),
            listOf(RecognizedLine("copper-lantern-sky", 0.99, NormRect(0.1, 0.3, 0.8, 0.4))),
            listOf(RecognizedLine("copper-#tag-sky", 0.98, NormRect(0.1, 0.3, 0.8, 0.4))),
        )
        val withText = CameraRead.withPayload(result, roi, readings)
        assertEquals(listOf("p1/h1", "p1/h2", "p1/h3"), withText.candidates.map { it.candidateId })
        assertEquals(listOf("coper-lantern-sky", "copper-lantern-sky", "copper-#tag-sky"), withText.candidates.map { it.rawText })
        val read = CameraRead.decode(withText, "p1", ReadPipeline(list, list, table))
        assertEquals(listOf("zz-coper-lantern-sky-zz", "zz-copper-lantern-sky-zz", null), read.candidates.map { it.canonical })
        assertEquals(listOf(Band.CLARIFY, Band.CLARIFY, Band.CLARIFY), read.candidates.map { it.band })
        assertNull(read.autoOpen)
    }

    @Test
    fun tooManyHypothesesAbstain() {
        val result = Page.detect(Page.line("zz-copper-lantern-sky-zz"))
        val roi = (CameraRead.roi(result, "p1", table) as InteriorRoi.Outcome.Safe).roi
        val readings = List(5) { listOf(RecognizedLine("copper-lantern-sky", 0.99, NormRect(0.1, 0.3, 0.8, 0.4))) }
        val read = CameraRead.decode(CameraRead.withPayload(result, roi, readings), "p1", ReadPipeline(list, list, table))
        assertTrue(read.candidates.isEmpty())
        assertEquals(listOf(ReasonCodes.HYPOTHESIS_LIMIT_EXCEEDED), read.reasonCodes)
        assertEquals(Band.ABSTAIN, read.emptyBand)
    }

    @Test
    fun aMarkerInsideThePayloadAbstains() {
        val result = Page.detect(Page.line("zz-copper-lantern-sky-zz"))
        val roi = (CameraRead.roi(result, "p1", table) as InteriorRoi.Outcome.Safe).roi
        val withText = CameraRead.withPayload(result, roi, listOf(listOf(RecognizedLine("copper zz lantern", 0.99, NormRect(0.1, 0.3, 0.8, 0.4)))))
        val read = CameraRead.decode(withText, "p1", ReadPipeline(list, list, table))
        val candidate = read.candidates.single()
        assertTrue(candidate.kind is ScanKind.Invalid)
        assertEquals(Band.ABSTAIN, candidate.band)
    }

    @Test
    fun confirmOnlyTurnsTheCameraAcceptIntoClarify() {
        val read = read(Page.line("zz-copper-lantern-sky-zz"), capture = CaptureConfig.DEFAULT)
        val candidate = read.candidates.single()
        assertEquals(Band.CLARIFY, candidate.band)
        assertTrue(candidate.confirmOnly)
        assertEquals("the full canonical code is kept for the confirm step", "zz-copper-lantern-sky-zz", candidate.canonical)
        assertNull(read.autoOpen)
    }

    @Test
    fun theReadDoesNotDependOnOtherCandidatesReasons() {
        // A partial elsewhere on the photo does not send the selected pair to Retry.
        val page = listOf(Page.line("zz-copper-lantern-sky-zz"), Page.line("see zz-river", y = 0.4))
        val result = Page.detect(*page.toTypedArray())
        assertEquals(listOf(ReasonCodes.MISSING_CLOSING_FIDUCIAL), result.reasonCodes)
        val (_, read) = Page.readPair(page, result, "p1", ReadPipeline(list, list, table))
        assertEquals(Band.ACCEPT, read.candidates.single().band)
        assertTrue(read.reasonCodes.isEmpty())
    }

    @Test
    fun aFailedRecognizerAbstainsWithNoOutput() {
        val failed = CameraRead.recognizerFailed("c", "e", "v", Page.IMAGE, Upright.NONE, CaptureConfig.DEFAULT)
        assertEquals(RuntimeOutcome.EXCEPTION, failed.runtimeOutcome)
        assertTrue(failed.failedClosed)
        assertTrue(failed.visualCandidates.isEmpty())
        assertEquals(Band.ABSTAIN, Bands.band(null, null, null, emptyList(), ReadSource.CAMERA, CaptureEvidence(failed.reasonCodes)))
    }
}

/** The pinned adapter-result schema shape, field for field (recognition.md, Boundary; T023). */
class RecognitionSchemaTest {
    @Test
    fun theEvidenceUsesTheSchemaKeys() {
        val page = listOf(Page.line("zz-copper-lantern-sky-zz", confidence = null))
        val result = Page.detect(*page.toTypedArray())
        val (withText, _) = Page.readPair(page, result, "p1", ReadPipeline(null, null))
        val fields = withText.schemaFields()
        assertEquals(setOf("runtime_outcome", "fiducials", "rois", "candidates", "reason_codes"), fields.keys)
        @Suppress("UNCHECKED_CAST")
        fun list(key: String) = fields.getValue(key) as List<Map<String, Any?>>
        assertTrue(list("fiducials").all { it.keys.containsAll(setOf("detection_id", "baseline_id", "role", "box")) })
        assertTrue(list("fiducials").all { it["role"] in setOf("opening", "closing", "unknown") })
        assertTrue("ML Kit gives no meaningful marker score, so none is written", list("fiducials").none { it.containsKey("score") })
        val roi = list("rois").single()
        assertTrue(roi.keys.containsAll(setOf("pair_id", "opening_detection_id", "closing_detection_id", "baseline_id", "polygon")))
        assertFalse("v1 records no rectification transform", roi.containsKey("rectification_transform"))
        val candidate = list("candidates").single()
        assertEquals(setOf("candidate_id", "pair_id", "raw_text", "confidence_status", "confidence", "regions"), candidate.keys)
        assertEquals("not-available", candidate["confidence_status"])
        assertNull("missing confidence is null, never a number", candidate["confidence"])
        assertEquals("p1", candidate["pair_id"])
    }

    @Test
    fun confidenceIsInRangeOrNotAvailable() {
        assertThrows(IllegalArgumentException::class.java) { Confidence.Available(1.2) }
        assertThrows(IllegalArgumentException::class.java) { Confidence.Available(Double.NaN) }
        assertEquals(Confidence.NotAvailable, Confidence.minimum(listOf(0.9, null)))
        assertEquals(Confidence.NotAvailable, Confidence.minimum(emptyList()))
        assertEquals(Confidence.Available(0.4), Confidence.minimum(listOf(0.9, 0.4)))
    }

    @Test
    fun aHypothesisWithoutAnRoiIsRejected() {
        val result = Page.detect(Page.line("zz-copper-lantern-sky-zz"))
        assertThrows(IllegalArgumentException::class.java) {
            result.copy(candidates = listOf(TextHypothesis("p1/h1", "p1", "copper", Confidence.NotAvailable, emptyList(), emptyList())))
        }
    }

    /** No image, crop, transcript, or candidate code is written, logged, or printed from the recognition code (FR-004, FR-005). */
    @Test
    fun theRecognitionFilesPersistNothing() {
        val forbidden = listOf("java.io", "java.nio", "println", "System.out", "System.err", "Logger", "printStackTrace")
        for (name in listOf("RecognitionResult.kt", "MarkerGeometry.kt", "CameraRead.kt", "Bands.kt", "Parameters.kt")) {
            val source = File("src/main/kotlin/com/zer0state/zzthat/grammar/$name").readText()
            for (word in forbidden) assertFalse("$name uses $word", source.contains(word))
        }
    }
}

/** The one config file (T022): prototype values, not qualified, confirm-only on (T102), and fail-closed validation. */
class CaptureConfigTest {
    @Test
    fun theDefaultIsTheUnqualifiedConfirmOnlyPrototype() {
        val config = CaptureConfig.DEFAULT
        assertFalse(config.qualified)
        assertTrue(config.confirmOnly)
        assertTrue(config.version.contains("unqualified"))
        assertEquals(BandThresholds.ACCEPT, config.acceptMinConfidence, 0.0)
        assertEquals(BandThresholds.RETRY_BELOW, config.retryBelowConfidence, 0.0)
        assertTrue(config.isValid)
    }

    @Test
    fun anUnknownReasonCodeAbstains() {
        assertEquals(Band.ABSTAIN, CaptureConfig.DEFAULT.bandFor("something-new"))
        assertEquals(Band.RETRY, CaptureConfig.DEFAULT.bandFor("blur"))
        assertEquals(Band.ABSTAIN, CaptureConfig.DEFAULT.bandFor("bare-mark"))
    }

    @Test
    fun invalidThresholdsOrLimitsFailClosed() {
        val list = Vectors.fixture7
        val code = Grammar.parse("zz-copper-lantern-sky-zz").codeOrNull()!!
        val rows = listOf(BandRow("copper", 0, 0.99), BandRow("lantern", 0, 0.99), BandRow("sky", 0, 0.99))
        val check = CheckWord.verify(code, list)
        for (bad in listOf(
            CaptureConfig.DEFAULT.copy(confirmOnly = false, retryBelowConfidence = 0.9, acceptMinConfidence = 0.8),
            CaptureConfig.DEFAULT.copy(confirmOnly = false, acceptMinConfidence = Double.NaN),
            CaptureConfig.DEFAULT.copy(confirmOnly = false, maxHypotheses = 0),
        )) {
            assertFalse(bad.isValid)
            assertEquals(Band.ABSTAIN, Bands.band(ScanKind.Code(code), CodeClass.Word, check, rows, ReadSource.CAMERA, config = bad))
        }
        val result = Page.detect(Page.line("zz-copper-lantern-sky-zz"), config = CaptureConfig.DEFAULT.copy(maxMarkers = 0))
        assertTrue(result.failedClosed)
        assertEquals(listOf(ReasonCodes.INVALID_CONFIGURATION), result.reasonCodes)
    }
}

private fun NormRect.rounded(): NormRect {
    fun r(value: Double) = Math.round(value * 1e9) / 1e9
    return NormRect(r(x), r(y), r(width), r(height))
}

/** The ROI edge past a joining hyphen, and the inset when nothing is glued to the marker. */
class RoiEdgeTest {
    @Test
    fun theEdgeSitsPastTheJoiningHyphen() {
        // Glyphs 40 px wide with a 10 px gap between every glyph.
        val gapped = Page.line("zz-copper-zz", cell = 0.02).let { line ->
            line.copy(pieces = line.pieces.map { piece -> piece.copy(glyphs = piece.glyphs.map { it.copy(width = 0.015) }) })
        }
        val result = Page.detect(gapped)
        val roi = (CameraRead.roi(result, "p1", CaptureConfig.DEFAULT) as InteriorRoi.Outcome.Safe).roi
        // The joining hyphen ends at 0.14 + 0.015 = 0.155 (310 px); the c starts at 320 px: halfway is 315 px.
        assertTrue("left ${roi.crop.left}", roi.crop.left in 315..316)
        // The r ends at 0.26 + 0.015 = 0.275 (550 px); the hyphen before the closing marker starts at 560 px.
        assertTrue("right ${roi.crop.right}", roi.crop.right in 554..555)
    }

    @Test
    fun aStandaloneMarkerUsesTheInset() {
        val result = Page.detect(Page.line("zz copper zz"))
        val roi = (CameraRead.roi(result, "p1", CaptureConfig.DEFAULT) as InteriorRoi.Outcome.Safe).roi
        // Opening ends at 280 px; 0.25 * 60 px inset is 15 px. Closing starts at 0.1 + 0.2 = 0.3 (600 px).
        assertEquals(295, roi.crop.left)
        assertEquals(585, roi.crop.right)
    }
}
