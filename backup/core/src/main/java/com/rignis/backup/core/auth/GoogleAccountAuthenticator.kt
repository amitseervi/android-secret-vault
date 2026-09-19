package com.rignis.backup.core.auth

import android.content.Context
import com.rignis.backup.api.BackupAuthHost

// Identity (who's signed in) and Drive authorization (permission to touch
// drive.appdata) are deliberately two separate steps - Credential Manager
// only ever proves identity, it never grants API scopes.
data class GoogleIdentity(val accountEmail: String, val displayName: String?)

sealed class GoogleAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
class SignInCancelledException : GoogleAuthException("Sign-in was cancelled or no Google account is available")
class SignInFailedException(cause: Throwable) : GoogleAuthException("Google sign-in failed", cause)
class DriveAuthorizationDeniedException : GoogleAuthException("Google Drive access was denied")
class DriveAuthorizationFailedException(cause: Throwable) : GoogleAuthException("Google Drive authorization failed", cause)

interface GoogleAccountAuthenticator {
    // Identity only - no Drive access yet.
    suspend fun signIn(context: Context): Result<GoogleIdentity>

    // Requests (or reuses a previous grant of) the drive.appdata scope and
    // returns a bearer access token usable against the Drive REST API.
    // May launch a system consent screen via host.launchResolution.
    suspend fun authorizeDriveAppData(host: BackupAuthHost): Result<String>

    suspend fun signOut(context: Context)
}
