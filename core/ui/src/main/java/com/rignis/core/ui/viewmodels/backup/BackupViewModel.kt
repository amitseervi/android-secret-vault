package com.rignis.core.ui.viewmodels.backup

import androidx.lifecycle.viewModelScope
import com.rignis.auth.domain.CipherManager
import com.rignis.backup.api.BackupAuthHost
import com.rignis.backup.api.BackupManager
import com.rignis.backup.api.BackupState
import com.rignis.backup.api.ConflictResolution
import com.rignis.backup.api.SyncIssue
import com.rignis.backup.api.SyncStatus
import com.rignis.backup.api.SyncTrigger
import com.rignis.core.base.BaseViewModel
import com.rignis.mysecret.analytics.api.Analytics
import com.rignis.mysecret.analytics.api.AnalyticsEvent
import com.rignis.mysecret.analytics.api.AnalyticsParam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupPageState(
    val backupState: BackupState = BackupState.NotConfigured,
    val syncStatus: SyncStatus = SyncStatus.Disabled,
    val notBackedUpCount: Int = 0,
    val issues: List<SyncIssue> = emptyList(),
    val isBusy: Boolean = false,
    val enteredCode: String = "",
    val pendingGeneratedCode: String? = null,
    val revealedCode: String? = null,
    val lastActionFailed: Boolean = false
)

sealed interface BackupPageAction {
    data class LinkAccount(val host: BackupAuthHost) : BackupPageAction
    data object GenerateNewCode : BackupPageAction
    data object DismissGeneratedCode : BackupPageAction
    data class UpdateEnteredCode(val value: String) : BackupPageAction

    // Works whether this account has no vault yet (mints one) or already
    // has one (validates against it) - BackupManager figures out which.
    data class SubmitCode(
        val host: BackupAuthHost, val cipherManager: CipherManager, val code: String
    ) : BackupPageAction

    data class RevealStoredCode(val cipherManager: CipherManager) : BackupPageAction
    data object DismissRevealedCode : BackupPageAction
    data class SyncNow(
        val host: BackupAuthHost, val cipherManager: CipherManager, val trigger: SyncTrigger
    ) : BackupPageAction

    data object LockKey : BackupPageAction
    data class DisableBackup(val deleteCloudCopy: Boolean) : BackupPageAction
    data class ResolveIssue(
        val secretId: String, val resolution: ConflictResolution, val host: BackupAuthHost, val cipherManager: CipherManager
    ) : BackupPageAction

    data class RetryAllFailed(val host: BackupAuthHost, val cipherManager: CipherManager) : BackupPageAction
    data object DismissError : BackupPageAction
}

class BackupViewModel(
    private val backupManager: BackupManager, private val analytics: Analytics
) : BaseViewModel<BackupPageState, BackupPageAction>() {

    private val _local = MutableStateFlow(BackupPageState())

    override val state: StateFlow<BackupPageState> = combine(
        backupManager.backupState, backupManager.syncStatus, backupManager.notBackedUpCount,
        backupManager.issues, _local
    ) { backupState, syncStatus, notBackedUpCount, issues, local ->
        local.copy(
            backupState = backupState,
            syncStatus = syncStatus,
            notBackedUpCount = notBackedUpCount,
            issues = issues,
            isBusy = local.isBusy || syncStatus == SyncStatus.InProgress
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BackupPageState())

    init {
        viewModelScope.launch { backupManager.refreshState() }
    }

    override fun onAction(e: BackupPageAction) {
        when (e) {
            is BackupPageAction.LinkAccount -> linkAccount(e.host)
            BackupPageAction.GenerateNewCode -> _local.update {
                it.copy(pendingGeneratedCode = backupManager.generateBackupCode())
            }

            BackupPageAction.DismissGeneratedCode -> _local.update { it.copy(pendingGeneratedCode = null) }
            is BackupPageAction.UpdateEnteredCode -> _local.update { it.copy(enteredCode = e.value) }
            is BackupPageAction.SubmitCode -> submitCode(e.host, e.cipherManager, e.code)
            is BackupPageAction.RevealStoredCode -> revealStoredCode(e.cipherManager)
            BackupPageAction.DismissRevealedCode -> _local.update { it.copy(revealedCode = null) }
            is BackupPageAction.SyncNow -> syncNow(e.host, e.cipherManager, e.trigger)
            BackupPageAction.LockKey -> backupManager.lockBackupKey()
            is BackupPageAction.DisableBackup -> disableBackup(e.deleteCloudCopy)
            is BackupPageAction.ResolveIssue -> resolveIssue(e.secretId, e.resolution, e.host, e.cipherManager)
            is BackupPageAction.RetryAllFailed -> retryAllFailed(e.host, e.cipherManager)
            BackupPageAction.DismissError -> _local.update { it.copy(lastActionFailed = false) }
        }
    }

    private inline fun withBusy(crossinline block: suspend () -> Boolean) {
        viewModelScope.launch {
            _local.update { it.copy(isBusy = true, lastActionFailed = false) }
            val failed = !block()
            _local.update { it.copy(isBusy = false, lastActionFailed = failed) }
        }
    }

    private fun linkAccount(host: BackupAuthHost) = withBusy {
        backupManager.linkAccount(host).onSuccess {
            analytics.logEvent(AnalyticsEvent.BackupAccountLinked) {}
        }.isSuccess
    }

    private fun submitCode(host: BackupAuthHost, cipherManager: CipherManager, code: String) = withBusy {
        val normalized = code.trim()
        val result = backupManager.enableBackup(host, cipherManager, normalized.toCharArray())
        if (result.isSuccess) {
            analytics.logEvent(AnalyticsEvent.BackupEnabled) {}
            _local.update { it.copy(enteredCode = "", pendingGeneratedCode = null) }
        }
        result.isSuccess
    }

    private fun revealStoredCode(cipherManager: CipherManager) = withBusy {
        val result = backupManager.revealStoredCode(cipherManager)
        result.onSuccess { code -> _local.update { it.copy(revealedCode = String(code)) } }
        result.isSuccess
    }

    private fun syncNow(host: BackupAuthHost, cipherManager: CipherManager, trigger: SyncTrigger) = withBusy {
        val report = backupManager.syncNow(host, cipherManager, trigger)
        val outcome = when {
            report.failed > 0 || report.skippedNotStaged > 0 || report.conflicts > 0 -> "partial"
            else -> "success"
        }
        analytics.logEvent(AnalyticsEvent.BackupSyncCompleted) {
            param(AnalyticsParam.SyncTriggerValue, trigger.name)
            param(AnalyticsParam.SyncResultValue, outcome)
        }
        true
    }

    private fun disableBackup(deleteCloudCopy: Boolean) = withBusy {
        backupManager.disableBackup(deleteCloudCopy).onSuccess {
            analytics.logEvent(AnalyticsEvent.BackupDisabled) {}
        }.isSuccess
    }

    private fun resolveIssue(
        secretId: String, resolution: ConflictResolution, host: BackupAuthHost, cipherManager: CipherManager
    ) = withBusy {
        val result = backupManager.resolveIssue(secretId, resolution, host, cipherManager)
        if (result.isSuccess) {
            analytics.logEvent(AnalyticsEvent.BackupConflictResolved) {
                param(AnalyticsParam.ConflictResolutionValue, resolution.name)
            }
        }
        result.isSuccess
    }

    private fun retryAllFailed(host: BackupAuthHost, cipherManager: CipherManager) = withBusy {
        val report = backupManager.retryAllFailed(host, cipherManager)
        report.failed == 0
    }
}
