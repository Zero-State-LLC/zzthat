package com.zer0state.zzthat.scan

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zer0state.zzthat.R
import com.zer0state.zzthat.grammar.Band
import com.zer0state.zzthat.grammar.CameraRead
import com.zer0state.zzthat.grammar.InteriorRoi
import com.zer0state.zzthat.grammar.NormRect
import com.zer0state.zzthat.grammar.PairRead
import com.zer0state.zzthat.grammar.PixelRect
import com.zer0state.zzthat.grammar.PixelSize
import com.zer0state.zzthat.grammar.ReadCandidate
import com.zer0state.zzthat.grammar.ReadPipeline
import com.zer0state.zzthat.grammar.ReasonCodes
import com.zer0state.zzthat.grammar.RecognitionResult
import com.zer0state.zzthat.grammar.RecognizedLine
import com.zer0state.zzthat.grammar.Upright
import com.zer0state.zzthat.grammar.VisualCandidate
import kotlinx.coroutines.CancellationException
import java.util.UUID

// The camera read pipeline (runtime.md, Read pipeline; recognition-adapter.md,
// Runtime sequence) on Android: one upright still, the reader's marker pass,
// pairing and selection before payload OCR, then the reader on the selected
// pair's interior ROI only. Everything here lives in memory for the current
// attempt; nothing is written, logged, or sent (FR-004, FR-005).

/** An upright still the reader can see whole or cropped. */
interface CaptureImage {
    val size: PixelSize
    val upright: Upright

    /** The pixels inside [rect] as a new image. It is used once and released. */
    fun crop(rect: PixelRect): CaptureImage

    /** Frees a crop's pixels. The photo itself is freed when the scan ends. */
    fun release()
}

/** What the reader returns for one image: lines normalized over that image, and how many lines came with no usable box. */
data class ReaderOutput(val lines: List<RecognizedLine>, val unlocalizedLines: Int = 0)

/** The on-device reader behind the adapter boundary. Vendor objects stay inside its implementation. */
interface TextRecognizer {
    val engineId: String
    val engineVersion: String

    suspend fun recognize(image: CaptureImage): ReaderOutput
}

/** The runtime sequence over one reader. The capture configuration is the read pipeline's, so there is one setting. */
class CameraPipeline(val recognizer: TextRecognizer, private val newCaptureId: () -> String = { UUID.randomUUID().toString() }) {
    /** Steps 1 to 3. A new image is a new attempt with a new capture id. */
    suspend fun detect(image: CaptureImage, pipeline: ReadPipeline): RecognitionResult {
        val id = newCaptureId()
        val output = readOrNull { recognizer.recognize(image) }
            ?: return CameraRead.recognizerFailed(id, recognizer.engineId, recognizer.engineVersion, image.size, image.upright, pipeline.capture)
        return CameraRead.detect(
            captureId = id,
            engineId = recognizer.engineId,
            engineVersion = recognizer.engineVersion,
            image = image.size,
            upright = image.upright,
            lines = output.lines,
            config = pipeline.capture,
            unlocalizedLines = output.unlocalizedLines,
        )
    }

    /** Steps 4 to 7 for the sole or selected complete pair: crop its ROI, read only that, decode. */
    suspend fun readPair(image: CaptureImage, result: RecognitionResult, pairId: String, pipeline: ReadPipeline): Pair<RecognitionResult, PairRead> {
        val roi = when (val outcome = CameraRead.roi(result, pairId, pipeline.capture)) {
            is InteriorRoi.Outcome.Unsafe -> return result to CameraRead.decode(result, pairId, pipeline, outcome.reason)
            is InteriorRoi.Outcome.Safe -> outcome.roi
        }
        val crop = image.crop(roi.crop)
        val output = try {
            readOrNull { recognizer.recognize(crop) }
        } finally {
            crop.release()
        }
        val reason = when {
            output == null -> ReasonCodes.RECOGNIZER_UNAVAILABLE
            // A payload line without a box cannot be placed, so the reading is incomplete.
            output.unlocalizedLines > 0 -> ReasonCodes.UNLOCALIZED_TEXT
            else -> null
        }
        if (output == null || reason != null) return result to CameraRead.decode(result, pairId, pipeline, reason)
        val withText = CameraRead.withPayload(result, roi, listOf(output.lines))
        return withText to CameraRead.decode(withText, pairId, pipeline)
    }
}

/** One row of the Scan list and one numbered box on the photo, in reading order. */
sealed interface ScanRow {
    val box: NormRect

    /** A visual candidate before payload OCR: a complete pair not read yet, a partial code, or a bare mark. */
    data class Unread(val candidate: VisualCandidate) : ScanRow {
        override val box get() = candidate.box
    }

    /** A decoded candidate of one read pair. */
    data class Read(val pairId: String, val candidate: ReadCandidate, override val box: NormRect) : ScanRow
}

/**
 * One capture attempt on screen: the photo, its evidence, and the rows. The
 * evidence is for this attempt only and is dropped by [clear].
 */
class CameraSession {
    var image by mutableStateOf<CaptureImage?>(null)
        private set
    var rows by mutableStateOf<List<ScanRow>>(emptyList())
        private set
    var reading by mutableStateOf(false)
        private set

    /** The current attempt's evidence, kept in memory only. */
    var result: RecognitionResult? = null
        private set

    /** What a photo or a tap led to. */
    sealed interface Outcome {
        /** Nothing to list; show [message]. */
        data class Empty(@StringRes val message: Int) : Outcome

        /** Rows to pick from. [read] is the sole pair's read when it ran without a tap. */
        data class Rows(val read: PairRead?) : Outcome

        /** A tap on a pair that decoded to [read]. */
        data class PairDone(val read: PairRead) : Outcome
    }

    /** Steps 1 to 3, then steps 4 to 7 only when one complete pair is the only selectable candidate. */
    suspend fun begin(image: CaptureImage, camera: CameraPipeline, pipeline: ReadPipeline): Outcome {
        clear()
        this.image = image
        reading = true
        try {
            val detected = camera.detect(image, pipeline)
            result = detected
            rows = detected.visualCandidates.map(ScanRow::Unread)
            if (rows.isEmpty()) return Outcome.Empty(emptyMessage(detected, pipeline))
            val sole = detected.soleCompletePair ?: return Outcome.Rows(null)
            return Outcome.Rows(readPair(sole.pairId, camera, pipeline))
        } finally {
            reading = false
        }
    }

    /** A tap on an unread complete pair runs steps 4 to 7 for that pair only. */
    suspend fun open(row: ScanRow.Unread, camera: CameraPipeline, pipeline: ReadPipeline): Outcome? {
        val pair = (row.candidate as? VisualCandidate.Complete)?.pair ?: return null
        reading = true
        try {
            return Outcome.PairDone(readPair(pair.pairId, camera, pipeline))
        } finally {
            reading = false
        }
    }

    private suspend fun readPair(pairId: String, camera: CameraPipeline, pipeline: ReadPipeline): PairRead {
        val current = requireNotNull(result)
        val photo = requireNotNull(image)
        val (withText, read) = camera.readPair(photo, current, pairId, pipeline)
        result = withText
        val box = current.pair(pairId)?.box ?: NormRect.ZERO
        if (read.candidates.isNotEmpty()) {
            rows = rows.flatMap { row ->
                val unread = row as? ScanRow.Unread
                val complete = unread?.candidate as? VisualCandidate.Complete
                if (complete?.pair?.pairId == pairId) read.candidates.map { ScanRow.Read(pairId, it, box) } else listOf(row)
            }
        }
        return read
    }

    /** The attempt ends: its evidence and rows go, and the photo is released. */
    fun clear() {
        result = null
        rows = emptyList()
        image = null
        reading = false
    }

    companion object {
        /** A frame with nothing to list: Retry asks for another photo; anything else says no code was found. */
        @StringRes
        fun emptyMessage(result: RecognitionResult, pipeline: ReadPipeline): Int {
            val retry = result.failedClosed && result.reasonCodes.all { pipeline.capture.bandFor(it) == Band.RETRY }
            return if (retry) R.string.scan_retry else R.string.scan_no_code
        }

        /** The sentence for a read pair with no candidate to show. */
        @StringRes
        fun emptyMessage(read: PairRead): Int = if (read.emptyBand == Band.RETRY) R.string.scan_retry else R.string.scan_no_code
    }
}

/** Cancellation ends the read operation; only ordinary reader errors become an empty result. */
internal suspend fun <T> readOrNull(read: suspend () -> T): T? = try {
    read()
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    null
}
