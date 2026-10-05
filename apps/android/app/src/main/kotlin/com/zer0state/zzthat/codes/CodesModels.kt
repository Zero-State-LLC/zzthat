package com.zer0state.zzthat.codes

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zer0state.zzthat.R
import com.zer0state.zzthat.api.ApiFailure
import com.zer0state.zzthat.api.ApiResult
import com.zer0state.zzthat.api.CallContext
import com.zer0state.zzthat.api.generated.models.OwnedCode
import com.zer0state.zzthat.api.generated.models.OwnerRecord
import com.zer0state.zzthat.api.message
import com.zer0state.zzthat.app.AppModel
import com.zer0state.zzthat.app.res
import com.zer0state.zzthat.ui.FieldLimits

/** One sentence for an owner read (spec.md, Errors). A 401 the refresh could not fix asks the person to sign in again. */
suspend fun ownerMessage(failure: ApiFailure, app: AppModel): Int = when (failure) {
    ApiFailure.NoConnection -> R.string.error_no_connection
    ApiFailure.Unauthorized -> {
        app.needsSignIn()
        R.string.common_sign_in_needed
    }
    else -> failure.message(CallContext.OWNER_READ).res
}

/**
 * My codes (US5, FR-021): `GET /v1/me/codes`, 50 a page, following
 * `next_cursor`. Owner responses are `no-store`, so no list is kept on the
 * device and offline shows `error.no_connection`.
 */
class CodesModel {
    sealed interface Phase {
        data object Idle : Phase

        data object Loading : Phase

        data object Loaded : Phase

        data class Failure(@StringRes val key: Int) : Phase
    }

    var rows by mutableStateOf<List<OwnedCode>>(emptyList())
        private set
    var nextCursor by mutableStateOf<String?>(null)
        private set
    var phase by mutableStateOf<Phase>(Phase.Idle)
        private set
    private var loadingMore = false
    private var loadedRevision: Int? = null

    fun row(id: String): OwnedCode? = rows.firstOrNull { it.id == id }

    suspend fun reloadIfNeeded(app: AppModel) {
        if (loadedRevision != app.ownerRevision || phase == Phase.Idle) reload(app)
    }

    suspend fun reload(app: AppModel) {
        loadedRevision = app.ownerRevision
        if (!app.isSignedIn) {
            rows = emptyList()
            phase = Phase.Idle
            return
        }
        if (!app.isOnline) {
            rows = emptyList()
            phase = Phase.Failure(R.string.error_no_connection)
            return
        }
        phase = Phase.Loading
        when (val result = app.service.myCodes()) {
            is ApiResult.Ok -> {
                rows = result.value.codes
                nextCursor = result.value.nextCursor
                phase = Phase.Loaded
            }
            is ApiResult.Err -> {
                rows = emptyList()
                phase = Phase.Failure(ownerMessage(result.failure, app))
            }
        }
    }

    suspend fun loadMore(app: AppModel) {
        val cursor = nextCursor ?: return
        if (loadingMore || !app.isOnline) return
        loadingMore = true
        try {
            val result = app.service.myCodes(cursor)
            if (result is ApiResult.Ok) {
                val known = rows.map { it.id }.toSet()
                rows = rows + result.value.codes.filter { it.id !in known }
                nextCursor = result.value.nextCursor
            }
        } finally {
            loadingMore = false
        }
    }
}

/**
 * Code detail (runtime.md, Navigation): the row by code id, then the title
 * and body from `GET /v1/records/{record_id}`. Contract 1 has no single-code read.
 */
class CodeDetailModel(val codeId: String, row: OwnedCode?) {
    sealed interface Phase {
        data object Loading : Phase

        data object Loaded : Phase

        data object NotFound : Phase

        data class Failure(@StringRes val key: Int) : Phase
    }

    sealed interface RevokePhase {
        data object Idle : RevokePhase

        data object Revoking : RevokePhase

        data class Failure(@StringRes val key: Int) : RevokePhase
    }

    var row by mutableStateOf(row)
        private set
    var record by mutableStateOf<OwnerRecord?>(null)
        private set
    var phase by mutableStateOf<Phase>(Phase.Loading)
        private set
    var revokePhase by mutableStateOf<RevokePhase>(RevokePhase.Idle)
        private set

    suspend fun load(app: AppModel) {
        if (!app.isOnline) {
            phase = Phase.Failure(R.string.error_no_connection)
            return
        }
        phase = Phase.Loading
        if (row == null || record != null) {
            when (val result = app.service.ownedCode(codeId)) {
                is ApiResult.Ok -> {
                    val found = result.value
                    if (found == null) {
                        phase = Phase.NotFound
                        return
                    }
                    row = found
                }
                is ApiResult.Err -> {
                    phase = Phase.Failure(ownerMessage(result.failure, app))
                    return
                }
            }
        }
        val current = row ?: return
        when (val result = app.service.record(current.recordId)) {
            is ApiResult.Ok -> {
                record = result.value
                phase = Phase.Loaded
            }
            is ApiResult.Err -> phase = if (result.failure == ApiFailure.NotFound) Phase.NotFound else Phase.Failure(ownerMessage(result.failure, app))
        }
    }

    /** Revoke posts the code id. Refused offline; a failure keeps the code active and says `error.nothing_saved`. */
    suspend fun revoke(app: AppModel) {
        val current = row ?: return
        if (!app.isOnline) {
            revokePhase = RevokePhase.Failure(R.string.error_no_connection)
            return
        }
        revokePhase = RevokePhase.Revoking
        when (val result = app.service.revoke(current.id)) {
            is ApiResult.Ok -> {
                app.ownerChanged(current.canonical)
                revokePhase = RevokePhase.Idle
                load(app)
            }
            is ApiResult.Err -> revokePhase = when (val failure = result.failure) {
                ApiFailure.NoConnection -> RevokePhase.Failure(R.string.error_no_connection)
                ApiFailure.Unauthorized -> {
                    app.needsSignIn()
                    RevokePhase.Failure(R.string.common_sign_in_needed)
                }
                else -> RevokePhase.Failure(failure.message(CallContext.REVOKE).res)
            }
        }
    }
}

/** Edit record (design/UX.md): the current title and body, then one Save that posts a version to the row's `record_id`. */
class EditRecordModel(val codeId: String, row: OwnedCode?) {
    sealed interface Phase {
        data object Loading : Phase

        /** The record could not be read, so there are no fields to edit. */
        data class LoadFailure(@StringRes val key: Int) : Phase

        data object Editing : Phase

        data object Saving : Phase

        data object NotFound : Phase

        /** A save failed; the fields stay as typed. */
        data class Failure(@StringRes val key: Int) : Phase
    }

    var row by mutableStateOf(row)
        private set
    var title by mutableStateOf("")
    var body by mutableStateOf("")
    var phase by mutableStateOf<Phase>(Phase.Loading)
        private set
    private var loaded = false

    suspend fun loadOnce(app: AppModel) {
        if (loaded) return
        loaded = true
        if (!app.isOnline) {
            phase = Phase.LoadFailure(R.string.error_no_connection)
            return
        }
        if (row == null) {
            when (val result = app.service.ownedCode(codeId)) {
                is ApiResult.Ok -> {
                    val found = result.value
                    if (found == null) {
                        phase = Phase.NotFound
                        return
                    }
                    row = found
                }
                is ApiResult.Err -> {
                    phase = Phase.LoadFailure(ownerMessage(result.failure, app))
                    return
                }
            }
        }
        val current = row ?: return
        when (val result = app.service.record(current.recordId)) {
            is ApiResult.Ok -> {
                title = result.value.title
                body = result.value.body
                phase = Phase.Editing
            }
            is ApiResult.Err -> phase = if (result.failure == ApiFailure.NotFound) {
                Phase.NotFound
            } else {
                Phase.LoadFailure(ownerMessage(result.failure, app))
            }
        }
    }

    /** `POST /v1/records/{record_id}/versions`. The fields stay on failure. */
    suspend fun save(app: AppModel): Boolean {
        val current = row ?: return false
        if (FieldLimits.isBlank(title)) {
            phase = Phase.Failure(R.string.create_title_required)
            return false
        }
        if (!app.isOnline) {
            phase = Phase.Failure(R.string.error_no_connection)
            return false
        }
        phase = Phase.Saving
        when (val result = app.service.addVersion(current.recordId, title.trim(), body.trim())) {
            is ApiResult.Ok -> {
                app.ownerChanged(current.canonical)
                phase = Phase.Editing
                return true
            }
            is ApiResult.Err -> phase = when (val failure = result.failure) {
                ApiFailure.NoConnection -> Phase.Failure(R.string.error_no_connection)
                ApiFailure.Unauthorized -> {
                    app.needsSignIn()
                    Phase.Failure(R.string.common_sign_in_needed)
                }
                else -> Phase.Failure(failure.message(CallContext.RECORD_VERSION).res)
            }
        }
        return false
    }
}
