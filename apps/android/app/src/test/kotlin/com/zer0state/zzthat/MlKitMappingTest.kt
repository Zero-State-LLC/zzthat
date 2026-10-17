package com.zer0state.zzthat

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import com.zer0state.zzthat.grammar.NormRect
import com.zer0state.zzthat.scan.MlKitMapping
import com.zer0state.zzthat.scan.MlKitRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The ML Kit adapter's mapping (tasks.md T025): ML Kit `Text` in, engine
 * neutral lines out. The fakes are ML Kit's own result types with their box
 * getters overridden, because the unit-test android.jar does not copy Rect
 * fields. Symbols have no public constructor, so their mapping is tested
 * through [MlKitMapping.glyphs], which is what the adapter calls with each
 * Symbol's box.
 */
class MlKitMappingTest {
    private fun rect(left: Int, top: Int, right: Int, bottom: Int) = Rect().apply {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    private class FakeElement(text: String, private val box: Rect?) : Text.Element(text, Rect(), emptyList<Any>(), "und", null, 0f, 0f, emptyList<Any>()) {
        override fun getBoundingBox(): Rect? = box
    }

    private class FakeLine(text: String, private val box: Rect?, elements: List<Text.Element>, angle: Float, confidence: Float) :
        Text.Line(text, Rect(), emptyList<Any>(), "und", null, elements, confidence, angle) {
        override fun getBoundingBox(): Rect? = box
    }

    private fun text(vararg lines: Text.Line): Text =
        Text(lines.joinToString("\n") { it.text }, listOf(Text.TextBlock("block", Rect(), emptyList<Any>(), "und", null, lines.toList())))

    @Test
    fun linesKeepTheirTextBoxAngleAndLineConfidence() {
        val line = FakeLine(
            "zz copper",
            rect(100, 50, 900, 110),
            listOf(FakeElement("zz", rect(100, 50, 180, 110)), FakeElement("copper", rect(220, 50, 900, 110))),
            angle = 2.5f,
            confidence = 0.83f,
        )
        val output = MlKitMapping.lines(text(line), 1000, 500)
        assertEquals(0, output.unlocalizedLines)
        val mapped = output.lines.single()
        assertEquals("zz copper", mapped.text)
        assertEquals(0.83, mapped.confidence!!, 1e-6)
        assertEquals(2.5, mapped.angleDegrees!!, 1e-6)
        assertEquals(NormRect(0.1, 0.1, 0.8, 0.12), mapped.box.rounded())
        assertEquals(listOf(0..1, 3..8), mapped.pieces.map { it.range })
        assertEquals(NormRect(0.1, 0.1, 0.08, 0.12), mapped.pieces[0].box.rounded())
        assertTrue("no Symbols, no glyph boxes", mapped.pieces.all { it.glyphs.isEmpty() })
    }

    /** Missing confidence stays missing (recognition-adapter.md step 5): 0, NaN, and out-of-range scores are not available. */
    @Test
    fun aScoreThatIsNotMeaningfulIsNotAvailable() {
        for (score in listOf(0f, Float.NaN, -0.1f, 1.5f, Float.POSITIVE_INFINITY)) {
            val line = FakeLine("zz", rect(0, 0, 10, 10), emptyList(), 0f, score)
            assertNull("score $score", MlKitMapping.lines(text(line), 100, 100).lines.single().confidence)
        }
        assertEquals(1.0, MlKitMapping.confidence(1f)!!, 0.0)
        assertEquals(0.5, MlKitMapping.confidence(0.5f)!!, 0.0)
    }

    /** A line ML Kit gives without a usable box is counted, so the frame can fail closed instead of losing a marker. */
    @Test
    fun aLineWithoutABoxIsCountedNotDropped() {
        val boxed = FakeLine("zz", rect(0, 0, 10, 10), emptyList(), 0f, 0.9f)
        val missing = FakeLine("zz", null, emptyList(), 0f, 0.9f)
        val empty = FakeLine("zz", rect(10, 10, 10, 20), emptyList(), 0f, 0.9f)
        val output = MlKitMapping.lines(text(boxed, missing, empty), 100, 100)
        assertEquals(1, output.lines.size)
        assertEquals(2, output.unlocalizedLines)
    }

    @Test
    fun anElementWithoutABoxGivesNoPiece() {
        val line = FakeLine("zz copper", rect(0, 0, 100, 10), listOf(FakeElement("zz", null), FakeElement("copper", rect(30, 0, 100, 10))), 0f, 0.9f)
        assertEquals(listOf(3..8), MlKitMapping.lines(text(line), 100, 100).lines.single().pieces.map { it.range })
    }

    @Test
    fun glyphBoxesNeedOneUsableSymbolPerCodePoint() {
        val boxes = listOf(rect(0, 0, 10, 10), rect(10, 0, 20, 10))
        assertEquals(listOf(NormRect(0.0, 0.0, 0.1, 0.1), NormRect(0.1, 0.0, 0.1, 0.1)), MlKitMapping.glyphs(2, boxes, 100, 100).map { it.rounded() })
        assertTrue("a count that does not match", MlKitMapping.glyphs(3, boxes, 100, 100).isEmpty())
        assertTrue("a Symbol without a box", MlKitMapping.glyphs(2, listOf(boxes[0], null), 100, 100).isEmpty())
    }

    @Test
    fun boxesAreClampedToTheImageAndEmptyOnesRejected() {
        assertEquals(NormRect(0.0, 0.0, 1.0, 0.5), MlKitMapping.normalized(rect(-5, -5, 120, 50), 100, 100)?.rounded())
        assertNull(MlKitMapping.normalized(rect(50, 50, 50, 60), 100, 100))
        assertNull(MlKitMapping.normalized(null, 100, 100))
        assertNull(MlKitMapping.normalized(rect(0, 0, 10, 10), 0, 100))
    }

    /** The engine version the adapter reports is the pinned dependency (recognition.md, Configuration). */
    @Test
    fun theEngineVersionIsThePinnedLibrary() {
        val catalog = File("../gradle/libs.versions.toml").readText()
        val pinned = Regex("""(?m)^mlkit-text-recognition = "([^"]+)"""").find(catalog)?.groupValues?.get(1)
        assertEquals(pinned, MlKitRecognizer.ENGINE_VERSION)
        assertEquals("mlkit-text-recognition-latin-bundled", MlKitRecognizer.ENGINE_ID)
    }

    private fun NormRect.rounded(): NormRect {
        fun r(value: Double) = Math.round(value * 1e9) / 1e9
        return NormRect(r(x), r(y), r(width), r(height))
    }
}
