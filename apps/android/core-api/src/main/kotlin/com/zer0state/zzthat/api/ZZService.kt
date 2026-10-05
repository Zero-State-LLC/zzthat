package com.zer0state.zzthat.api

import com.zer0state.zzthat.api.generated.apis.AuthApi
import com.zer0state.zzthat.api.generated.apis.CodesApi
import com.zer0state.zzthat.api.generated.apis.MetaApi
import com.zer0state.zzthat.api.generated.apis.RecordsApi
import com.zer0state.zzthat.api.generated.apis.ReportsApi
import com.zer0state.zzthat.api.generated.models.Account
import com.zer0state.zzthat.api.generated.models.Code
import com.zer0state.zzthat.api.generated.models.CodePage
import com.zer0state.zzthat.api.generated.models.Discovery
import com.zer0state.zzthat.api.generated.models.Error
import com.zer0state.zzthat.api.generated.models.EverydayRecord
import com.zer0state.zzthat.api.generated.models.MintRequest
import com.zer0state.zzthat.api.generated.models.NotFoundError
import com.zer0state.zzthat.api.generated.models.OwnedCode
import com.zer0state.zzthat.api.generated.models.OwnerRecord
import com.zer0state.zzthat.api.generated.models.RateLimitedError
import com.zer0state.zzthat.api.generated.models.RefreshRequest
import com.zer0state.zzthat.api.generated.models.ReportRequest
import com.zer0state.zzthat.api.generated.models.Resolve
import com.zer0state.zzthat.api.generated.models.TokenRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException

/** The one `Cache-Control` value FR-041 may keep. */
const val PUBLIC_CACHE_CONTROL = "public, max-age=60, stale-while-revalidate=300"

/** A resolve and whether FR-041 may keep it: only when its `Cache-Control` is [PUBLIC_CACHE_CONTROL]. */
data class ResolveResponse(val record: Resolve, val cacheable: Boolean)

/** An [ApiFailure] carried through OkHttp, which accepts only an IOException from an interceptor or authenticator. */
class ApiFailureException(val failure: ApiFailure) : IOException(failure.toString())

/**
 * Every call the apps make (spec.md, API contract), over the generated
 * interfaces. Each call returns the decoded body or one [ApiFailure].
 */
class ZZService(val apis: ZZApis, val tokens: TokenStore) {
    // MARK: Discovery

    suspend fun discovery(): ApiResult<Discovery> =
        call(200, { apis.meta.getDiscovery(MetaApi.XZZContractGetDiscovery._1) }) { it.requireBody() }

    // MARK: Auth

    suspend fun nonce(): ApiResult<String> =
        call(200, { apis.auth.createNonce(AuthApi.XZZContractCreateNonce._1) }) { it.requireBody().nonce }

    /** `POST /v1/auth/token` (Auth step 5), then keep the pair. Tokens are kept only after the exchange succeeds. */
    suspend fun exchange(
        provider: TokenRequest.Provider,
        idToken: String,
        nonce: String,
        authorizationCode: String? = null,
    ): ApiResult<Unit> {
        val request = TokenRequest(provider, TokenRequest.Client.android, idToken, nonce, authorizationCode)
        return call(200, { apis.auth.exchangeToken(AuthApi.XZZContractExchangeToken._1, request) }) {
            val pair = it.requireBody()
            tokens.signIn(pair.accessToken, pair.refreshToken)
        }
    }

    /**
     * Sign-out: `POST /v1/auth/revoke`, then delete the tokens (Auth step 8).
     * The tokens are deleted even when the revoke fails, so sign-out always
     * ends the session on this device.
     */
    suspend fun signOut(): ApiResult<Unit> {
        val refresh = tokens.refreshToken
        val result = call(204, { apis.auth.revokeToken(AuthApi.XZZContractRevokeToken._1, RefreshRequest(refresh)) }) {}
        tokens.clear()
        return result
    }

    suspend fun me(): ApiResult<Account> = call(200, { apis.auth.getMe(AuthApi.XZZContractGetMe._1) }) { it.requireBody() }

    /** `DELETE /v1/me` (FR-034). On success the tokens are deleted here; the app deletes the draft and every cache. */
    suspend fun deleteAccount(): ApiResult<Unit> {
        val result = call(204, { apis.auth.deleteMe(AuthApi.XZZContractDeleteMe._1) }) {}
        if (result is ApiResult.Ok) tokens.clear()
        return result
    }

    // MARK: Codes

    /** `GET /v1/resolve/{code}` with the canonical code only: no photo and no `Authorization` (FR-016, T012). */
    suspend fun resolve(canonical: String): ApiResult<ResolveResponse> =
        call(200, { apis.codes.resolveCode(CodesApi.XZZContractResolveCode._1, canonical) }) {
            ResolveResponse(it.requireBody(), cacheable = it.headers()["Cache-Control"] == PUBLIC_CACHE_CONTROL)
        }

    /** `POST /v1/codes` with scope `free_public`, kind `plain`, and the title and body. No code string and no photo (FR-017, SC-2). */
    suspend fun mint(title: String, body: String): ApiResult<Code> {
        val request = MintRequest(
            scope = MintRequest.Scope.free_public,
            record = EverydayRecord(title, body),
            kind = MintRequest.Kind.plain,
            singleUse = null,
        )
        return call(201, { apis.codes.mintCode(CodesApi.XZZContractMintCode._1, request) }) { it.requireBody() }
    }

    suspend fun reroll(codeId: String): ApiResult<Code> =
        call(200, { apis.codes.rerollCode(CodesApi.XZZContractRerollCode._1, codeId) }) { it.requireBody() }

    /** One page of `GET /v1/me/codes`, 50 a page (FR-021). */
    suspend fun myCodes(cursor: String? = null, limit: Int = PAGE_SIZE): ApiResult<CodePage> =
        call(200, { apis.codes.listMyCodes(CodesApi.XZZContractListMyCodes._1, limit, cursor) }) { it.requireBody() }

    /**
     * Code detail and Edit record by code id (runtime.md, Navigation): page
     * through `GET /v1/me/codes` until a row's `id` matches. Null when no row
     * matches, which shows the one not-found state.
     */
    suspend fun ownedCode(id: String): ApiResult<OwnedCode?> {
        var cursor: String? = null
        do {
            when (val page = myCodes(cursor)) {
                is ApiResult.Err -> return page
                is ApiResult.Ok -> {
                    page.value.codes.firstOrNull { it.id == id }?.let { return ApiResult.Ok(it) }
                    cursor = page.value.nextCursor
                }
            }
        } while (cursor != null)
        return ApiResult.Ok(null)
    }

    suspend fun record(id: String): ApiResult<OwnerRecord> =
        call(200, { apis.records.getRecord(RecordsApi.XZZContractGetRecord._1, id) }) { it.requireBody() }

    suspend fun addVersion(recordId: String, title: String, body: String): ApiResult<Unit> =
        call(201, { apis.records.addRecordVersion(RecordsApi.XZZContractAddRecordVersion._1, recordId, EverydayRecord(title, body)) }) {}

    suspend fun revoke(codeId: String): ApiResult<Unit> =
        call(200, { apis.codes.revokeCode(CodesApi.XZZContractRevokeCode._1, codeId) }) {}

    /** `POST /v1/reports` (FR-035). The answer never says whether the code exists. */
    suspend fun report(canonical: String, reason: ReportRequest.Reason, note: String): ApiResult<Unit> {
        val request = ReportRequest(canonical, reason, note.ifEmpty { null })
        return call(202, { apis.reports.createReport(ReportsApi.XZZContractCreateReport._1, request) }) {}
    }

    companion object {
        const val PAGE_SIZE = 50

        /**
         * One blocking `POST /v1/auth/refresh` for the token store. A 401 is
         * [RefreshOutcome.Rejected]; any other failure throws, so the session stays.
         */
        fun refresh(auth: AuthApi, refreshToken: String): RefreshOutcome {
            // OkHttp accepts only an IOException from an interceptor or an
            // authenticator, so every other failure travels as ApiFailureException.
            val response = try {
                runBlocking { auth.refreshToken(AuthApi.XZZContractRefreshToken._1, RefreshRequest(refreshToken)) }
            } catch (error: IOException) {
                throw error
            } catch (error: Exception) {
                throw ApiFailureException(ApiFailure.from(error))
            }
            if (response.headers()[CONTRACT_HEADER] != CONTRACT_VERSION) throw ApiFailureException(ApiFailure.Unavailable)
            return when (response.code()) {
                200 -> {
                    val pair = response.body() ?: throw ApiFailureException(ApiFailure.Unavailable)
                    val rotated = pair.refreshToken ?: throw ApiFailureException(ApiFailure.Unavailable)
                    RefreshOutcome.Refreshed(pair.accessToken, rotated)
                }
                401 -> RefreshOutcome.Rejected
                else -> throw ApiFailureException(errorFailure(response))
            }
        }

        /**
         * Runs one call. A response without `X-ZZ-Contract: 1` is
         * `unavailable`, whatever its status (runtime.md, API client step 10).
         */
        internal suspend fun <T, R> call(
            success: Int,
            request: suspend () -> Response<T>,
            decode: (Response<T>) -> R,
        ): ApiResult<R> {
            val response = try {
                request()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                return ApiResult.Err(failureOf(error))
            }
            if (response.headers()[CONTRACT_HEADER] != CONTRACT_VERSION) return ApiResult.Err(ApiFailure.Unavailable)
            if (!response.isSuccessful) return ApiResult.Err(errorFailure(response))
            if (response.code() != success) return ApiResult.Err(ApiFailure.Unavailable)
            return try {
                ApiResult.Ok(decode(response))
            } catch (error: IllegalStateException) {
                ApiResult.Err(ApiFailure.Unavailable)
            }
        }

        private fun failureOf(error: Throwable): ApiFailure =
            if (error is ApiFailureException) error.failure else ApiFailure.from(error)

        /** An error response as the contract declares it. A body that does not match its schema is `unavailable`. */
        internal fun errorFailure(response: Response<*>): ApiFailure {
            val status = response.code()
            val text = try {
                response.errorBody()?.string().orEmpty()
            } catch (error: IOException) {
                return ApiFailure.from(error)
            }
            return try {
                when (status) {
                    404 -> {
                        ZZJson.strict.decodeFromString(NotFoundError.serializer(), text)
                        ApiFailure.NotFound
                    }
                    429 -> {
                        ZZJson.strict.decodeFromString(RateLimitedError.serializer(), text)
                        val retryAfter = response.headers()["Retry-After"]?.trim()?.toIntOrNull()?.takeIf { it >= 1 }
                        if (retryAfter == null) ApiFailure.Unavailable else ApiFailure.RateLimited(retryAfter)
                    }
                    else -> {
                        val error = ZZJson.strict.decodeFromString(Error.serializer(), text)
                        when {
                            status == 401 -> ApiFailure.Unauthorized
                            error.error == Error.Error.contractMinusVersion -> ApiFailure.ContractVersion
                            error.error == Error.Error.notMinusFound -> ApiFailure.NotFound
                            error.error == Error.Error.unauthorized -> ApiFailure.Unauthorized
                            else -> ApiFailure.Response(status, error.error.value, error.reason)
                        }
                    }
                }
            } catch (error: SerializationException) {
                ApiFailure.Unavailable
            } catch (error: IllegalArgumentException) {
                ApiFailure.Unavailable
            }
        }
    }
}

private fun <T> Response<T>.requireBody(): T = body() ?: throw IllegalStateException("no body")
