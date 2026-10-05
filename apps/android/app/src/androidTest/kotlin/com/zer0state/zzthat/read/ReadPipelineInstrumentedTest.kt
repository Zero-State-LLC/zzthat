package com.zer0state.zzthat.read

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zer0state.zzthat.grammar.CheckResult
import com.zer0state.zzthat.grammar.CodeClass
import com.zer0state.zzthat.grammar.ReadPipeline
import com.zer0state.zzthat.grammar.WordListLoader
import com.zer0state.zzthat.scan.MlKitReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * runtime.md, Testing: the read pipeline on a rendered image of
 * zz-copper-lantern-sky-zz yields that one candidate (tasks.md T023, Phase 2),
 * with ML Kit's bundled Latin model on the device.
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

    @Test
    fun renderedCodeYieldsThatOneCandidate(): Unit = runBlocking {
        val lines = MlKitReader().read(render(listOf("zz-copper-lantern-sky-zz")))
        lines.forEach { Log.i(TAG, "line \"${it.text}\" confidence ${it.confidence} pieces ${it.pieces.size}") }
        val list = fixture7()
        val result = ReadPipeline(list, list).read(lines)
        assertEquals("read: ${result.text}", listOf("zz-copper-lantern-sky-zz"), result.candidates.map { it.canonical })
        val candidate = result.candidates.single()
        assertEquals(CodeClass.Word, candidate.codeClass)
        assertEquals(CheckResult.Ok, candidate.check)
        assertTrue(candidate.boxes.isNotEmpty())
        assertTrue(candidate.boxes.toString(), candidate.boxes.all { it.minX >= 0 && it.minY >= 0 && it.maxX <= 1 && it.maxY <= 1 })
        Log.i(TAG, "band ${candidate.band} rows ${candidate.rows}")
    }

    @Test
    fun aCodeAcrossTwoLinesIsOneCode(): Unit = runBlocking {
        val lines = MlKitReader().read(render(listOf("zz-copper-lantern", "sky-zz")))
        lines.forEach { Log.i(TAG, "line \"${it.text}\" confidence ${it.confidence}") }
        val result = ReadPipeline(null, null).read(lines)
        assertEquals("read: ${result.text}", listOf("zz-copper-lantern-sky-zz"), result.candidates.map { it.canonical })
        assertEquals(listOf(0, 0, 1), result.candidates.single().rows.map { it.lineIndex })
    }

    private companion object {
        const val TAG = "ReadPipelineTest"
    }
}
