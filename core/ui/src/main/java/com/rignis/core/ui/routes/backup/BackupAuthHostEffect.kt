package com.rignis.core.ui.routes.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.rignis.backup.api.BackupAuthHost
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Bridges BackupAuthHost.launchResolution's PendingIntent through Compose's
// activity-result API. Only one resolution is ever in flight at a time,
// matching how the auth flow actually calls it (one Drive-authorization
// prompt at a time, never overlapping).
@Composable
fun rememberBackupAuthHost(): BackupAuthHost {
    val activity = LocalActivity.current as Activity
    val pending = remember { mutableStateOf<CancellableContinuation<Intent?>?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val continuation = pending.value
        pending.value = null
        if (result.resultCode == Activity.RESULT_OK) {
            continuation?.resume(result.data)
        } else {
            continuation?.resume(null)
        }
    }

    return remember(activity, launcher) {
        object : BackupAuthHost {
            override val activity: Activity = activity

            override suspend fun launchResolution(pendingIntent: PendingIntent): Intent? =
                suspendCancellableCoroutine { continuation ->
                    pending.value = continuation
                    launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                }
        }
    }
}
