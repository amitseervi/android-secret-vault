package com.rignis.store.api

sealed interface SyncStatus {
    data class Idle(val lastSyncTimeStamp: Long) : SyncStatus

    data object InProgress : SyncStatus

    data class Failed(val errorMessage: String) : SyncStatus
}