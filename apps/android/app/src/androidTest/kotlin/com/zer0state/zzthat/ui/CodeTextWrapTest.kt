package com.zer0state.zzthat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zer0state.zzthat.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * runtime.md, Accessibility (T076): at a large text size the canonical code
 * wraps rather than truncates, and it wraps only after a hyphen: each part,
 * with its hyphen, stays on one line. TalkBack reads the code as one label,
 * and a row that holds a code still reads as one item.
 */
@RunWith(AndroidJUnit4::class)
class CodeTextWrapTest {
    @get:Rule
    val compose = createComposeRule()

    private val code = "zz-copper-lantern-sky-zz"

    @Test
    fun aLongCodeWrapsOnlyAfterAHyphen() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ZZTheme(dark = false) {
                    Box(Modifier.width(200.dp)) { CodeText(code) }
                }
            }
        }
        compose.onNodeWithContentDescription(code).assertExists()
        val pieces = compose.onAllNodesWithTag(CODE_PIECE_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(codePieces(code).size, pieces.size)
        // One line each: every piece is as tall as the shortest one.
        val heights = pieces.map { it.boundsInRoot.height }
        val line = heights.min()
        assertTrue("each piece stays on one line: $heights", heights.all { it < line * 1.5f })
        val tops = pieces.map { it.boundsInRoot.top }.toSet()
        assertTrue("the code wraps at this width", tops.size > 1)
    }

    /** The pieces add no labels of their own, so a tappable row keeps the code in its one merged label. */
    @Test
    fun aRowThatHoldsACodeCarriesItsLabel() {
        compose.setContent {
            ZZTheme(dark = false) {
                Column(Modifier.testTag("row").clickable {}) {
                    CodeText(code)
                    Text(stringResource(R.string.nav_my_codes))
                }
            }
        }
        val row = compose.onNodeWithTag("row").fetchSemanticsNode()
        assertEquals(listOf(code), row.config.getOrNull(SemanticsProperties.ContentDescription))
    }
}
