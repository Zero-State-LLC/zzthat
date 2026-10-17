package com.zer0state.zzthat

import com.zer0state.zzthat.grammar.CameraRead
import com.zer0state.zzthat.grammar.InteriorRoi
import com.zer0state.zzthat.grammar.NormRect
import com.zer0state.zzthat.grammar.PixelRect
import com.zer0state.zzthat.grammar.PixelSize
import com.zer0state.zzthat.grammar.ReadPipeline
import com.zer0state.zzthat.grammar.RecognizedLine
import com.zer0state.zzthat.grammar.Upright
import com.zer0state.zzthat.scan.CaptureImage
import com.zer0state.zzthat.scan.ReaderOutput
import com.zer0state.zzthat.scan.ScanRow
import com.zer0state.zzthat.scan.TextRecognizer

/**
 * Reader fixtures for the camera flow. A page is a list of reader lines over
 * the source image with one box per glyph, as ML Kit gives Element and
 * Symbol boxes. A reader shown a crop sees only the glyphs whose centers
 * fall inside it.
 */
object CaptureFixtures {
    val IMAGE = PixelSize(2000, 1000)

    /** One line on a monospace grid. */
    fun line(text: String, x: Double = 0.1, y: Double = 0.1, confidence: Double? = 0.95, cell: Double = 0.02, height: Double = 0.06): RecognizedLine {
        val boxes = text.indices.map { NormRect(x + it * cell, y, cell, height) }
        val pieces = Regex("\\S+").findAll(text).map { match ->
            val range = match.range
            RecognizedLine.Piece(range, boxes.subList(range.first, range.last + 1).reduce(NormRect::union), boxes.subList(range.first, range.last + 1))
        }.toList()
        return RecognizedLine(text, confidence, boxes.reduce(NormRect::union), pieces, angleDegrees = 0.0)
    }

    /** What a reader returns for [crop] of [page], normalized over the crop. */
    fun readCrop(page: List<RecognizedLine>, crop: PixelRect, image: PixelSize = IMAGE): List<RecognizedLine> = page.mapNotNull { line ->
        val glyphs = line.pieces.flatMap { piece -> piece.range.zip(piece.glyphs) }.toMap()
        val kept = line.text.indices.filter { index ->
            val box = glyphs[index] ?: return@filter false
            val cx = (box.minX + box.width / 2) * image.width
            val cy = (box.minY + box.height / 2) * image.height
            cx > crop.left && cx < crop.right && cy > crop.top && cy < crop.bottom
        }
        if (kept.isEmpty()) return@mapNotNull null
        val text = line.text.substring(kept.first(), kept.last() + 1).trim()
        if (text.isEmpty()) return@mapNotNull null
        val box = kept.mapNotNull { glyphs[it] }.reduce(NormRect::union)
        val left = ((box.minX * image.width - crop.left) / crop.width).coerceIn(0.0, 1.0)
        val right = ((box.maxX * image.width - crop.left) / crop.width).coerceIn(0.0, 1.0)
        val top = ((box.minY * image.height - crop.top) / crop.height).coerceIn(0.0, 1.0)
        val bottom = ((box.maxY * image.height - crop.top) / crop.height).coerceIn(0.0, 1.0)
        RecognizedLine(text, line.confidence, NormRect(left, top, right - left, bottom - top), angleDegrees = 0.0)
    }

    /**
     * The rows the Scan screen shows for [page]: every visual candidate, and
     * when [readSole] is set and one complete pair is the only candidate, its
     * decoded reading in its place, as the runtime sequence produces them.
     */
    fun rows(page: List<RecognizedLine>, image: PixelSize, pipeline: ReadPipeline, readSole: Boolean = true): List<ScanRow> {
        val result = CameraRead.detect("fixture", "fixture", "0", image, Upright.NONE, page, pipeline.capture)
        val sole = result.soleCompletePair
        val unread = result.visualCandidates.map(ScanRow::Unread)
        if (!readSole || sole == null) return unread
        val roi = (CameraRead.roi(result, sole.pairId, pipeline.capture) as? InteriorRoi.Outcome.Safe)?.roi ?: return unread
        val read = CameraRead.decode(CameraRead.withPayload(result, roi, listOf(readCrop(page, roi.crop, image))), sole.pairId, pipeline)
        return read.candidates.map { ScanRow.Read(sole.pairId, it, sole.box) }.ifEmpty { unread }
    }
}

/** An image with no pixels: it knows its size and which source region it shows. */
class FakeImage(
    override val size: PixelSize = CaptureFixtures.IMAGE,
    override val upright: Upright = Upright.NONE,
    val region: PixelRect = PixelRect(0, 0, size.width, size.height),
    private val released: MutableList<PixelRect> = mutableListOf(),
) : CaptureImage {
    val releasedCrops: List<PixelRect> get() = released

    override fun crop(rect: PixelRect): CaptureImage = FakeImage(PixelSize(rect.width, rect.height), upright, rect, released)

    override fun release() {
        released += region
    }
}

/**
 * A reader over a fixed page. It records each region it was shown, so tests
 * can prove which pixels were read. [cropReadings], when set, replaces the
 * crop reading with fixed hypotheses (only the first is returned, as ML Kit
 * returns one reading per image).
 */
class FixtureRecognizer(
    var page: List<RecognizedLine>,
    var failWith: Exception? = null,
    var cropLines: ((PixelRect) -> List<RecognizedLine>)? = null,
    var unlocalized: Int = 0,
) : TextRecognizer {
    override val engineId = "fixture-engine"
    override val engineVersion = "0.0.0"
    val regions = mutableListOf<PixelRect>()

    override suspend fun recognize(image: CaptureImage): ReaderOutput {
        val fake = image as FakeImage
        regions += fake.region
        failWith?.let { throw it }
        val full = fake.region == PixelRect(0, 0, CaptureFixtures.IMAGE.width, CaptureFixtures.IMAGE.height)
        if (full) return ReaderOutput(page, unlocalized)
        return ReaderOutput(cropLines?.invoke(fake.region) ?: CaptureFixtures.readCrop(page, fake.region))
    }
}
