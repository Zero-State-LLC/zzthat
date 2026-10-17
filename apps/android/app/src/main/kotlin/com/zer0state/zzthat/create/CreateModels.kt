package com.zer0state.zzthat.create

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zer0state.zzthat.R
import com.zer0state.zzthat.api.ApiFailure
import com.zer0state.zzthat.api.ApiResult
import com.zer0state.zzthat.api.CallContext
import com.zer0state.zzthat.api.MessageKey
import com.zer0state.zzthat.api.generated.models.Code
import com.zer0state.zzthat.api.message
import com.zer0state.zzthat.app.AppModel
import com.zer0state.zzthat.app.res
import com.zer0state.zzthat.grammar.PairRead
import com.zer0state.zzthat.grammar.ReadCandidate
import com.zer0state.zzthat.scan.BitmapImage
import com.zer0state.zzthat.scan.CameraPipeline
import com.zer0state.zzthat.scan.CameraSession
import com.zer0state.zzthat.scan.CaptureImage
import com.zer0state.zzthat.scan.ScanRow
import com.zer0state.zzthat.ui.FieldLimits

/**
 * A 429 on mint or re-roll (FR-033, T014; design/UX.md, Create: the daily
 * limit). The client waits for `Retry-After` and keeps Get a code and Get
 * different words off until then.
 */
object CreateWait {
    /**
     * A `Retry-After` longer than this, in seconds, is the RM-072 daily cap,
     * which can run to the next UTC midnight, and shows `create.daily_limit`.
     * A shorter one is a burst limit and keeps `resolve.rate_limited`. UX.md
     * names the daily cap by its long wait, and no burst window in zzThis
     * spec 005 runs as long as an hour.
     */
    const val DAILY_LIMIT_SECONDS = 3600

    @StringRes
    fun message(retryAfter: Int): Int = if (retryAfter > DAILY_LIMIT_SECONDS) R.string.create_daily_limit else R.string.resolve_rate_limited

    /** When the wait ends, in wall-clock milliseconds. */
    fun until(retryAfter: Int, nowMillis: Long = System.currentTimeMillis()): Long = nowMillis + maxOf(retryAfter, 1) * 1000L
}

/**
 * Create (US1): mint a wordlist code. The server chooses the words and the
 * check word (ZQ11), so there is no field for code words (FR-019, T034).
 */
class CreateModel {
    sealed interface Phase {
        data object Editing : Phase

        data object Minting : Phase

        data class Failure(@StringRes val key: Int) : Phase
    }

    var title by mutableStateOf("")
    var body by mutableStateOf("")
    var phase by mutableStateOf<Phase>(Phase.Editing)

    /** A mint answered `scope-unavailable` or `not-ready` (FR-018). */
    var mintUnavailable by mutableStateOf(false)
        private set

    /**
     * The Terms step (design/UX.md, Terms; D-2026-10-13-04): the person
     * confirms they are 18 or older and accepts the Terms. Both are needed
     * before the first create on this device, and again when `terms.version`
     * changes. Client-only: nothing about it is sent.
     */
    var ageConfirmed by mutableStateOf(false)
    var termsAccepted by mutableStateOf(false)

    /** After a 429, Get a code stays off until this wall-clock time, in milliseconds (FR-033). */
    var retryAt by mutableStateOf<Long?>(null)
        private set

    val waiting: Boolean get() = retryAt != null

    /** The `Retry-After` wait is over: Get a code turns back on. */
    fun endWait() {
        retryAt = null
    }

    /** Whether Get a code may run: the Terms step is done, or not needed. */
    fun termsDone(app: AppModel): Boolean = !app.needsTerms || (ageConfirmed && termsAccepted)

    private var draftLoaded = false
    private val minted = mutableMapOf<String, MintedModel>()

    /** The Minted code screen for a code this tab minted, kept while the tab lives. */
    fun mintedModel(codeId: String): MintedModel? = minted[codeId]

    /** The local draft, labeled "Not a code yet." (FR-024). It holds no code words. */
    suspend fun loadDraft(app: AppModel) {
        if (draftLoaded) return
        draftLoaded = true
        app.drafts.draft()?.let {
            title = it.title
            body = it.body
        }
    }

    suspend fun saveDraft(app: AppModel) {
        app.drafts.save(title, body)
    }

    val hasDraft: Boolean get() = !FieldLimits.isBlank(title) || !FieldLimits.isBlank(body)

    /** `POST /v1/codes`. Refused offline, never queued as a success (FR-022). */
    suspend fun mint(app: AppModel): Code? {
        retryAt?.let {
            if (System.currentTimeMillis() < it) return null
            endWait()
        }
        if (FieldLimits.isBlank(title)) {
            phase = Phase.Failure(R.string.create_title_required)
            return null
        }
        // Both confirmations come before Create continues (design/UX.md, Terms).
        if (!termsDone(app)) return null
        if (app.needsTerms) {
            app.acceptTerms()
            // Consumed: if the step shows again (a new version, or after
            // account deletion), it starts unchecked.
            ageConfirmed = false
            termsAccepted = false
        }
        if (!app.isOnline) {
            saveDraft(app)
            phase = Phase.Failure(R.string.create_offline)
            return null
        }
        phase = Phase.Minting
        when (val result = app.service.mint(title.trim(), body.trim())) {
            is ApiResult.Ok -> {
                app.ownerMinted()
                app.drafts.discard()
                title = ""
                body = ""
                phase = Phase.Editing
                minted[result.value.id] = MintedModel(result.value)
                return result.value
            }
            is ApiResult.Err -> when (val failure = result.failure) {
                ApiFailure.NoConnection -> {
                    saveDraft(app)
                    phase = Phase.Failure(R.string.create_offline)
                }
                ApiFailure.Unauthorized -> {
                    phase = Phase.Failure(R.string.common_sign_in_needed)
                    app.needsSignIn()
                }
                is ApiFailure.RateLimited -> {
                    // The title and body stay as typed; Get a code waits (FR-033).
                    retryAt = CreateWait.until(failure.retryAfter)
                    phase = Phase.Failure(CreateWait.message(failure.retryAfter))
                }
                else -> {
                    val key = failure.message(CallContext.MINT)
                    if (key == MessageKey.CREATE_UNAVAILABLE) mintUnavailable = true
                    // The title and body stay as typed (DEP-005).
                    phase = Phase.Failure(key.res)
                }
            }
        }
        return null
    }
}

/**
 * Minted code (design/UX.md, Minted code): the canonical form, the check
 * word, and the re-rolls `rerolls_remaining` allows, 5 for a new code
 * (FR-017, RM-125). The suggested-word picker (zzThis
 * RM-113) offers the server's words with Keep these words beside re-roll.
 */
class MintedModel(code: Code) {
    sealed interface Phase {
        data object Idle : Phase

        data object Rerolling : Phase

        data class Failure(@StringRes val key: Int) : Phase
    }

    var code by mutableStateOf(code)
        private set
    var capReached by mutableStateOf(code.rerollsRemaining <= 0)
        private set
    var phase by mutableStateOf<Phase>(Phase.Idle)
        private set

    /** After a 429, Get different words stays off until this wall-clock time, in milliseconds (FR-033). */
    var retryAt by mutableStateOf<Long?>(null)
        private set

    val waiting: Boolean get() = retryAt != null

    /** The `Retry-After` wait is over: Get different words turns back on. */
    fun endWait() {
        retryAt = null
    }

    /** The person kept these words, so the picker closes and re-roll is gone. */
    var kept by mutableStateOf(false)
        private set

    /**
     * Keep these words (RM-113). Local only: no call, and nothing about the
     * code is sent. The words stay, as they do at the re-roll cap.
     */
    fun keep() {
        kept = true
    }

    /** `POST /v1/codes/{id}/reroll`. Each tap replaces the current code; at the cap, or once the code was resolved, the words stay. */
    suspend fun reroll(app: AppModel) {
        // Kept words stay, as at the cap.
        if (kept) return
        retryAt?.let {
            if (System.currentTimeMillis() < it) return
            endWait()
        }
        if (!app.isOnline) {
            phase = Phase.Failure(R.string.error_no_connection)
            return
        }
        phase = Phase.Rerolling
        val previous = code
        when (val result = app.service.reroll(previous.id)) {
            is ApiResult.Ok -> {
                app.ownerChanged(previous.canonical)
                code = result.value
                capReached = result.value.rerollsRemaining <= 0
                phase = Phase.Idle
            }
            is ApiResult.Err -> when (val failure = result.failure) {
                ApiFailure.NoConnection -> phase = Phase.Failure(R.string.error_no_connection)
                ApiFailure.Unauthorized -> {
                    phase = Phase.Failure(R.string.common_sign_in_needed)
                    app.needsSignIn()
                }
                is ApiFailure.RateLimited -> {
                    // The words stay; Get different words waits (FR-033).
                    retryAt = CreateWait.until(failure.retryAfter)
                    phase = Phase.Failure(CreateWait.message(failure.retryAfter))
                }
                else -> {
                    val key = failure.message(CallContext.REROLL)
                    if (key == MessageKey.MINTED_REROLL_CAP) capReached = true
                    phase = Phase.Failure(key.res)
                }
            }
        }
    }
}

/**
 * Write check (FR-037): the camera read pipeline on the person's photo,
 * compared with the minted code on the device. Nothing is uploaded. The
 * comparison is not a resolve, so confirm-only does not apply to it.
 */
class WriteCheckModel(val canonical: String) {
    enum class Verdict {
        MATCH,
        MISMATCH,
    }

    val session = CameraSession()

    val photo: Bitmap? get() = (session.image as? BitmapImage)?.bitmap
    val rows: List<ScanRow> get() = session.rows
    val reading: Boolean get() = session.reading

    var verdict by mutableStateOf<Verdict?>(null)
        private set

    @get:StringRes
    var message by mutableStateOf<Int?>(null)
        private set

    suspend fun read(image: CaptureImage, app: AppModel, camera: CameraPipeline) {
        verdict = null
        message = null
        when (val outcome = session.begin(image, camera, app.pipeline)) {
            is CameraSession.Outcome.Empty -> message = R.string.scan_no_code
            is CameraSession.Outcome.Rows -> outcome.read?.let(::settle)
            is CameraSession.Outcome.PairDone -> Unit
        }
    }

    /** The person picked the candidate their photo shows (spec 004 FR-017). A pair not read yet is read now, and only it. */
    suspend fun pick(row: ScanRow, app: AppModel, camera: CameraPipeline) {
        when (row) {
            is ScanRow.Read -> pick(row.candidate)
            is ScanRow.Unread -> {
                val done = session.open(row, camera, app.pipeline) as? CameraSession.Outcome.PairDone
                if (done == null) pick(null) else settle(done.read)
            }
        }
    }

    /** One reading decides; several wait for a tap; none is a message. */
    private fun settle(read: PairRead) {
        when (read.candidates.size) {
            0 -> message = R.string.scan_no_code
            1 -> pick(read.candidates.single())
            else -> Unit
        }
    }

    private fun pick(candidate: ReadCandidate?) {
        verdict = if (candidate?.canonical == canonical) Verdict.MATCH else Verdict.MISMATCH
        message = null
    }

    fun cameraIsOff() {
        message = R.string.scan_camera_off
    }

    /** The check ends: its photo is deleted (FR-004) and the evidence dropped. */
    fun end(app: AppModel) {
        app.photos.end()
        session.clear()
        verdict = null
        message = null
    }
}
