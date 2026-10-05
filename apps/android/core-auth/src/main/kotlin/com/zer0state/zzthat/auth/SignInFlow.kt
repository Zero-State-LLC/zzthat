package com.zer0state.zzthat.auth

import com.zer0state.zzthat.api.ApiFailure
import com.zer0state.zzthat.api.ApiResult
import com.zer0state.zzthat.api.MessageKey
import com.zer0state.zzthat.api.ZZService
import com.zer0state.zzthat.api.generated.models.TokenRequest

/** What the provider's own sign-in answered (runtime.md, Auth steps 2 to 4). */
sealed interface ProviderToken {
    data class Token(val idToken: String) : ProviderToken

    /** The person closed the provider sheet. Nothing is shown. */
    data object Cancelled : ProviderToken

    data object Failed : ProviderToken
}

/**
 * Sign-in (runtime.md, Auth; zzThis spec 005 FR-020): a server nonce from
 * `POST /v1/auth/nonce`, the platform's own provider sign-in with that nonce,
 * then the provider ID token to `POST /v1/auth/token`. A failure leaves no
 * half session: tokens are kept only after the exchange succeeds.
 */
class SignInFlow(private val service: ZZService) {
    sealed interface Outcome {
        data object SignedIn : Outcome

        data object Cancelled : Outcome

        /** `signin.failed`, or `error.no_connection` offline. */
        data class Failed(val message: MessageKey) : Outcome
    }

    /**
     * @param demo Demo buttons do not call a provider; the demo API keeps the session in memory.
     * @param idToken the provider sign-in for the server nonce.
     */
    suspend fun run(
        provider: TokenRequest.Provider,
        online: Boolean,
        demo: Boolean,
        idToken: suspend (nonce: String) -> ProviderToken,
    ): Outcome {
        if (demo) return exchange(provider, "demo", "demo-nonce")
        if (!online) return Outcome.Failed(MessageKey.NO_CONNECTION)
        val nonce = when (val result = service.nonce()) {
            is ApiResult.Ok -> result.value
            is ApiResult.Err -> return failed(result.failure)
        }
        return when (val token = idToken(nonce)) {
            is ProviderToken.Token -> exchange(provider, token.idToken, nonce)
            ProviderToken.Cancelled -> Outcome.Cancelled
            ProviderToken.Failed -> Outcome.Failed(MessageKey.SIGN_IN_FAILED)
        }
    }

    private suspend fun exchange(provider: TokenRequest.Provider, idToken: String, nonce: String): Outcome =
        when (val result = service.exchange(provider, idToken, nonce)) {
            is ApiResult.Ok -> Outcome.SignedIn
            is ApiResult.Err -> failed(result.failure)
        }

    private fun failed(failure: ApiFailure): Outcome = Outcome.Failed(
        if (failure == ApiFailure.NoConnection) MessageKey.NO_CONNECTION else MessageKey.SIGN_IN_FAILED,
    )
}
