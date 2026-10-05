package com.zer0state.zzthat.api

import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ProtocolException
import javax.net.ssl.SSLException

/** What went wrong with one call, before it becomes one sentence. */
sealed interface ApiFailure {
    /** A documented error response: its status, its `error` code, and its `reason`. */
    data class Response(val status: Int, val error: String, val reason: String?) : ApiFailure

    /** The one not-found body (FR-010). */
    data object NotFound : ApiFailure

    /** 429 with `Retry-After` in whole seconds (FR-033). */
    data class RateLimited(val retryAfter: Int) : ApiFailure

    /** 401 that the refresh could not fix (FR-015). */
    data object Unauthorized : ApiFailure

    /** 400 `contract-version`, the only result that shows `error.update_app` (T013). */
    data object ContractVersion : ApiFailure

    /** No connection (US6). */
    data object NoConnection : ApiFailure

    /**
     * Any other response without a usable `X-ZZ-Contract`, or a body that does
     * not match the contract (runtime.md, API client step 10).
     */
    data object Unavailable : ApiFailure

    companion object {
        /** Maps an exception from a call. A transport failure that means no connection follows the offline rules (US6). */
        fun from(error: Throwable): ApiFailure = when (error) {
            is OriginNotConfigured -> Unavailable
            is SSLException, is ProtocolException -> Unavailable
            is IOException -> NoConnection
            is SerializationException, is IllegalArgumentException -> Unavailable
            else -> Unavailable
        }
    }
}

/** The call a failure came from, which decides its sentence (spec.md, Errors). */
enum class CallContext(val isWrite: Boolean) {
    RESOLVE(false),
    MINT(true),
    REROLL(true),
    REVOKE(true),
    RECORD_VERSION(true),
    REPORT(true),
    OWNER_READ(false),
    SIGN_IN(false),
    SIGN_OUT(false),
    DELETE_ACCOUNT(true),
    DISCOVERY(false),
}

/**
 * Copy keys from zzThis design/copy.json. These are keys, not sentences: the
 * app looks each one up in its generated catalog (strings.xml, where dots are
 * underscores).
 */
enum class MessageKey(val key: String) {
    TYPE_MALFORMED("type.malformed"),
    RESOLVE_NOT_FOUND("resolve.not_found"),
    RESOLVE_RATE_LIMITED("resolve.rate_limited"),
    SCAN_CHECK_MISMATCH("scan.check_mismatch"),
    SCAN_WRONG_LENGTH("scan.wrong_length"),
    SCAN_BARE("scan.bare"),
    CREATE_UNAVAILABLE("create.unavailable"),
    CREATE_CONTENT_REFUSED("create.content_refused"),
    MINTED_REROLL_CAP("minted.reroll_cap"),
    SIGN_IN_NEEDED("common.sign_in_needed"),
    SIGN_IN_FAILED("signin.failed"),
    DELETE_FAILED("delete.failed"),
    FORBIDDEN("error.forbidden"),
    TAKEN("error.taken"),
    RESERVED_HANDLE("error.reserved_handle"),
    NOTHING_SAVED("error.nothing_saved"),
    UPDATE_APP("error.update_app"),
    NO_CONNECTION("error.no_connection"),
    UNAVAILABLE("error.unavailable"),
}

/** The one sentence for this failure (spec.md, Errors; design/UX.md, Errors). */
fun ApiFailure.message(context: CallContext): MessageKey = when (this) {
    ApiFailure.ContractVersion -> MessageKey.UPDATE_APP
    is ApiFailure.RateLimited -> MessageKey.RESOLVE_RATE_LIMITED
    ApiFailure.Unavailable -> MessageKey.UNAVAILABLE
    ApiFailure.NoConnection -> MessageKey.NO_CONNECTION
    ApiFailure.NotFound -> if (context == CallContext.DELETE_ACCOUNT) MessageKey.DELETE_FAILED else MessageKey.RESOLVE_NOT_FOUND
    ApiFailure.Unauthorized -> when (context) {
        CallContext.SIGN_IN -> MessageKey.SIGN_IN_FAILED
        CallContext.DELETE_ACCOUNT, CallContext.MINT, CallContext.REROLL, CallContext.REVOKE, CallContext.RECORD_VERSION,
        CallContext.REPORT, CallContext.OWNER_READ,
        -> MessageKey.SIGN_IN_NEEDED
        CallContext.RESOLVE, CallContext.SIGN_OUT, CallContext.DISCOVERY -> MessageKey.UNAVAILABLE
    }
    is ApiFailure.Response -> responseMessage(status, error, reason, context)
}

private fun responseMessage(status: Int, error: String, reason: String?, context: CallContext): MessageKey {
    if (context == CallContext.SIGN_IN) return MessageKey.SIGN_IN_FAILED
    if (context == CallContext.DELETE_ACCOUNT) return MessageKey.DELETE_FAILED
    return when {
        status == 400 && error == "contract-version" -> MessageKey.UPDATE_APP
        status == 400 && error == "malformed" && context == CallContext.RESOLVE -> when (reason) {
            "check-mismatch" -> MessageKey.SCAN_CHECK_MISMATCH
            "wrong-length" -> MessageKey.SCAN_WRONG_LENGTH
            else -> MessageKey.TYPE_MALFORMED
        }
        status == 400 -> if (context.isWrite) MessageKey.NOTHING_SAVED else MessageKey.UNAVAILABLE
        status == 422 && error == "unsupported" -> MessageKey.SCAN_BARE
        status == 422 && error == "reserved-handle" -> MessageKey.RESERVED_HANDLE
        status == 422 && error == "content-refused" -> MessageKey.CREATE_CONTENT_REFUSED
        status == 403 && error == "reroll-cap" -> MessageKey.MINTED_REROLL_CAP
        status == 403 && error == "scope-unavailable" -> MessageKey.CREATE_UNAVAILABLE
        status == 503 && error == "not-ready" && context == CallContext.MINT -> MessageKey.CREATE_UNAVAILABLE
        status == 403 -> MessageKey.FORBIDDEN
        status == 409 && error == "taken" -> MessageKey.TAKEN
        status == 413 -> MessageKey.NOTHING_SAVED
        status == 500 -> if (context.isWrite) MessageKey.NOTHING_SAVED else MessageKey.UNAVAILABLE
        else -> MessageKey.UNAVAILABLE
    }
}

/** One call's answer: the decoded body, or one failure. */
sealed interface ApiResult<out T> {
    data class Ok<out T>(val value: T) : ApiResult<T>

    data class Err(val failure: ApiFailure) : ApiResult<Nothing>

    val valueOrNull: T? get() = (this as? Ok)?.value
    val failureOrNull: ApiFailure? get() = (this as? Err)?.failure
}
