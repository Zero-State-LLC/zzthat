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
import com.zer0state.zzthat.grammar.RecognizedLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import llc.zerostate.zzthis.design.ZZColor
import java.io.File
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The Android reader (runtime.md, Read pipeline step 2): ML Kit text
 * recognition with the bundled Latin model. Each line keeps its own
 * `Text.Line.getConfidence()`; Element and Symbol confidences are never used.
 * Elements give only the word boxes.
 */
class MlKitReader : TextReader {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun read(bitmap: Bitmap): List<RecognizedLine> {
        val text = suspendCancellableCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
        return lines(text, bitmap.width, bitmap.height)
    }

    companion object {
        fun lines(text: Text, width: Int, height: Int): List<RecognizedLine> = text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                RecognizedLine(
                    text = line.text,
                    confidence = line.confidence.toDouble(),
                    box = normalized(box, width, height),
                    pieces = pieces(line, width, height),
                )
            }
        }

        /** Each element's code point range in the line text, found in order, with its box. */
        private fun pieces(line: Text.Line, width: Int, height: Int): List<RecognizedLine.Piece> {
            val pieces = mutableListOf<RecognizedLine.Piece>()
            var cursor = 0
            for (element in line.elements) {
                val box = element.boundingBox ?: continue
                val start = line.text.indexOf(element.text, cursor)
                if (start < 0 || element.text.isEmpty()) continue
                val end = start + element.text.length
                val first = line.text.codePointCount(0, start)
                val last = first + line.text.codePointCount(start, end) - 1
                pieces += RecognizedLine.Piece(first..last, normalized(box, width, height))
                cursor = end
            }
            return pieces
        }

        private fun normalized(box: Rect, width: Int, height: Int) = NormRect(
            x = box.left.toDouble() / width,
            y = box.top.toDouble() / height,
            width = box.width().toDouble() / width,
            height = box.height().toDouble() / height,
        )
    }
}

/**
 * Decodes the photo upright (the EXIF orientation applied) and no larger
 * than [MAX_SIDE] pixels, so the reader and the boxes see the same image.
 */
object PhotoDecoder {
    const val MAX_SIDE = 3072

    suspend fun decode(file: File): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext null
        decode({ file.inputStream() }, { file.inputStream() })
    }

    suspend fun decode(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val open = { context.contentResolver.openInputStream(uri) ?: throw java.io.FileNotFoundException(uri.toString()) }
        try {
            decode(open, open)
        } catch (error: Exception) {
            null
        }
    }

    private fun decode(forBitmap: () -> InputStream, forExif: () -> InputStream): Bitmap? {
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
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

/**
 * The fixture photo a Demo build reads instead of the camera (runtime.md,
 * Screens: Scan and Write check, Demo). It is drawn on the device, so it
 * holds only the code, never a sentence.
 */
object DemoPhoto {
    const val CANONICAL = "zz-copper-lantern-sky-zz"

    class Photo(val bitmap: Bitmap, val lines: List<Pair<String, NormRect>>)

    /** The photo and, for snapshots, the lines a reader returns for it. */
    fun make(context: Context, lines: List<String>): Photo {
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
        val placed = lines.mapIndexed { index, line ->
            val measured = paint.measureText(line)
            val x = (width - measured) / 2
            val top = 130f + index * 180
            canvas.drawText(line, x, top - metrics.ascent, paint)
            line to NormRect(
                x = x.toDouble() / width,
                y = top.toDouble() / height,
                width = measured.toDouble() / width,
                height = (metrics.descent - metrics.ascent).toDouble() / height,
            )
        }
        return Photo(bitmap, placed)
    }
}
