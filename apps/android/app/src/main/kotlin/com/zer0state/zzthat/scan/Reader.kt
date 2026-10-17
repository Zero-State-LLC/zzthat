package com.zer0state.zzthat.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.zer0state.zzthat.grammar.NormRect
import com.zer0state.zzthat.grammar.PixelRect
import com.zer0state.zzthat.grammar.PixelSize
import com.zer0state.zzthat.grammar.RecognizedLine
import com.zer0state.zzthat.grammar.Upright
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import llc.zerostate.zzthis.design.ZZColor
import java.io.File
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * An upright Android bitmap. The scan photo is never recycled here; a crop
 * made for the reader is, as soon as the reader is done with it.
 */
class BitmapImage(val bitmap: Bitmap, override val upright: Upright, private val isCrop: Boolean = false) : CaptureImage {
    override val size: PixelSize get() = PixelSize(bitmap.width, bitmap.height)

    override fun crop(rect: PixelRect): CaptureImage =
        BitmapImage(Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width, rect.height), upright, isCrop = true)

    override fun release() {
        if (isCrop && !bitmap.isRecycled) bitmap.recycle()
    }
}

/**
 * The Android reader adapter (runtime.md, Read pipeline step 5; tasks.md
 * T025): ML Kit text recognition with the bundled Latin model, on the
 * device. The bundled model needs no download, and no image, crop, or text
 * leaves the phone. ML Kit objects stop here: [MlKitMapping] turns them into
 * RecognizedLine before anything else sees them.
 */
class MlKitRecognizer : TextRecognizer {
    override val engineId: String = ENGINE_ID
    override val engineVersion: String = ENGINE_VERSION

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(image: CaptureImage): ReaderOutput {
        val bitmap = (image as BitmapImage).bitmap
        val text = suspendCancellableCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
        return MlKitMapping.lines(text, bitmap.width, bitmap.height)
    }

    companion object {
        const val ENGINE_ID = "mlkit-text-recognition-latin-bundled"

        /** `com.google.mlkit:text-recognition` in gradle/libs.versions.toml (a test keeps them equal). */
        const val ENGINE_VERSION = "16.0.1"
    }
}

/**
 * ML Kit `Text` to engine-neutral lines. Each line keeps its own
 * `Text.Line.getConfidence()`; Element and Symbol confidences are never used.
 * Elements give the word boxes and Symbols the glyph boxes the marker
 * detector needs.
 */
object MlKitMapping {
    fun lines(text: Text, width: Int, height: Int): ReaderOutput {
        var unlocalized = 0
        val lines = text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = normalized(line.boundingBox, width, height)
                if (box == null) {
                    unlocalized++
                    return@mapNotNull null
                }
                RecognizedLine(
                    text = line.text,
                    confidence = confidence(line.confidence),
                    box = box,
                    pieces = pieces(line, width, height),
                    angleDegrees = line.angle.toDouble().takeIf { it.isFinite() },
                )
            }
        }
        return ReaderOutput(lines, unlocalized)
    }

    /**
     * A line confidence counts as meaningful only when it is finite, above 0,
     * and at most 1. A 0 cannot be told apart from "no score", so 0, NaN, and
     * anything out of range are not available (fail closed: Clarify, never
     * Accept). Nothing is synthesized. Whether ML Kit's line score is
     * calibrated enough for the bands is a qualification question.
     */
    fun confidence(value: Float): Double? = value.toDouble().takeIf { it.isFinite() && it > 0.0 && it <= 1.0 }

    /** Each element's code point range in the line text, found in order, with its box and, when ML Kit gives one per code point, its glyph boxes. */
    private fun pieces(line: Text.Line, width: Int, height: Int): List<RecognizedLine.Piece> {
        val pieces = mutableListOf<RecognizedLine.Piece>()
        var cursor = 0
        for (element in line.elements) {
            val box = normalized(element.boundingBox, width, height) ?: continue
            val start = line.text.indexOf(element.text, cursor)
            if (start < 0 || element.text.isEmpty()) continue
            val end = start + element.text.length
            val first = line.text.codePointCount(0, start)
            val count = line.text.codePointCount(start, end)
            pieces += RecognizedLine.Piece(first until first + count, box, glyphs(count, element.symbols.map { it.boundingBox }, width, height))
            cursor = end
        }
        return pieces
    }

    /**
     * One glyph box per code point of an element, from its Symbols, or none:
     * a count that does not match, or any Symbol without a usable box, gives
     * no glyph boxes rather than misplaced ones.
     */
    fun glyphs(count: Int, symbolBoxes: List<Rect?>, width: Int, height: Int): List<NormRect> {
        if (symbolBoxes.size != count) return emptyList()
        val boxes = symbolBoxes.map { normalized(it, width, height) ?: return emptyList() }
        return boxes
    }

    /** A box normalized over the image, or null when it is missing or empty. Reads the Rect fields, not its methods. */
    fun normalized(box: Rect?, width: Int, height: Int): NormRect? {
        if (box == null || width <= 0 || height <= 0) return null
        val left = box.left.coerceIn(0, width)
        val top = box.top.coerceIn(0, height)
        val right = box.right.coerceIn(0, width)
        val bottom = box.bottom.coerceIn(0, height)
        if (right <= left || bottom <= top) return null
        return NormRect(
            x = left.toDouble() / width,
            y = top.toDouble() / height,
            width = (right - left).toDouble() / width,
            height = (bottom - top).toDouble() / height,
        )
    }
}

/**
 * Step 1, upright normalization: decodes the photo with its EXIF
 * orientation applied, once, and no larger than [MAX_SIDE] pixels, so the
 * reader and the boxes see the same image. The rotation applied is kept as
 * provenance.
 */
object PhotoDecoder {
    const val MAX_SIDE = 3072

    suspend fun decode(file: File): BitmapImage? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext null
        decode({ file.inputStream() }, { file.inputStream() })
    }

    suspend fun decode(context: Context, uri: Uri): BitmapImage? = withContext(Dispatchers.IO) {
        val open = { context.contentResolver.openInputStream(uri) ?: throw java.io.FileNotFoundException(uri.toString()) }
        try {
            decode(open, open)
        } catch (error: Exception) {
            null
        }
    }

    private fun decode(forBitmap: () -> InputStream, forExif: () -> InputStream): BitmapImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        forBitmap().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val bitmap = forBitmap().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return null
        val orientation = try {
            forExif().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (error: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val upright = upright(orientation)
        if (upright == Upright.NONE) return BitmapImage(bitmap, upright)
        val matrix = Matrix()
        if (upright.rotationDegrees != 0) matrix.postRotate(upright.rotationDegrees.toFloat())
        if (upright.mirrored) matrix.postScale(-1f, 1f)
        return BitmapImage(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true), upright)
    }

    /** The EXIF orientation as a rotation, then an optional horizontal flip. */
    fun upright(orientation: Int): Upright = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> Upright(90, mirrored = false)
        ExifInterface.ORIENTATION_ROTATE_180 -> Upright(180, mirrored = false)
        ExifInterface.ORIENTATION_ROTATE_270 -> Upright(270, mirrored = false)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Upright(0, mirrored = true)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Upright(180, mirrored = true)
        ExifInterface.ORIENTATION_TRANSPOSE -> Upright(90, mirrored = true)
        ExifInterface.ORIENTATION_TRANSVERSE -> Upright(270, mirrored = true)
        else -> Upright.NONE
    }
}

/**
 * The fixture photo a Demo build reads instead of the camera (runtime.md,
 * Screens: Scan and Write check, Demo). It is drawn on the device, so it
 * holds only the code, never a sentence.
 */
object DemoPhoto {
    const val CANONICAL = "zz-copper-lantern-sky-zz"

    /** The photo and, for snapshots and tests, the lines a reader with glyph boxes returns for it. */
    class Photo(val image: BitmapImage, val lines: List<RecognizedLine>) {
        val bitmap: Bitmap get() = image.bitmap
    }

    fun make(context: Context, lines: List<String>, confidence: Double? = 0.97): Photo {
        val width = 1200
        val height = 260 + 180 * lines.size
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ZZColor.ColorPaperLight.toArgb())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ZZColor.ColorInkLight.toArgb()
            textSize = 84f
            typeface = try {
                ResourcesCompat.getFont(context, com.zer0state.zzthat.design.R.font.ibmplexsans_semibold)
            } catch (error: Exception) {
                null
            } ?: Typeface.DEFAULT_BOLD
        }
        val metrics = paint.fontMetrics
        val lineHeight = (metrics.descent - metrics.ascent).toDouble() / height
        val placed = lines.mapIndexed { index, line ->
            val measured = paint.measureText(line)
            val x = (width - measured) / 2
            val top = 130f + index * 180
            canvas.drawText(line, x, top - metrics.ascent, paint)
            // One box per character, from the advance of the text before it.
            val glyphs = line.indices.map { offset ->
                val left = x + paint.measureText(line, 0, offset)
                val right = x + paint.measureText(line, 0, offset + 1)
                NormRect(left.toDouble() / width, top.toDouble() / height, (right - left).toDouble() / width, lineHeight)
            }
            val pieces = Regex("\\S+").findAll(line).map { match ->
                val range = match.range
                RecognizedLine.Piece(range, glyphs.subList(range.first, range.last + 1).reduce(NormRect::union), glyphs.subList(range.first, range.last + 1))
            }.toList()
            RecognizedLine(
                text = line,
                confidence = confidence,
                box = NormRect(x.toDouble() / width, top.toDouble() / height, measured.toDouble() / width, lineHeight),
                pieces = pieces,
                angleDegrees = 0.0,
            )
        }
        return Photo(BitmapImage(bitmap, Upright.NONE), placed)
    }
}
