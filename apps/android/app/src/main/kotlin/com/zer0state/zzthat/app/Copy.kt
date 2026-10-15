package com.zer0state.zzthat.app

import androidx.annotation.StringRes
import com.zer0state.zzthat.R
import com.zer0state.zzthat.api.MessageKey
import com.zer0state.zzthat.api.generated.models.OwnedCode
import com.zer0state.zzthat.api.generated.models.ReportRequest

// Every sentence on screen is a catalog lookup (FR-042): the generated
// strings.xml, whose names are the copy.json keys with dots as underscores.

/** The catalog string for an error-table sentence. */
@get:StringRes
val MessageKey.res: Int
    get() = when (this) {
        MessageKey.TYPE_MALFORMED -> R.string.type_malformed
        MessageKey.RESOLVE_NOT_FOUND -> R.string.resolve_not_found
        MessageKey.RESOLVE_RATE_LIMITED -> R.string.resolve_rate_limited
        MessageKey.SCAN_CHECK_MISMATCH -> R.string.scan_check_mismatch
        MessageKey.SCAN_WRONG_LENGTH -> R.string.scan_wrong_length
        MessageKey.SCAN_BARE -> R.string.scan_bare
        MessageKey.CREATE_UNAVAILABLE -> R.string.create_unavailable
        MessageKey.CREATE_CONTENT_REFUSED -> R.string.create_content_refused
        MessageKey.MINTED_REROLL_CAP -> R.string.minted_reroll_cap
        MessageKey.SIGN_IN_NEEDED -> R.string.common_sign_in_needed
        MessageKey.SIGN_IN_FAILED -> R.string.signin_failed
        MessageKey.DELETE_FAILED -> R.string.delete_failed
        MessageKey.FORBIDDEN -> R.string.error_forbidden
        MessageKey.TAKEN -> R.string.error_taken
        MessageKey.RESERVED_HANDLE -> R.string.error_reserved_handle
        MessageKey.NOTHING_SAVED -> R.string.error_nothing_saved
        MessageKey.UPDATE_APP -> R.string.error_update_app
        MessageKey.NO_CONNECTION -> R.string.error_no_connection
        MessageKey.UNAVAILABLE -> R.string.error_unavailable
    }

/** `provider.apple`, `provider.google`, or `provider.dev` (runtime.md, Localization). */
@StringRes
fun providerRes(provider: String): Int? = when (provider) {
    "apple" -> R.string.provider_apple
    "google" -> R.string.provider_google
    "dev" -> R.string.provider_dev
    else -> null
}

@get:StringRes
val OwnedCode.Status.res: Int
    get() = when (this) {
        OwnedCode.Status.active -> R.string.codes_status_active
        OwnedCode.Status.used -> R.string.codes_status_used
        OwnedCode.Status.expired -> R.string.codes_status_expired
        OwnedCode.Status.revoked -> R.string.codes_status_revoked
        // A status the contract does not list yet (T106). It is not active, so
        // the screens offer no Revoke. There is no copy for it, and copy comes
        // only from zzThis, so it shows as expired, the nearest not-active
        // state. iOS decodes the same value to expired, so both apps agree.
        OwnedCode.Status.unknown_default_open_api -> R.string.codes_status_expired
    }

@get:StringRes
val ReportRequest.Reason.res: Int
    get() = when (this) {
        ReportRequest.Reason.spam -> R.string.report_reason_spam
        ReportRequest.Reason.harassment -> R.string.report_reason_harassment
        ReportRequest.Reason.personalMinusData -> R.string.report_reason_personal_data
        ReportRequest.Reason.other -> R.string.report_reason_other
        // A request enum: the app picks the reason from REASONS and never
        // sends this generated case (T106). Labelled as other if it ever did.
        ReportRequest.Reason.unknown_default_open_api -> R.string.report_reason_other
    }
