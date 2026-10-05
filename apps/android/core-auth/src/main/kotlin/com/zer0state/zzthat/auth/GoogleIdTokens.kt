package com.zer0state.zzthat.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Sign in with Google through Credential Manager (runtime.md, Auth step 4):
 * `GetSignInWithGoogleOption` with the web client id as the server client id
 * and the server nonce. Only the ID token is kept. It runs only when the
 * person taps the Google button.
 */
object GoogleIdTokens {
    /** A web client id that is still the build's placeholder hides the Google button. */
    fun isConfigured(webClientId: String): Boolean = webClientId.isNotBlank() && webClientId != PLACEHOLDER

    const val PLACEHOLDER = "unset"

    suspend fun request(activityContext: Context, webClientId: String, nonce: String): ProviderToken {
        val option = GetSignInWithGoogleOption.Builder(webClientId).setNonce(nonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                ProviderToken.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                ProviderToken.Failed
            }
        } catch (cancelled: GetCredentialCancellationException) {
            ProviderToken.Cancelled
        } catch (error: GetCredentialException) {
            ProviderToken.Failed
        } catch (error: GoogleIdTokenParsingException) {
            ProviderToken.Failed
        }
    }
}
