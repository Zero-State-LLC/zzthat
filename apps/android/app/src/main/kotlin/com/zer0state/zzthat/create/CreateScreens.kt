package com.zer0state.zzthat.create

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.zer0state.zzthat.R
import com.zer0state.zzthat.design.ZZFonts
import com.zer0state.zzthat.grammar.ReadCandidate
import com.zer0state.zzthat.scan.CandidateList
import com.zer0state.zzthat.scan.CaptureButtons
import com.zer0state.zzthat.ui.ButtonKind
import com.zer0state.zzthat.ui.CodeText
import com.zer0state.zzthat.ui.FieldLimits
import com.zer0state.zzthat.ui.LimitedField
import com.zer0state.zzthat.ui.LocalPalette
import com.zer0state.zzthat.ui.Message
import com.zer0state.zzthat.ui.PhotoBoxes
import com.zer0state.zzthat.ui.RoleText
import com.zer0state.zzthat.ui.TextRole
import com.zer0state.zzthat.ui.ZZButton
import com.zer0state.zzthat.ui.ZZScreen
import com.zer0state.zzthat.ui.style
import llc.zerostate.zzthis.design.ZZRadius
import llc.zerostate.zzthis.design.ZZSpace
import llc.zerostate.zzthis.design.ZZType

data class CreateScreenState(
    val gate: Gate,
    val phase: CreateModel.Phase = CreateModel.Phase.Editing,
    val offline: Boolean = false,
    val demo: Boolean = false,
) {
    enum class Gate {
        LOADING,
        SIGNED_OUT,
        UNAVAILABLE,
        FORM,
    }
}

/**
 * Create (design/UX.md, Create). Signed out, it asks for sign-in. While
 * `free_public` is false, or mint is not ready, it is Unavailable and shows
 * no code (FR-018). Offline, the title and body stay as a draft labeled
 * "Not a code yet." and mint is refused (FR-022, FR-024).
 */
@Composable
fun CreateScreen(
    title: String,
    body: String,
    state: CreateScreenState,
    onTitle: (String) -> Unit = {},
    onBody: (String) -> Unit = {},
    onSubmit: () -> Unit = {},
    onSignIn: () -> Unit = {},
) {
    when (state.gate) {
        CreateScreenState.Gate.UNAVAILABLE -> UnavailableScreen(state.demo)
        CreateScreenState.Gate.LOADING -> ZZScreen(demo = state.demo, loading = true) {}
        CreateScreenState.Gate.SIGNED_OUT -> ZZScreen(demo = state.demo) {
            Message(R.string.create_signed_out)
            ZZButton(stringResource(R.string.signin_title), "create.sign_in", onClick = onSignIn)
        }
        CreateScreenState.Gate.FORM -> ZZScreen(demo = state.demo, loading = state.phase == CreateModel.Phase.Minting) {
            if (state.offline) {
                DraftLabel()
                Message(R.string.create_offline)
            }
            LimitedField(R.string.create_title_label, title, onTitle, FieldLimits.TITLE, "create.title")
            LimitedField(R.string.create_body_label, body, onBody, FieldLimits.BODY, "create.body", multiline = true)
            RoleText(stringResource(R.string.create_public_hint), TextRole.SECONDARY)
            val failure = state.phase as? CreateModel.Phase.Failure
            if (failure != null && (failure.key != R.string.create_offline || !state.offline)) Message(failure.key, emphasis = true)
            ZZButton(
                stringResource(R.string.create_submit),
                "create.submit",
                enabled = !state.offline && state.phase != CreateModel.Phase.Minting,
                onClick = onSubmit,
            )
            if (state.offline) RoleText(stringResource(R.string.error_no_connection), TextRole.CAPTION)
        }
    }
}

/** `create.draft_label`: what a draft is called. It has no code words and is never shared or resolved (FR-024). */
@Composable
fun DraftLabel() {
    val palette = LocalPalette.current
    Text(
        text = stringResource(R.string.create_draft_label),
        style = TextStyle(fontFamily = ZZFonts.mono, fontSize = ZZType.TextXs, fontWeight = ZZType.WeightSemibold, color = palette.ink2),
        modifier = Modifier
            .testTag("create.draft_label")
            .drawBehind {
                drawRoundRect(
                    color = palette.ruleStrong,
                    cornerRadius = CornerRadius(ZZRadius.RadiusChip.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .padding(horizontal = ZZSpace.SpaceXs, vertical = ZZSpace.Space3xs),
    )
}

/** Unavailable (FR-018): Create stays on screen and invents no code. */
@Composable
fun UnavailableScreen(demo: Boolean) {
    ZZScreen(demo = demo) {
        Message(R.string.create_unavailable)
    }
}

data class MintedScreenState(
    val canonical: String,
    val checkWord: String?,
    val capReached: Boolean,
    val phase: MintedModel.Phase = MintedModel.Phase.Idle,
    val offline: Boolean = false,
    val demo: Boolean = false,
)

/**
 * Minted code (design/UX.md): the code is the largest text, in the mono font,
 * never truncated. The check word sits in place and has its own label and
 * accessibility label. Re-roll hides at the cap and offline.
 */
@Composable
fun MintedScreen(state: MintedScreenState, onReroll: () -> Unit = {}, onWriteCheck: () -> Unit = {}) {
    ZZScreen(demo = state.demo, loading = state.phase == MintedModel.Phase.Rerolling) {
        RoleText(stringResource(R.string.minted_heading), TextRole.TITLE, Modifier.semantics { heading() })
        CodeText(state.canonical, state.checkWord, ZZType.TextLg, Modifier.testTag("minted.code"))
        state.checkWord?.let {
            CheckWordLabel(it)
            RoleText(stringResource(R.string.minted_check_word_help), TextRole.SECONDARY)
        }
        (state.phase as? MintedModel.Phase.Failure)?.let { Message(it.key) }
        if (state.capReached) {
            if (state.phase != MintedModel.Phase.Failure(R.string.minted_reroll_cap)) Message(R.string.minted_reroll_cap)
        } else if (!state.offline) {
            ZZButton(
                stringResource(R.string.minted_reroll),
                "minted.reroll",
                ButtonKind.SECONDARY,
                enabled = state.phase != MintedModel.Phase.Rerolling,
                onClick = onReroll,
            )
        }
        ZZButton(stringResource(R.string.minted_write_check), "minted.write_check", onClick = onWriteCheck)
    }
}

/** The check word as its own label (runtime.md, Accessibility). */
@Composable
fun CheckWordLabel(word: String) {
    val palette = LocalPalette.current
    val label = stringResource(R.string.minted_check_word_label)
    Row(
        horizontalArrangement = Arrangement.spacedBy(ZZSpace.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("minted.check_word").clearAndSetSemantics {
            contentDescription = label
            stateDescription = word
        },
    ) {
        Text(label, style = TextRole.LABEL.style())
        Text(word, style = TextStyle(fontFamily = ZZFonts.mono, fontSize = ZZType.TextBase, fontWeight = ZZType.WeightSemibold, color = palette.accentInk))
    }
}

data class WriteCheckScreenState(
    val canonical: String,
    val photo: ImageBitmap? = null,
    val candidates: List<ReadCandidate> = emptyList(),
    val reading: Boolean = false,
    val verdict: WriteCheckModel.Verdict? = null,
    @StringRes val message: Int? = null,
    val demo: Boolean = false,
    val cameraAvailable: Boolean = false,
)

/**
 * Write check (design/UX.md): the photo of the handwriting is read on the
 * phone and compared with the minted code. `writecheck.on_device` is always
 * visible. Nothing is uploaded.
 */
@Composable
fun WriteCheckScreen(
    state: WriteCheckScreenState,
    onTakePhoto: () -> Unit = {},
    onPickPhoto: () -> Unit = {},
    onSelect: (ReadCandidate) -> Unit = {},
) {
    ZZScreen(demo = state.demo, loading = state.reading) {
        CodeText(state.canonical)
        RoleText(stringResource(R.string.writecheck_on_device), TextRole.SECONDARY)
        state.photo?.let { photo ->
            PhotoBoxes(photo, state.candidates)
            if (state.verdict == null && state.candidates.size > 1) CandidateList(state.candidates, onSelect)
        }
        when (state.verdict) {
            WriteCheckModel.Verdict.MATCH -> Message(R.string.writecheck_match)
            WriteCheckModel.Verdict.MISMATCH -> Message(R.string.writecheck_mismatch, emphasis = true)
            null -> state.message?.let { Message(it) }
        }
        if (!state.reading) CaptureButtons(retake = state.photo != null, state.cameraAvailable, state.demo, onTakePhoto, onPickPhoto)
    }
}
