package com.zer0state.zzthat.resolve

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.zer0state.zzthat.R
import com.zer0state.zzthat.api.ApiFailure
import com.zer0state.zzthat.api.ApiResult
import com.zer0state.zzthat.api.CallContext
import com.zer0state.zzthat.api.message
import com.zer0state.zzthat.app.AppModel
import com.zer0state.zzthat.app.res
import com.zer0state.zzthat.storage.ResolveCache
import com.zer0state.zzthat.ui.ButtonKind
import com.zer0state.zzthat.ui.CodeText
import com.zer0state.zzthat.ui.Message
import com.zer0state.zzthat.ui.OfflineBanner
import com.zer0state.zzthat.ui.RetryButton
import com.zer0state.zzthat.ui.TextRole
import com.zer0state.zzthat.ui.ZZButton
import com.zer0state.zzthat.ui.ZZScreen
import com.zer0state.zzthat.ui.panel
import com.zer0state.zzthat.ui.style
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import llc.zerostate.zzthis.design.ZZSpace

/** The record a resolve shows: the title and body from the server, and the share text, which is the canonical code (FR-020). */
data class ResolvedView(val canonical: String, val title: String, val body: String, val shareText: String) {
    companion object {
        fun of(entry: ResolveCache.Entry) = ResolvedView(entry.canonical, entry.title, entry.body, entry.shareText)
    }
}

/** What the Resolve screen shows (runtime.md, Screens: Resolve). */
sealed interface ResolvePhase {
    data class Loading(val previous: ResolvedView?) : ResolvePhase

    /** A live record, or the FR-041 copy labeled offline. */
    data class Record(val view: ResolvedView, val offline: Boolean) : ResolvePhase

    /**
     * The person hid this code on this device (FR-035). It resolved as usual,
     * and no title or body reaches the screen.
     */
    data object Hidden : ResolvePhase

    /** The one not-found state (FR-010). */
    data object NotFound : ResolvePhase

    /** Offline and not cached, or cached longer ago than 360 seconds. */
    data object NotOnDevice : ResolvePhase

    /** One sentence from the error table. 429 carries `Retry-After`. */
    data class Failure(@StringRes val key: Int, val retryAfter: Int?) : ResolvePhase
}

/**
 * Resolve with the FR-041 cache in front of `GET /v1/resolve/{code}`. Resolve
 * sends the canonical code only, with no Authorization (FR-016).
 */
object ResolveLoader {
    suspend fun load(canonical: String, app: AppModel): ResolvePhase {
        app.lastCanonical.set(canonical)
        if (!app.isOnline) return offline(canonical, app)
        val fresh = withContext(Dispatchers.IO) { app.cache.lookup(canonical, online = true) }
        if (fresh is ResolveCache.Lookup.Fresh) return ResolvePhase.Record(ResolvedView.of(fresh.entry), offline = false)
        return when (val result = app.service.resolve(canonical)) {
            is ApiResult.Ok -> {
                val response = result.value
                // A no-store answer is never kept, and it replaces an older copy.
                withContext(Dispatchers.IO) {
                    if (response.cacheable) app.cache.store(response, canonical) else app.cache.remove(canonical)
                }
                val record = response.record
                ResolvePhase.Record(ResolvedView(record.canonical, record.record.title, record.record.body, record.share.text), offline = false)
            }
            is ApiResult.Err -> when (val failure = result.failure) {
                ApiFailure.NotFound -> {
                    withContext(Dispatchers.IO) { app.cache.remove(canonical) }
                    ResolvePhase.NotFound
                }
                ApiFailure.NoConnection -> offline(canonical, app)
                is ApiFailure.RateLimited -> ResolvePhase.Failure(R.string.resolve_rate_limited, failure.retryAfter)
                else -> ResolvePhase.Failure(failure.message(CallContext.RESOLVE).res, null)
            }
        }
    }

    /** Offline: the copy, labeled offline, until 360 seconds after the fetch. */
    suspend fun offline(canonical: String, app: AppModel): ResolvePhase {
        val lookup = withContext(Dispatchers.IO) { app.cache.lookup(canonical, online = false) }
        return if (lookup is ResolveCache.Lookup.Offline) ResolvePhase.Record(ResolvedView.of(lookup.entry), offline = true) else ResolvePhase.NotOnDevice
    }
}

/**
 * One Resolve screen. A code hidden on this device resolves through the same
 * path, scanned or typed, and then shows [ResolvePhase.Hidden] (FR-035). The
 * resolved record stays in this model only, so Unhide shows it again at once
 * and without a call.
 */
class ResolveModel(val canonical: String) {
    var phase by mutableStateOf<ResolvePhase>(ResolvePhase.Loading(null))
        private set

    /** The last record this screen resolved, shown or hidden. */
    private var record: ResolvePhase.Record? = null

    /** While a 429 wait runs, Try again stays off (FR-033). */
    var waiting by mutableStateOf(false)
        private set
    private var loaded = false

    suspend fun loadOnce(app: AppModel) {
        if (loaded) return
        loaded = true
        load(app)
    }

    suspend fun load(app: AppModel) {
        // A hidden record is not kept on screen while loading.
        phase = ResolvePhase.Loading((phase as? ResolvePhase.Record)?.view)
        val next = ResolveLoader.load(canonical, app)
        record = next as? ResolvePhase.Record
        phase = if (next is ResolvePhase.Record && app.hidden.isHidden(canonical)) ResolvePhase.Hidden else next
        if (next is ResolvePhase.Failure && next.retryAfter != null) {
            waiting = true
            delay(maxOf(next.retryAfter, 1) * 1000L)
            waiting = false
        }
    }

    /** Hide this code on this device. The title and body leave the screen at once. No server call. */
    suspend fun hide(app: AppModel) {
        app.hide(canonical)
        sync(app)
    }

    /** Unhide this code on this device. The record shows again at once. No server call. */
    suspend fun unhide(app: AppModel) {
        app.unhide(canonical)
        sync(app)
    }

    /** After a hide or unhide here or elsewhere (the Report sheet, the Account reset), show the stored state. No server call. */
    suspend fun sync(app: AppModel) {
        val shown = record ?: return
        if (phase !is ResolvePhase.Record && phase !is ResolvePhase.Hidden) return
        phase = if (app.hidden.isHidden(canonical)) ResolvePhase.Hidden else shown
    }
}

/**
 * Resolve (US3, design/UX.md Resolve): the title and body as plain text, or
 * the one not-found state. Share sends the canonical code as text, never a
 * URL (FR-020). Report opens the FR-035 screen, and Hide this code hides it on
 * this device. A hidden code shows `resolve.hidden` and Unhide in place of
 * its title and body (FR-035).
 */
@Composable
fun ResolveScreen(
    canonical: String,
    phase: ResolvePhase,
    demo: Boolean,
    waiting: Boolean = false,
    onRetry: () -> Unit = {},
    onShare: (String) -> Unit = {},
    onReport: () -> Unit = {},
    onHide: () -> Unit = {},
    onUnhide: () -> Unit = {},
) {
    ZZScreen(demo = demo, loading = phase is ResolvePhase.Loading) {
        when (phase) {
            is ResolvePhase.Loading -> phase.previous?.let { RecordBlock(it) } ?: CodeText(canonical)
            is ResolvePhase.Record -> {
                if (phase.offline) OfflineBanner()
                RecordBlock(phase.view)
                ZZButton(stringResource(R.string.resolve_share), "resolve.share") { onShare(phase.view.shareText) }
                ZZButton(stringResource(R.string.resolve_report), "resolve.report", ButtonKind.SECONDARY, onClick = onReport)
                ZZButton(stringResource(R.string.resolve_hide), "resolve.hide", ButtonKind.SECONDARY, onClick = onHide)
            }
            ResolvePhase.Hidden -> {
                CodeText(canonical)
                Message(R.string.resolve_hidden)
                ZZButton(stringResource(R.string.resolve_unhide), "resolve.unhide", ButtonKind.SECONDARY, onClick = onUnhide)
            }
            ResolvePhase.NotFound -> {
                CodeText(canonical)
                Message(R.string.resolve_not_found)
            }
            ResolvePhase.NotOnDevice -> {
                CodeText(canonical)
                Message(R.string.resolve_not_on_device)
                RetryButton(waiting = false, onClick = onRetry)
            }
            is ResolvePhase.Failure -> {
                CodeText(canonical)
                Message(phase.key)
                RetryButton(waiting = waiting, onClick = onRetry)
            }
        }
    }
}

/**
 * The record: the code in mono, then the title and the body, both as the
 * server sent them. The body is plain text and links in it are not tappable
 * (FR-046).
 */
@Composable
fun RecordBlock(view: ResolvedView) {
    Column(verticalArrangement = Arrangement.spacedBy(ZZSpace.SpaceXs), modifier = Modifier.panel()) {
        CodeText(view.canonical)
        Text(view.title, style = TextRole.HEADING.style(), modifier = Modifier.testTag("record.title").semantics { heading() })
        if (view.body.isNotEmpty()) {
            SelectionContainer {
                Text(view.body, style = TextRole.BODY.style(), modifier = Modifier.testTag("record.body"))
            }
        }
    }
}
