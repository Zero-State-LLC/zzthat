package com.zer0state.zzthat.saved

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.zer0state.zzthat.R
import com.zer0state.zzthat.ui.CodeText
import com.zer0state.zzthat.ui.Message
import com.zer0state.zzthat.ui.MinTouch
import com.zer0state.zzthat.ui.ZZScreen
import com.zer0state.zzthat.ui.panel
import llc.zerostate.zzthis.design.ZZSpace
import llc.zerostate.zzthis.design.ZZType

data class SavedScreenState(
    /** The saved canonical codes, newest first. */
    val codes: List<String> = emptyList(),
    /** Set when a tapped code cannot open, such as offline with nothing cached. */
    @StringRes val message: Int? = null,
    val demo: Boolean = false,
)

/**
 * Saved (FR-050, zzThis RM-115): the canonical codes saved on this device,
 * newest first, in mono, under the `saved.title` bar. A tap opens Resolve
 * for that code, which resolves as usual. The list is local only, so it
 * shows the same offline.
 */
@Composable
fun SavedScreen(state: SavedScreenState, onSelect: (String) -> Unit = {}) {
    ZZScreen(demo = state.demo) {
        state.message?.let { Message(it) }
        if (state.codes.isEmpty()) {
            Message(R.string.saved_empty)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ZZSpace.SpaceXs)) {
                state.codes.forEach { canonical ->
                    Column(
                        modifier = Modifier
                            .testTag("saved.row.$canonical")
                            .clickable(role = Role.Button) { onSelect(canonical) }
                            .defaultMinSize(minHeight = MinTouch)
                            .semantics(mergeDescendants = true) {}
                            .panel(),
                    ) {
                        CodeText(canonical, size = ZZType.TextBase)
                    }
                }
            }
        }
    }
}
