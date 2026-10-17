package com.zer0state.zzthat.scan

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.zer0state.zzthat.R
import com.zer0state.zzthat.design.ZZFonts
import com.zer0state.zzthat.grammar.Band
import com.zer0state.zzthat.grammar.Grammar
import com.zer0state.zzthat.grammar.MarkerRole
import com.zer0state.zzthat.grammar.ScanKind
import com.zer0state.zzthat.grammar.VisualCandidate
import com.zer0state.zzthat.ui.ButtonKind
import com.zer0state.zzthat.ui.ChipShape
import com.zer0state.zzthat.ui.CodeText
import com.zer0state.zzthat.ui.LimitedField
import com.zer0state.zzthat.ui.LocalPalette
import com.zer0state.zzthat.ui.Message
import com.zer0state.zzthat.ui.MinTouch
import com.zer0state.zzthat.ui.MonoText
import com.zer0state.zzthat.ui.PhotoBoxes
import com.zer0state.zzthat.ui.RoleText
import com.zer0state.zzthat.ui.TextRole
import com.zer0state.zzthat.ui.ZZButton
import com.zer0state.zzthat.ui.PhotoRegion
import com.zer0state.zzthat.ui.ZZScreen
import llc.zerostate.zzthis.design.ZZSpace
import llc.zerostate.zzthis.design.ZZType

/** What the Scan screen shows (runtime.md, Screens: Scan and Pick). */
data class ScanScreenState(
    val photo: ImageBitmap? = null,
    val rows: List<ScanRow> = emptyList(),
    val reading: Boolean = false,
    @StringRes val message: Int? = null,
    val demo: Boolean = false,
    val cameraAvailable: Boolean = false,
)

/**
 * Scan (design/UX.md, Scan with the camera): one still photo, a box on it
 * for each candidate, and a list in reading order. Typing is always one tap
 * away. Several candidates wait for a tap (FR-026) before any payload is
 * read (FR-052); nothing is picked for the person.
 */
@Composable
fun ScanScreen(
    state: ScanScreenState,
    onTakePhoto: () -> Unit = {},
    onPickPhoto: () -> Unit = {},
    onTypeInstead: () -> Unit = {},
    onSelect: (ScanRow) -> Unit = {},
) {
    ZZScreen(demo = state.demo, loading = state.reading) {
        val photo = state.photo
        if (photo != null) {
            PhotoBoxes(photo, state.rows.map { it.box })
            if (state.rows.isNotEmpty()) CandidateList(state.rows, onSelect, photo)
            state.message?.let { Message(it) }
            if (!state.reading) CaptureButtons(retake = true, state.cameraAvailable, state.demo, onTakePhoto, onPickPhoto)
        } else {
            state.message?.let { Message(it) }
            CaptureButtons(retake = false, state.cameraAvailable, state.demo, onTakePhoto, onPickPhoto)
        }
        ZZButton(stringResource(R.string.scan_type_instead), "scan.type_instead", ButtonKind.SECONDARY, onClick = onTypeInstead)
    }
}

/**
 * `scan.take_photo` opens the system camera when the phone has one. The
 * system photo picker, `scan.choose_photo` (Danny, #43 A1), is offered on
 * every build (FR-012); on a phone without a camera it is the main button.
 */
@Composable
fun CaptureButtons(retake: Boolean, cameraAvailable: Boolean, demo: Boolean, onTakePhoto: () -> Unit, onPickPhoto: () -> Unit) {
    val palette = LocalPalette.current
    val label = stringResource(if (retake) R.string.scan_retry else R.string.scan_take_photo)
    val choose = stringResource(R.string.scan_choose_photo)
    if (cameraAvailable || demo) {
        Row(horizontalArrangement = Arrangement.spacedBy(ZZSpace.SpaceXs), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                ZZButton(label, "scan.take_photo", onClick = onTakePhoto)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .testTag("scan.photo_picker")
                    .size(MinTouch)
                    .background(palette.panel, ChipShape)
                    .border(ZZSpace.RuleHair, palette.ruleStrong, ChipShape)
                    .clickable(role = Role.Button, onClick = onPickPhoto)
                    .semantics { contentDescription = choose },
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_gallery),
                    contentDescription = null,
                    tint = palette.ink,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    } else {
        ZZButton(choose, "scan.photo_picker", onClick = onPickPhoto)
    }
}

/**
 * Pick (runtime.md, Screens): every candidate in reading order, numbered like
 * its box. A row opens only when the person taps it. A candidate not read yet
 * shows its own region of [photo], so the person sees which code is which
 * (zzThis spec 004 FR-013, FR-020); a complete pair's payload is read after
 * the tap.
 */
@Composable
fun CandidateList(rows: List<ScanRow>, onSelect: (ScanRow) -> Unit, photo: ImageBitmap? = null) {
    val palette = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(ZZSpace.SpaceXs)) {
        if (rows.size > 1 || (rows.firstOrNull() as? ScanRow.Read)?.candidate?.band != Band.ACCEPT) {
            RoleText(stringResource(R.string.scan_pick_one), TextRole.TITLE, Modifier.semantics { heading() })
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.panel, ChipShape)
                .border(ZZSpace.RuleHair, palette.rule, ChipShape),
        ) {
            rows.forEachIndexed { offset, row ->
                CandidateRow(offset + 1, row, Modifier.testTag("scan.candidate.${offset + 1}").clickable(role = Role.Button) { onSelect(row) }, photo)
                if (offset < rows.size - 1) HorizontalDivider(color = palette.rule, thickness = ZZSpace.RuleHair)
            }
        }
    }
}

@Composable
fun CandidateRow(number: Int, row: ScanRow, modifier: Modifier = Modifier, photo: ImageBitmap? = null) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZZSpace.SpaceXs),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouch)
            .semantics(mergeDescendants = true) {}
            .padding(ZZSpace.SpaceSm),
    ) {
        Text(
            text = "$number",
            style = TextStyle(fontFamily = ZZFonts.mono, fontSize = ZZType.TextXs, fontWeight = ZZType.WeightSemibold, color = palette.onAccent),
            modifier = Modifier.background(palette.accent, ChipShape).padding(horizontal = ZZSpace.Space2xs),
        )
        Column(verticalArrangement = Arrangement.spacedBy(ZZSpace.Space3xs), modifier = Modifier.weight(1f)) {
            when (row) {
                is ScanRow.Read -> when (val kind = row.candidate.kind) {
                    is ScanKind.Code -> CodeText(kind.code.canonical, size = ZZType.TextBase)
                    is ScanKind.Partial -> {
                        MonoText(kind.text)
                        RoleText(stringResource(R.string.scan_partial), TextRole.CAPTION)
                    }
                    ScanKind.Bare -> {
                        MonoText("zz")
                        RoleText(stringResource(R.string.scan_bare), TextRole.CAPTION)
                    }
                    is ScanKind.Invalid -> {
                        MonoText(kind.text)
                        RoleText(stringResource(R.string.type_malformed), TextRole.CAPTION)
                    }
                }
                is ScanRow.Unread -> if (photo != null) {
                    PhotoRegion(photo, row.box)
                    when (row.candidate) {
                        is VisualCandidate.Complete -> Unit
                        is VisualCandidate.Partial -> RoleText(stringResource(R.string.scan_partial), TextRole.CAPTION)
                        is VisualCandidate.Bare -> RoleText(stringResource(R.string.scan_bare), TextRole.CAPTION)
                    }
                } else when (val candidate = row.candidate) {
                    is VisualCandidate.Complete -> MonoText(UNREAD_PAIR)
                    is VisualCandidate.Partial -> {
                        MonoText(if (candidate.marker.role == MarkerRole.CLOSING) "\u2026 " + candidate.marker.literal else candidate.marker.literal + " \u2026")
                        RoleText(stringResource(R.string.scan_partial), TextRole.CAPTION)
                    }
                    is VisualCandidate.Bare -> {
                        MonoText(candidate.marker.literal)
                        RoleText(stringResource(R.string.scan_bare), TextRole.CAPTION)
                    }
                }
            }
        }
    }
}

/** Without a photo: the two markers of a pair whose payload is read only after the tap. Glyphs, not a sentence. */
private const val UNREAD_PAIR = "zz \u2026 zz"

// MARK: Type

data class TypeScreenState(
    @StringRes val message: Int? = null,
    val loading: Boolean = false,
    val demo: Boolean = false,
)

/** Type (US2a): the same parser and the same resolve as Scan. */
@Composable
fun TypeScreen(text: String, onTextChange: (String) -> Unit, state: TypeScreenState, onOpen: () -> Unit = {}) {
    ZZScreen(demo = state.demo, loading = state.loading) {
        LimitedField(
            label = R.string.type_field_label,
            value = text,
            onValueChange = onTextChange,
            limit = Int.MAX_VALUE,
            tag = "type.field",
            countsTrimmed = false,
            mono = true,
            keyboardType = KeyboardType.Ascii,
            onDone = onOpen,
        )
        when {
            state.message != null -> Message(state.message)
            text.isBlank() -> RoleText(stringResource(R.string.type_empty), TextRole.SECONDARY)
        }
        ZZButton(stringResource(R.string.type_open), "type.open", enabled = !state.loading, onClick = onOpen)
    }
}

// MARK: Clarify

data class ClarifyScreenState(val parts: List<String>, val step: ClarifyStep, val demo: Boolean = false)

/**
 * Clarify (spec 004 FR-015 and FR-016): the uncertain word with its list
 * candidates and the part as written, or a check-word mismatch with the words
 * as read. Nothing is corrected for the person.
 */
@Composable
fun ClarifyScreen(
    state: ClarifyScreenState,
    onPick: (String, Int) -> Unit = { _, _ -> },
    onKeep: (Int) -> Unit = {},
    onConfirm: () -> Unit = {},
    onTypeInstead: () -> Unit = {},
    onRetake: () -> Unit = {},
    @StringRes message: Int? = null,
) {
    ZZScreen(demo = state.demo) {
        val monoStyle = TextStyle(fontFamily = ZZFonts.mono, fontSize = ZZType.TextBase, fontWeight = ZZType.WeightSemibold)
        when (val step = state.step) {
            is ClarifyStep.NearWord -> {
                RoleText(stringResource(R.string.scan_clarify_title), TextRole.HEADING, Modifier.semantics { heading() })
                PartsText(state.parts, highlight = step.position)
                for (word in step.candidates) {
                    ZZButton(word, "clarify.candidate.$word", ButtonKind.SECONDARY, textStyle = monoStyle) { onPick(word, step.position) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(ZZSpace.Space3xs)) {
                    MonoText(step.written)
                    ZZButton(stringResource(R.string.scan_clarify_as_written), "clarify.as_written", ButtonKind.SECONDARY) { onKeep(step.position) }
                }
            }
            ClarifyStep.Confirm -> {
                RoleText(stringResource(R.string.scan_clarify_title), TextRole.HEADING, Modifier.semantics { heading() })
                PartsText(state.parts, highlight = null)
                ZZButton(stringResource(R.string.scan_clarify_as_written), "clarify.as_written", onClick = onConfirm)
            }
            ClarifyStep.CheckMismatch -> {
                PartsText(state.parts, highlight = null)
                Message(R.string.scan_check_mismatch)
            }
            ClarifyStep.WrongLength -> {
                PartsText(state.parts, highlight = null)
                Message(R.string.scan_wrong_length)
            }
            is ClarifyStep.Done -> CodeText(step.canonical)
        }
        ZZButton(stringResource(R.string.scan_type_instead), "scan.type_instead", ButtonKind.SECONDARY, onClick = onTypeInstead)
        ZZButton(stringResource(R.string.scan_retry), "scan.retry", ButtonKind.SECONDARY, onClick = onRetake)
        message?.let { Message(it) }
    }
}

/** A code as read, with one part marked for the person to check. */
@Composable
fun PartsText(parts: List<String>, highlight: Int?) {
    val palette = LocalPalette.current
    val text = buildAnnotatedString {
        append("zz-")
        parts.forEachIndexed { index, part ->
            if (highlight == index + 1) {
                withStyle(SpanStyle(color = palette.accentInk, background = palette.accentWash, textDecoration = TextDecoration.Underline)) {
                    append(part)
                }
            } else {
                append(part)
            }
            append("-")
        }
        append("zz")
    }
    val canonical = Grammar.format(parts)
    Text(
        text = text,
        style = TextStyle(fontFamily = ZZFonts.mono, fontSize = ZZType.TextMd, fontWeight = ZZType.WeightMedium, color = palette.ink),
        modifier = Modifier.semantics { contentDescription = canonical },
    )
}
