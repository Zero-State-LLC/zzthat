package com.zer0state.zzthat.read

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zer0state.zzthat.grammar.Band
import com.zer0state.zzthat.grammar.CheckResult
import com.zer0state.zzthat.grammar.CodeClass
import com.zer0state.zzthat.grammar.MarkerRole
import com.zer0state.zzthat.grammar.ReadPipeline
import com.zer0state.zzthat.grammar.Upright
import com.zer0state.zzthat.grammar.VisualCandidate
import com.zer0state.zzthat.grammar.WordListLoader
import com.zer0state.zzthat.scan.BitmapImage
import com.zer0state.zzthat.scan.CameraPipeline
import com.zer0state.zzthat.scan.CameraSession
import com.zer0state.zzthat.scan.MlKitRecognizer
import com.zer0state.zzthat.scan.ScanRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * runtime.md, Testing: the read pipeline on a rendered image of
 * zz-copper-lantern-sky-zz yields that one candidate (tasks.md T023 and T025),
 * with ML Kit's bundled Latin model on the device: the marker pass, pairing,
 * and the reader on the selected ROI only. A smoke test, not qualification
 * evidence.
 */
@RunWith(AndroidJUnit4::class)
class ReadPipelineInstrumentedTest {
    /** Black text on white, one line per string, in a plain sans-serif face. */
    private fun render(lines: List<String>, width: Int = 1800, lineHeight: Int = 220): Bitmap {
        val bitmap = Bitmap.createBitmap(width, lineHeight * lines.size + 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 110f
            typeface = Typeface.SANS_SERIF
        }
        lines.forEachIndexed { index, text -> canvas.drawText(text, 60f, (40 + lineHeight * (index + 1) - 60).toFloat(), paint) }
        return bitmap
    }

    private fun fixture7() = InstrumentationRegistry.getInstrumentation().targetContext.assets
        .open("wordlists/fixture-7.txt").bufferedReader().use { WordListLoader.parse("fixture-7", it.readText()) }

    private suspend fun scan(lines: List<String>, pipeline: ReadPipeline = ReadPipeline(fixture7(), fixture7())): Pair<CameraSession, CameraSession.Outcome> {
        val session = CameraSession()
        val outcome = session.begin(BitmapImage(render(lines), Upright.NONE), CameraPipeline(MlKitRecognizer()), pipeline)
        session.result?.let { result ->
            Log.i(TAG, "markers ${result.fiducials.map { it.role to it.box }} pairs ${result.pairs.size} reasons ${result.reasonCodes}")
        }
        return session to outcome
    }

    /** The marker pass boxes both markers from ML Kit's Symbols, then only the ROI is read; confirm-only asks for a confirm. */
    @Test
    fun renderedCodeYieldsThatOneCandidate(): Unit = runBlocking {
        val (session, outcome) = scan(listOf("zz-copper-lantern-sky-zz"))
        val result = requireNotNull(session.result)
        assertEquals("reasons ${result.reasonCodes}", false, result.failedClosed)
        assertEquals(listOf(MarkerRole.OPENING, MarkerRole.CLOSING), result.fiducials.map { it.role })
        val read = requireNotNull((outcome as CameraSession.Outcome.Rows).read)
        Log.i(TAG, "hypotheses ${session.result?.candidates?.map { it.rawText to it.confidence }}")
        assertEquals(listOf("zz-copper-lantern-sky-zz"), read.candidates.map { it.canonical })
        val candidate = read.candidates.single()
        assertEquals(CodeClass.Word, candidate.codeClass)
        assertEquals(CheckResult.Ok, candidate.check)
        // Never Accept while confirm-only is on: a reading at or above 0.80 is turned into Clarify, and a lower one is Clarify already.
        assertEquals(Band.CLARIFY, candidate.band)
        val confidence = session.result!!.candidates.single().confidence.valueOrNull
        if (confidence != null && confidence >= 0.80) assertTrue("confirm-only turned the Accept into Clarify", candidate.confirmOnly)
        val roi = session.result!!.rois.single()
        assertTrue(roi.polygon.toString(), roi.polygon.all { it.x in 0.0..1.0 && it.y in 0.0..1.0 })
        Log.i(TAG, "rows ${candidate.rows}")
    }

    /** v1 pairs same-baseline markers only (FR-009 as narrowed by FR-052): a code wrapped onto a second line fails closed, with no payload read. */
    @Test
    fun aCodeAcrossTwoBaselinesIsNotPaired(): Unit = runBlocking {
        val (session, outcome) = scan(listOf("zz-copper-lantern", "sky-zz"))
        assertTrue(outcome is CameraSession.Outcome.Rows)
        assertNull((outcome as CameraSession.Outcome.Rows).read)
        assertTrue(session.result!!.pairs.isEmpty())
        assertEquals(2, session.rows.count { (it as ScanRow.Unread).candidate is VisualCandidate.Partial })
        assertTrue(session.result!!.candidates.isEmpty())
    }

    /** Two codes wait for a pick before any payload is read. */
    @Test
    fun twoCodesWaitForSelection(): Unit = runBlocking {
        val (session, outcome) = scan(listOf("zz-river-falcon-sky-zz", "zz-copper-lantern-sky-zz"))
        assertNull((outcome as CameraSession.Outcome.Rows).read)
        assertEquals(2, session.result!!.pairs.size)
        assertTrue(session.result!!.candidates.isEmpty())
    }

    private companion object {
        const val TAG = "ReadPipelineTest"
    }
}
