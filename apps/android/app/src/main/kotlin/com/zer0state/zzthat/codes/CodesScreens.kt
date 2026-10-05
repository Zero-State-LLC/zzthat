package com.zer0state.zzthat.codes

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import com.zer0state.zzthat.ui.ChipShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.selection.SelectionContainer
import com.zer0state.zzthat.R
import com.zer0state.zzthat.api.generated.models.OwnedCode
import com.zer0state.zzthat.api.generated.models.OwnerRecord
import com.zer0state.zzthat.app.res
import com.zer0state.zzthat.ui.ButtonKind
import com.zer0state.zzthat.ui.CodeText
import com.zer0state.zzthat.ui.FieldLimits
import com.zer0state.zzthat.ui.LimitedField
import com.zer0state.zzthat.ui.LocalPalette
import com.zer0state.zzthat.ui.Message
import com.zer0state.zzthat.ui.MinTouch
import com.zer0state.zzthat.ui.RetryButton
import com.zer0state.zzthat.ui.RoleText
import com.zer0state.zzthat.ui.TextRole
import com.zer0state.zzthat.ui.ZZButton
import com.zer0state.zzthat.ui.ZZScreen
import com.zer0state.zzthat.ui.panel
import com.zer0state.zzthat.ui.style
import llc.zerostate.zzthis.design.ZZSpace
import llc.zerostate.zzthis.design.ZZType

data class CodesScreenState(
    val signedIn: Boolean,
    val rows: List<OwnedCode> = emptyList(),
    val phase: CodesModel.Phase = CodesModel.Phase.Idle,
    val demo: Boolean = false,
)

/** My codes (design/UX.md): rows with the code in mono, the record title, and a status word when the code is not active. */
@Composable
fun CodesScreen(
    state: CodesScreenState,
    onSignIn: () -> Unit = {},
    onSelect: (OwnedCode) -> Unit = {},
    onLastRow: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    ZZScreen(demo = state.demo, loading = state.phase == CodesModel.Phase.Loading) {
        val failure = state.phase as? CodesModel.Phase.Failure
        when {
            !state.signedIn -> {
                Message(R.string.codes_signed_out)
                ZZButton(stringResource(R.string.signin_title), "codes.sign_in", onClick = onSignIn)
            }
            failure != null -> {
                Message(failure.key)
                RetryButton(waiting = false, onClick = onRetry)
            }
            state.rows.isEmpty() && state.phase == CodesModel.Phase.Loaded -> Message(R.string.codes_empty)
            else -> Column(verticalArrangement = Arrangement.spacedBy(ZZSpace.SpaceXs)) {
                state.rows.forEach { row ->
                    OwnedCodeRow(row, Modifier.testTag("codes.row.${row.id}").clickable(role = Role.Button) { onSelect(row) })
                    if (row.id == state.rows.last().id) LaunchedEffect(row.id) { onLastRow() }
                }
            }
        }
    }
}

@Composable
fun OwnedCodeRow(row: OwnedCode, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ZZSpace.Space3xs),
        modifier = modifier
            .defaultMinSize(minHeight = MinTouch)
            .semantics(mergeDescendants = true) {}
            .panel(),
    ) {
        CodeText(row.canonical, row.checkWord, ZZType.TextBase)
        Text(row.title, style = TextRole.BODY.style())
        if (row.status != OwnedCode.Status.active) RoleText(stringResource(row.status.res), TextRole.CAPTION)
    }
}

data class CodeDetailScreenState(
    val row: OwnedCode? = null,
    val record: OwnerRecord? = null,
    val phase: CodeDetailModel.Phase = CodeDetailModel.Phase.Loading,
    val revokePhase: CodeDetailModel.RevokePhase = CodeDetailModel.RevokePhase.Idle,
    val saved: Boolean = false,
    val demo: Boolean = false,
)

/**
 * Code detail (design/UX.md): the code, the title, the body as plain text,
 * and Share, Edit, and Revoke. Revoke shows only while the code is active. A
 * revoked code shows `detail.revoked_note` and no Edit.
 */
@Composable
fun CodeDetailScreen(
    state: CodeDetailScreenState,
    onShare: (String) -> Unit = {},
    onEdit: () -> Unit = {},
    onRevoke: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val loading = state.phase == CodeDetailModel.Phase.Loading || state.revokePhase == CodeDetailModel.RevokePhase.Revoking
    ZZScreen(demo = state.demo, loading = loading) {
        when (val phase = state.phase) {
            CodeDetailModel.Phase.NotFound -> Message(R.string.resolve_not_found)
            is CodeDetailModel.Phase.Failure -> {
                Message(phase.key)
                RetryButton(waiting = false, onClick = onRetry)
            }
            CodeDetailModel.Phase.Loading, CodeDetailModel.Phase.Loaded -> state.row?.let { row ->
                Column(verticalArrangement = Arrangement.spacedBy(ZZSpace.SpaceXs), modifier = Modifier.panel()) {
                    CodeText(row.canonical, row.checkWord)
                    Text(state.record?.title ?: row.title, style = TextRole.HEADING.style(), modifier = Modifier.semantics { heading() })
                    val body = state.record?.body
                    if (!body.isNullOrEmpty()) SelectionContainer { Text(body, style = TextRole.BODY.style()) }
                    if (row.status != OwnedCode.Status.active) RoleText(stringResource(row.status.res), TextRole.CAPTION)
                }
                if (state.saved) Message(R.string.edit_saved)
                if (row.status == OwnedCode.Status.revoked) Message(R.string.detail_revoked_note)
                (state.revokePhase as? CodeDetailModel.RevokePhase.Failure)?.let { Message(it.key, emphasis = true) }
                if (phase == CodeDetailModel.Phase.Loaded) {
                    ZZButton(stringResource(R.string.resolve_share), "detail.share") { onShare(row.canonical) }
                    if (row.status != OwnedCode.Status.revoked) {
                        ZZButton(stringResource(R.string.detail_edit), "detail.edit", ButtonKind.SECONDARY, onClick = onEdit)
                    }
                    if (row.status == OwnedCode.Status.active) {
                        ZZButton(
                            stringResource(R.string.detail_revoke),
                            "detail.revoke",
                            ButtonKind.DESTRUCTIVE,
                            enabled = state.revokePhase != CodeDetailModel.RevokePhase.Revoking,
                            onClick = onRevoke,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Revoke confirm and Delete confirm (design/UX.md): a system dialog with the
 * title, the body, a destructive action, and Cancel, which is the default:
 * back and a tap outside cancel.
 */
@Composable
fun ConfirmDialog(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes action: Int,
    tag: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        ConfirmCard(title, body, action, tag, onConfirm, onCancel)
    }
}

/** The dialog's card, also drawn on its own for the snapshots. */
@Composable
fun ConfirmCard(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes action: Int,
    tag: String,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val palette = LocalPalette.current
    Column(
        verticalArrangement = Arrangement.spacedBy(ZZSpace.SpaceSm),
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .background(palette.panel, ChipShape)
            .border(ZZSpace.RuleHair, palette.rule, ChipShape)
            .padding(ZZSpace.SpaceMd),
    ) {
        Text(stringResource(title), style = TextRole.TITLE.style(), modifier = Modifier.semantics { heading() })
        Text(stringResource(body), style = TextRole.BODY.style())
        Row(horizontalArrangement = Arrangement.spacedBy(ZZSpace.SpaceXs, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            DialogButton(stringResource(R.string.common_cancel), "$tag.cancel", palette.ink, onCancel)
            DialogButton(stringResource(action), "$tag.confirm", palette.accentInk, onConfirm)
        }
    }
}

@Composable
private fun DialogButton(text: String, tag: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ZZSpace.SpaceXs),
    ) {
        Text(text, style = TextRole.LABEL.style().copy(fontSize = ZZType.TextBase, color = color))
    }
}

data class EditRecordScreenState(
    val phase: EditRecordModel.Phase = EditRecordModel.Phase.Loading,
    val demo: Boolean = false,
    val offline: Boolean = false,
)

/** Edit record (design/UX.md): the title is required; one Save; a failure keeps the text in the fields. */
@Composable
fun EditRecordScreen(
    title: String,
    body: String,
    state: EditRecordScreenState,
    onTitle: (String) -> Unit = {},
    onBody: (String) -> Unit = {},
    onSave: () -> Unit = {},
) {
    val busy = state.phase == EditRecordModel.Phase.Loading || state.phase == EditRecordModel.Phase.Saving
    ZZScreen(demo = state.demo, loading = busy) {
        when (val phase = state.phase) {
            EditRecordModel.Phase.NotFound -> Message(R.string.resolve_not_found)
            is EditRecordModel.Phase.LoadFailure -> Message(phase.key)
            else -> {
                LimitedField(R.string.create_title_label, title, onTitle, FieldLimits.TITLE, "edit.title")
                LimitedField(R.string.create_body_label, body, onBody, FieldLimits.BODY, "edit.body", multiline = true)
                RoleText(stringResource(R.string.create_public_hint), TextRole.SECONDARY)
                if (phase is EditRecordModel.Phase.Failure) {
                    Message(phase.key, emphasis = true)
                } else if (FieldLimits.isBlank(title) && phase == EditRecordModel.Phase.Editing) {
                    Message(R.string.create_title_required)
                }
                ZZButton(stringResource(R.string.edit_save), "edit.save", enabled = !state.offline && !busy, onClick = onSave)
                if (state.offline) RoleText(stringResource(R.string.error_no_connection), TextRole.CAPTION)
            }
        }
    }
}
