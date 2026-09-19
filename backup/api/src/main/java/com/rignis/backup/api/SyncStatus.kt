package com.rignis.backup.api

sealed interface SyncStatus {
    data object Disabled : SyncStatus

    data class Idle(val lastSyncTimeStamp: Long) : SyncStatus

    data object InProgress : SyncStatus

    // Carries "some of the secrets aren't synced" to the UI without exposing
    // which ones here - the actual list lives behind BackupManager.issues.
    data class PartiallyCompleted(val lastSyncTimeStamp: Long, val issueCount: Int) : SyncStatus

    data class Failed(val errorMessage: String) : SyncStatus
}
