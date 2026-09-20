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

    // Generates a fresh XXXX-XXXX-XXXX-XXXX backup key. Pure/non-suspend -
    // the caller is responsible for showing it to the user before it's used,
    // since this is the user's only copy until they transcribe it themselves.
    fun generateBackupCode(): String

    // First-time setup (mints a new vault) or entering a previously-generated
    // code when linking a second device (validates against the existing
    // vault instead) - enableBackup figures out which via Drive's vault meta.
    // Either way, [code] is also stored on-device (Keystore + biometric) so
    // routine syncs never need it retyped.
    suspend fun enableBackup(host: BackupAuthHost, cipherManager: CipherManager, code: CharArray): Result<Unit>

    // Manual fallback when the on-device stored code is missing, wrong, or
    // syncNow's automatic unlock failed for any other reason.
    suspend fun unlockWithCode(cipherManager: CipherManager, code: CharArray): Result<Unit>

    fun lockBackupKey()

    // For a Settings "show my backup key" affordance - biometric-gated,
    // needed before setting up a second device since Keystore storage never
    // travels on its own.
    suspend fun revealStoredCode(cipherManager: CipherManager): Result<CharArray>

    suspend fun changeBackupKey(
        host: BackupAuthHost, cipherManager: CipherManager, oldCode: CharArray, newCode: CharArray
    ): Result<Unit>

    suspend fun disableBackup(deleteCloudCopy: Boolean): Result<Unit>

    // Auto-unlocks from the on-device stored code (one biometric prompt) if
    // the key isn't already held in memory, before running the sync pass.
    suspend fun syncNow(host: BackupAuthHost, cipherManager: CipherManager, trigger: SyncTrigger): SyncReport
    suspend fun resolveIssue(
        secretId: String, resolution: ConflictResolution, host: BackupAuthHost, cipherManager: CipherManager
    ): Result<Unit>

    suspend fun retryAllFailed(host: BackupAuthHost, cipherManager: CipherManager): SyncReport
}
