package com.joetr.basil.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.joetr.basil.network.BasilFirebase
import com.joetr.basil.platform.AndroidContextHolder
import com.joetr.basil.platform.BasilConfig

public actual suspend fun googleSignIn(firebase: BasilFirebase): GoogleSignInResult {
    val serverClientId = BasilConfig.GOOGLE_WEB_CLIENT_ID
    require(serverClientId.isNotBlank() && !serverClientId.startsWith("YOUR_")) {
        "Google sign-in is not configured. Set basil.google.webClientId in local.properties " +
            "(Google Cloud OAuth Web client ID), then rebuild."
    }
    val context: Context = AndroidContextHolder.activity
        ?: throw authException("Google sign-in needs an Activity context")

    // Do not set a nonce here — Firebase REST signInWithIdp doesn't participate in our
    // nonce challenge, and a mismatched/hashed nonce has caused silent failures in the field.
    val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()
    val credentialManager = CredentialManager.create(context)
    val response = try {
        credentialManager.getCredential(request = request, context = context)
    } catch (e: NoCredentialException) {
        throw authException(
            "No Google account available. Add a Google account on this device, and make sure " +
                "the app's debug SHA-1 is added in Firebase Project Settings → Your apps.",
        )
    } catch (e: GetCredentialCancellationException) {
        val detail = e.message.orEmpty()
        if (detail.contains("reauth", ignoreCase = true) || detail.contains("[16]")) {
            throw authException(
                "Google sign-in failed (account reauth). Add this debug SHA-1 in Firebase " +
                    "Project Settings for com.joetr.basil, then re-download google-services.json:\n" +
                    "6C:1A:C2:BE:F4:C8:58:B6:0C:B6:19:93:BD:99:1B:21:36:8F:40:35",
            )
        }
        throw authException("Google sign-in was cancelled")
    } catch (e: GetCredentialException) {
        val detail = e.message.orEmpty()
        if (detail.contains("reauth", ignoreCase = true) || detail.contains("[16]")) {
            throw authException(
                "Google sign-in failed (account reauth). Add this debug SHA-1 in Firebase " +
                    "Project Settings for com.joetr.basil, then re-download google-services.json:\n" +
                    "6C:1A:C2:BE:F4:C8:58:B6:0C:B6:19:93:BD:99:1B:21:36:8F:40:35",
            )
        }
        throw authException(detail.ifBlank { "Google sign-in failed" })
    }
    val credential = response.credential
    val idToken = if (
        credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        GoogleIdTokenCredential.createFrom(credential.data).idToken
    } else {
        throw authException("Unexpected credential type returned from Credential Manager")
    }
    return signInToFirebaseWithGoogleIdToken(firebase, idToken)
}
