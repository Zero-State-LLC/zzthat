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
import com.zer0state.zzthat.grammar.ReadCandidate
import com.zer0state.zzthat.scan.TextReader
import com.zer0state.zzthat.ui.FieldLimits

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
        if (FieldLimits.isBlank(title)) {
            phase = Phase.Failure(R.string.create_title_required)
            return null
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

/** Minted code (design/UX.md, Minted code): the canonical form, the check word, and up to 3 re-rolls (FR-017). */
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

    /** `POST /v1/codes/{id}/reroll`. Each tap replaces the current code; at the cap, or once the code was resolved, the words stay. */
    suspend fun reroll(app: AppModel) {
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
 * Write check (FR-037): the read pipeline on the person's photo, compared
 * with the minted code on the device. Nothing is uploaded.
 */
class WriteCheckModel(val canonical: String) {
    enum class Verdict {
        MATCH,
        MISMATCH,
    }

    var photo by mutableStateOf<Bitmap?>(null)
        private set
    var candidates by mutableStateOf<List<ReadCandidate>>(emptyList())
        private set
    var reading by mutableStateOf(false)
        private set
    var verdict by mutableStateOf<Verdict?>(null)
        private set

    @get:StringRes
    var message by mutableStateOf<Int?>(null)
        private set

    suspend fun read(bitmap: Bitmap, app: AppModel, reader: TextReader) {
        photo = bitmap
        verdict = null
        message = null
        candidates = emptyList()
        reading = true
        val lines = try {
            reader.read(bitmap)
        } catch (error: Exception) {
            emptyList()
        }
        candidates = app.pipeline.read(lines).candidates
        reading = false
        val codes = candidates.filter { it.canonical != null }
        if (candidates.isEmpty()) {
            message = R.string.scan_no_code
        } else if (codes.size == 1 && candidates.size == 1) {
            pick(codes.first())
        }
    }

    fun cameraIsOff() {
        message = R.string.scan_camera_off
    }

    /** The person picked the candidate their photo shows (spec 004 FR-017). */
    fun pick(candidate: ReadCandidate) {
        verdict = if (candidate.canonical == canonical) Verdict.MATCH else Verdict.MISMATCH
        message = null
    }

    /** The check ends: its photo is deleted (FR-004). */
    fun end(app: AppModel) {
        app.photos.end()
        photo = null
        candidates = emptyList()
        verdict = null
        message = null
    }
}
