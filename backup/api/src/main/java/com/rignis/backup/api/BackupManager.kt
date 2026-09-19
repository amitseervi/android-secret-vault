package com.rignis.backup.api

import com.rignis.auth.domain.CipherManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BackupManager {
    val backupState: StateFlow<BackupState>
    val syncStatus: StateFlow<SyncStatus>
    val issues: Flow<List<SyncIssue>>
    val notBackedUpCount: Flow<Int>

    suspend fun linkAccount(host: BackupAuthHost): Result<String>
    suspend fun enableBackup(host: BackupAuthHost, password: CharArray): Result<Unit>
    suspend fun unlockWithPassword(password: CharArray): Result<Unit>
    fun lockBackupKey()
    suspend fun changeBackupPassword(host: BackupAuthHost, old: CharArray, new: CharArray): Result<Unit>
    suspend fun disableBackup(deleteCloudCopy: Boolean): Result<Unit>

    suspend fun syncNow(host: BackupAuthHost, cipherManager: CipherManager, trigger: SyncTrigger): SyncReport
    suspend fun resolveIssue(
        secretId: String, resolution: ConflictResolution, host: BackupAuthHost, cipherManager: CipherManager
    ): Result<Unit>

    suspend fun retryAllFailed(host: BackupAuthHost, cipherManager: CipherManager): SyncReport
}
