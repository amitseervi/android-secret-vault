package com.rignis.core.ui.routes.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.rignis.auth.domain.CipherManager
import com.rignis.backup.api.BackupAuthHost
import com.rignis.backup.api.SyncTrigger
import com.rignis.core.ui.viewmodels.backup.BackupPageAction
import com.rignis.core.ui.viewmodels.backup.BackupViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// User-triggered + on-app-open only, never silent background sync (no
// WorkManager) - this fires syncNow on the same ON_RESUME hook HomeRoute
// already uses for its own biometric-capability check. Debounced so
// rapid resume/pause cycles (e.g. a quick app-switcher glance) don't spam
// Drive with redundant sync attempts.
@Composable
fun AutoSyncOnOpenEffect(
    viewModel: BackupViewModel, cipherManager: CipherManager, host: BackupAuthHost, minIntervalMillis: Long = 30_000L
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lastAttempt = remember { longArrayOf(0L) }

    DisposableEffect(lifecycleOwner, host) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = System.currentTimeMillis()
                if (now - lastAttempt[0] >= minIntervalMillis) {
                    lastAttempt[0] = now
                    viewModel.onAction(BackupPageAction.SyncNow(host, cipherManager, SyncTrigger.APP_OPEN))
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
