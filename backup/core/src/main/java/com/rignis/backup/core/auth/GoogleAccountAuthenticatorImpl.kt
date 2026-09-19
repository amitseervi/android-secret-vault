package com.rignis.backup.core.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.rignis.backup.api.BackupAuthHost
import com.rignis.backup.core.BuildConfig

class GoogleAccountAuthenticatorImpl(
    private val serverClientId: String = BuildConfig.GOOGLE_SERVER_CLIENT_ID
) : GoogleAccountAuthenticator {

    override suspend fun signIn(context: Context): Result<GoogleIdentity> = runCatching {
        check(serverClientId.isNotBlank()) {
            "GOOGLE_SERVER_CLIENT_ID is not configured - add it to local.properties"
        }

        val option = GetGoogleIdOption.Builder().setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val response = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (e: GetCredentialException) {
            throw SignInFailedException(e)
        }

        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw SignInFailedException(IllegalStateException("Unexpected credential type"))
        }

        val tokenCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: GoogleIdTokenParsingException) {
            throw SignInFailedException(e)
        }

        GoogleIdentity(accountEmail = tokenCredential.id, displayName = tokenCredential.displayName)
    }

    override suspend fun authorizeDriveAppData(host: BackupAuthHost): Result<String> = runCatching {
        val client = Identity.getAuthorizationClient(host.activity)
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE))).build()

        val initial = try {
            client.authorize(request).await()
        } catch (e: Exception) {
            throw DriveAuthorizationFailedException(e)
        }

        val resolved = if (initial.hasResolution()) {
            val pendingIntent = initial.pendingIntent
                ?: throw DriveAuthorizationFailedException(IllegalStateException("Missing resolution intent"))
            val resultIntent = host.launchResolution(pendingIntent)
                ?: throw DriveAuthorizationDeniedException()
            try {
                client.getAuthorizationResultFromIntent(resultIntent)
            } catch (e: ApiException) {
                throw DriveAuthorizationFailedException(e)
            }
        } else {
            initial
        }

        resolved.accessToken ?: throw DriveAuthorizationFailedException(
            IllegalStateException("Authorization granted but no access token was returned")
        )
    }

    override suspend fun signOut(context: Context) {
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
