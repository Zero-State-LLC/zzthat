package com.zer0state.zzthat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * runtime.md, Accessibility (T076): at a large text size the canonical code
 * wraps rather than truncates, and it wraps only after a hyphen: each part,
 * with its hyphen, stays on one line.
 */
@RunWith(AndroidJUnit4::class)
class CodeTextWrapTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aLongCodeWrapsOnlyAfterAHyphen() {
        val code = "zz-copper-lantern-sky-zz"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ZZTheme(dark = false) {
                    Box(Modifier.width(200.dp)) { CodeText(code) }
                }
            }
        }
        compose.onNodeWithContentDescription(code).assertExists()
        val tops = mutableSetOf<Float>()
        for (piece in codePieces(code)) {
            val node = compose.onNodeWithText(piece, useUnmergedTree = true).fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            assertEquals("\"$piece\" stays on one line", 1, layout.lineCount)
            assertTrue("\"$piece\" is not cut off", !layout.hasVisualOverflow)
            tops += node.boundsInRoot.top
        }
        assertTrue("the code wraps at this width", tops.size > 1)
    }
}
