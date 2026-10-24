package com.closetheloop.sync

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.closetheloop.mobile.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/** Google sign-in via Credential Manager, exchanged for a Firebase session. */
object GoogleAccount {
    sealed interface Result {
        data object Success : Result
        data object Cancelled : Result
        data class Failed(val message: String) : Result
    }

    /** [activityContext] must be an Activity so the account picker can show. Also hands the sign-in to the watch. */
    suspend fun signIn(activityContext: Context): Result = try {
        val idToken = requestIdToken(activityContext, GetSignInWithGoogleOption.Builder(webClientId(activityContext)).build())
        if (idToken == null) {
            Result.Failed("Unexpected credential type")
        } else {
            FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            runCatching { WatchBridge.sendSignIn(activityContext, idToken) }
            Result.Success
        }
    } catch (e: GetCredentialCancellationException) {
        Result.Cancelled
    } catch (e: Exception) {
        Result.Failed(e.message ?: "Sign-in failed")
    }

    /** Re-sends a fresh token for the already-signed-in account to the watch (auto-selects, no picker). */
    suspend fun connectWatch(activityContext: Context): Result = try {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId(activityContext))
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()
        val idToken = requestIdToken(activityContext, option)
        if (idToken == null) {
            Result.Failed("Unexpected credential type")
        } else {
            WatchBridge.sendSignIn(activityContext, idToken)
            Result.Success
        }
    } catch (e: GetCredentialCancellationException) {
        Result.Cancelled
    } catch (e: Exception) {
        Result.Failed(e.message ?: "Couldn't reach the watch")
    }

    private fun webClientId(context: Context) = context.getString(R.string.default_web_client_id)

    private suspend fun requestIdToken(activityContext: Context, option: androidx.credentials.CredentialOption): String? {
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        return if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            null
        }
    }

    suspend fun signOut(context: Context) {
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        runCatching { WatchBridge.sendSignOut(context) }
    }
}
