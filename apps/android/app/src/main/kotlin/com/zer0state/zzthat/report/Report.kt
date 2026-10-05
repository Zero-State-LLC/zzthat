package com.zer0state.zzthat.report

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.zer0state.zzthat.R
import com.zer0state.zzthat.api.ApiFailure
import com.zer0state.zzthat.api.ApiResult
import com.zer0state.zzthat.api.CallContext
import com.zer0state.zzthat.api.generated.models.ReportRequest
import com.zer0state.zzthat.api.message
import com.zer0state.zzthat.app.AppModel
import com.zer0state.zzthat.app.res
import com.zer0state.zzthat.ui.ButtonKind
import com.zer0state.zzthat.ui.ChipShape
import com.zer0state.zzthat.ui.FieldLimits
import com.zer0state.zzthat.ui.LimitedField
import com.zer0state.zzthat.ui.LocalPalette
import com.zer0state.zzthat.ui.Message
import com.zer0state.zzthat.ui.MinTouch
import com.zer0state.zzthat.ui.RoleText
import com.zer0state.zzthat.ui.TextRole
import com.zer0state.zzthat.ui.ZZButton
import com.zer0state.zzthat.ui.ZZScreen
import com.zer0state.zzthat.ui.style
import kotlinx.coroutines.delay
import llc.zerostate.zzthis.design.ZZSpace

/**
 * Report (FR-035, design/UX.md Report): the four reasons, an optional note
 * of up to 500 characters, and one send. The answer never says whether the
 * code exists. No block list is kept.
 */
data class ReportState(
    val reason: ReportRequest.Reason? = null,
    val note: String = "",
    val phase: Phase = Phase.Editing,
    val offline: Boolean = false,
    val demo: Boolean = false,
    /** After a 429, Send waits for `Retry-After` (FR-033). */
    val waiting: Boolean = false,
) {
    sealed interface Phase {
        data object Editing : Phase

        data object Sending : Phase

        data object Sent : Phase

        data class Failure(@StringRes val key: Int) : Phase
    }

    val canSend: Boolean get() = reason != null && !offline && !waiting && phase != Phase.Sending && phase != Phase.Sent
}

/** The four FR-035 reasons, in the order the copy lists them. */
val REPORT_REASONS = listOf(
    ReportRequest.Reason.spam,
    ReportRequest.Reason.harassment,
    ReportRequest.Reason.personalMinusData,
    ReportRequest.Reason.other,
)

@Composable
fun ReportScreen(
    state: ReportState,
    onReason: (ReportRequest.Reason) -> Unit = {},
    onNote: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    ZZScreen(demo = state.demo, loading = state.phase == ReportState.Phase.Sending) {
        RoleText(stringResource(R.string.report_title), TextRole.HEADING, Modifier.semantics { heading() })
        if (state.phase == ReportState.Phase.Sent) {
            Message(R.string.report_sent)
            ZZButton(stringResource(R.string.common_done), "report.done", onClick = onDone)
        } else {
            ReasonPicker(state.reason, onReason)
            LimitedField(
                label = R.string.report_note_label,
                value = state.note,
                onValueChange = onNote,
                limit = FieldLimits.REPORT_NOTE,
                tag = "report.note",
                multiline = true,
                countsTrimmed = false,
            )
            (state.phase as? ReportState.Phase.Failure)?.let { Message(it.key) }
            if (state.offline) Message(R.string.error_no_connection)
            ZZButton(stringResource(R.string.report_send), "report.send", enabled = state.canSend, onClick = onSend)
            ZZButton(stringResource(R.string.common_cancel), "report.cancel", ButtonKind.SECONDARY, onClick = onDone)
        }
    }
}

/** The four FR-035 reasons as one choice. */
@Composable
fun ReasonPicker(selection: ReportRequest.Reason?, onSelect: (ReportRequest.Reason) -> Unit) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .background(palette.panel, ChipShape)
            .border(ZZSpace.RuleHair, palette.rule, ChipShape),
    ) {
        REPORT_REASONS.forEachIndexed { index, reason ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .testTag("report.reason.${reason.value}")
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MinTouch)
                    .selectable(selected = selection == reason, role = Role.RadioButton) { onSelect(reason) }
                    .padding(start = ZZSpace.SpaceSm),
            ) {
                Text(stringResource(reason.res), style = TextRole.BODY.style(), modifier = Modifier.weight(1f))
                RadioButton(
                    selected = selection == reason,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = palette.accentInk, unselectedColor = palette.ruleStrong),
                    modifier = Modifier.padding(end = ZZSpace.SpaceXs),
                )
            }
            if (index < REPORT_REASONS.lastIndex) HorizontalDivider(color = palette.rule, thickness = ZZSpace.RuleHair)
        }
    }
}

/**
 * Sends one report (FR-035). The answer never says whether the code exists.
 * Signed out, the report carries no bearer (FR-016). After a 429, Send waits
 * for `Retry-After` (FR-033).
 */
class ReportModel(val canonical: String) {
    var state by mutableStateOf(ReportState())

    suspend fun send(app: AppModel) {
        val reason = state.reason ?: return
        if (!app.isOnline) {
            state = state.copy(offline = true)
            return
        }
        // Not while a send, a sent answer, or a Retry-After wait is on.
        if (!state.canSend) return
        state = state.copy(phase = ReportState.Phase.Sending)
        when (val result = app.service.report(canonical, reason, state.note)) {
            is ApiResult.Ok -> state = state.copy(phase = ReportState.Phase.Sent)
            is ApiResult.Err -> when (val failure = result.failure) {
                ApiFailure.NoConnection -> state = state.copy(phase = ReportState.Phase.Failure(R.string.error_no_connection))
                is ApiFailure.RateLimited -> {
                    state = state.copy(phase = ReportState.Phase.Failure(R.string.resolve_rate_limited), waiting = true)
                    delay(maxOf(failure.retryAfter, 1) * 1000L)
                    state = state.copy(waiting = false)
                }
                else -> state = state.copy(phase = ReportState.Phase.Failure(failure.message(CallContext.REPORT).res))
            }
        }
    }
}
