package com.zer0state.zzthat.api

import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Invocation
import java.io.IOException

/** The header every `/v1` call carries (FR-032, ZQ19). */
const val CONTRACT_HEADER = "X-ZZ-Contract"
const val CONTRACT_VERSION = "1"

/** The operation of a Retrofit request: the generated method name, which is the contract operationId. */
internal val Request.operation: String? get() = tag(Invocation::class.java)?.method()?.name

/**
 * Every `/v1` call carries exactly one `X-ZZ-Contract: 1` (FR-032). The
 * generated Retrofit enum for the header serializes as `_1`, so the value is
 * set here by replacing, never by adding (runtime.md, API client step 2).
 */
class ContractInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header(CONTRACT_HEADER, CONTRACT_VERSION).build())
}

/**
 * The bearer rule (runtime.md, API client step 3, FR-016). Resolve, nonce,
 * token, refresh, and revoke never carry a bearer. A report carries one only
 * when the person is signed in. `Authorization` is set by replacing, never
 * by adding.
 */
class AuthInterceptor(private val tokens: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder().removeHeader(AUTHORIZATION)
        val token = when (request.operation) {
            in OPTIONAL_BEARER -> tokens.currentAccessToken
            in BEARER -> guarded { tokens.tokenForRequest() }
            else -> null
        }
        if (token != null) builder.header(AUTHORIZATION, "Bearer $token")
        return chain.proceed(builder.build())
    }

    companion object {
        const val AUTHORIZATION = "Authorization"

        /** Operations whose contract security is the bearer token. */
        val BEARER = setOf(
            "getMe", "deleteMe", "mintCode", "rerollCode", "listMyCodes", "revokeCode", "getRecord", "addRecordVersion",
            "listAudit",
        )

        /** Operations where the bearer is optional, except resolve, which never sends one so it stays cacheable (FR-016). */
        val OPTIONAL_BEARER = setOf("createReport", "submitRead")
    }
}

/**
 * The 401 retry (runtime.md, API client step 8): a bearer call that gets a
 * 401 waits for or starts the one refresh, then retries once with the new
 * token. The person is signed out only when the refresh itself returns 401.
 */
class RefreshAuthenticator(private val tokens: TokenStore) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (request.operation !in AuthInterceptor.BEARER) return null
        // The retry happens once.
        if (response.priorResponse != null) return null
        val failed = request.header(AuthInterceptor.AUTHORIZATION)?.removePrefix("Bearer ") ?: return null
        val renewed = guarded { tokens.refreshedAfter(failed) } ?: return null
        return request.newBuilder().header(AuthInterceptor.AUTHORIZATION, "Bearer $renewed").build()
    }
}

/**
 * OkHttp accepts only an IOException from an interceptor or an authenticator;
 * anything else would end the process. Every other failure in the token
 * logic travels as [ApiFailureException].
 */
internal inline fun <T> guarded(block: () -> T): T = try {
    block()
} catch (error: IOException) {
    throw error
} catch (error: Exception) {
    throw ApiFailureException(ApiFailure.from(error))
}

/** A build with no API origin: every call fails with `unavailable`, and the app never invents data. */
class OriginNotConfigured : IOException("no API origin is configured for this build")

internal class UnconfiguredInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = throw OriginNotConfigured()
}
