package com.rignis.backup.api

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent

// Activity/Compose-side bridge for resolving a Google Drive authorization
// PendingIntent, kept out of the singleton BackupManager implementation.
interface BackupAuthHost {
    val activity: Activity

    // Launches the given resolution PendingIntent and suspends until the
    // user responds. Returns the result Intent when granted, or null if
    // denied/cancelled.
    suspend fun launchResolution(pendingIntent: PendingIntent): Intent?
}
