package com.rignis.backup.core.manager

import com.rignis.auth.domain.CipherManager
import com.rignis.backup.api.BackupAuthHost
import com.rignis.backup.api.BackupManager
import com.rignis.backup.api.BackupState
import com.rignis.backup.api.BackupStager
import com.rignis.backup.api.ConflictResolution
import com.rignis.backup.api.SyncIssue
import com.rignis.backup.api.SyncIssueKind
import com.rignis.backup.api.SyncReport
import com.rignis.backup.api.SyncStatus
import com.rignis.backup.api.SyncTrigger
import com.rignis.backup.core.auth.GoogleAccountAuthenticator
import com.rignis.backup.core.auth.GoogleAuthException
import com.rignis.backup.core.code.BackupCodeStore
import com.rignis.backup.core.crypto.BackupCodeGenerator
import com.rignis.backup.core.crypto.BackupKeyHolder
import com.rignis.backup.core.drive.DriveClient
import com.rignis.backup.core.drive.DriveException
import com.rignis.backup.core.meta.VaultMetaStore
import com.rignis.backup.core.meta.WrongBackupPasswordException
import com.rignis.backup.core.sync.SyncEngine
import com.rignis.store.api.BackupSettings
import com.rignis.store.api.DataStore
import com.rignis.store.api.EncryptedDataEntry
import com.rignis.store.api.SecretSyncRef
import com.rignis.store.api.StagedBackupBlob
import com.rignis.store.api.SyncDataStore
import com.rignis.store.api.SyncFailureKind
import com.rignis.store.api.SyncItemRecord
import com.rignis.store.api.SyncItemStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BackupManagerImpl(
    private val authenticator: GoogleAccountAuthenticator,
    private val driveClient: DriveClient,
    private val vaultMetaStore: VaultMetaStore,
    private val keyHolder: BackupKeyHolder,
    private val backupStager: BackupStager,
    private val syncEngine: SyncEngine,
    private val syncDataStore: SyncDataStore,
    private val dataStore: DataStore,
    private val codeStore: BackupCodeStore
) : BackupManager {

    // Guards every method below - an app-open sync and a manual "Back up
    // now" (or a conflict resolution mid-sync) must never interleave.
    private val mutex = Mutex()

    private val _backupState = MutableStateFlow<BackupState>(BackupState.NotConfigured)
    override val backupState: StateFlow<BackupState> get() = _backupState

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Disabled)
    override val syncStatus: StateFlow<SyncStatus> get() = _syncStatus

    override val issues: Flow<List<SyncIssue>> get() = syncDataStore.observeIssues().map { list -> list.map { it.toIssue() } }
    override val notBackedUpCount: Flow<Int> get() = syncDataStore.observeNotBackedUpCount()

    // The Drive access token, kept only for this process's lifetime - same
    // "no persisted key material" rule the backup key itself follows.
    @Volatile
    private var cachedAccessToken: String? = null

    private var restored = false

    private suspend fun ensureStateRestored() {
        if (restored) return
        val settings = syncDataStore.backupSettings()
        _backupState.value = when {
            settings == null -> BackupState.NotConfigured
            keyHolder.available.value -> BackupState.Ready(settings.accountEmail, settings.lastSyncAt ?: 0L)
            else -> BackupState.NeedsCode(settings.accountEmail)
        }
        _syncStatus.value = if (settings == null) SyncStatus.Disabled else SyncStatus.Idle(settings.lastSyncAt ?: 0L)
        restored = true
    }

    override fun generateBackupCode(): String = BackupCodeGenerator.generate()

    override suspend fun linkAccount(host: BackupAuthHost): Result<String> = mutex.withLock {
        ensureStateRestored()
        val identity = authenticator.signIn(host.activity).getOrElse { return@withLock Result.failure(it) }
        val token = authenticator.authorizeDriveAppData(host).getOrElse { return@withLock Result.failure(it) }
        cachedAccessToken = token
        syncDataStore.saveBackupSettings(
            BackupSettings(identity.accountEmail, keyEpoch = null, lastSyncAt = null, storedCode = null)
        )
        _backupState.value = BackupState.NeedsCode(identity.accountEmail)
        Result.success(identity.accountEmail)
    }

    // Also handles linking a second device: if no vault exists on Drive
    // yet, [code] becomes the new one; if one already exists, this behaves
    // like unlockWithCode instead of destroying it. Either way the code is
    // then stored on-device so routine syncs never need it retyped.
    override suspend fun enableBackup(
        host: BackupAuthHost, cipherManager: CipherManager, code: CharArray
    ): Result<Unit> = mutex.withLock {
        ensureStateRestored()
        val settings = syncDataStore.backupSettings()
            ?: return@withLock Result.failure(IllegalStateException("Link a Google account first"))
        val token = cachedAccessToken ?: authenticator.authorizeDriveAppData(host)
            .getOrElse { return@withLock Result.failure(it) }.also { cachedAccessToken = it }

        val existingMeta = vaultMetaStore.fetch(token).getOrElse { return@withLock Result.failure(it) }
        val unlocked = if (existingMeta == null) {
            vaultMetaStore.setPassword(token, code)
        } else {
            vaultMetaStore.unlockWithPassword(token, code)
        }.getOrElse { return@withLock Result.failure(it) }

        keyHolder.unlock(unlocked.derivedKey.keyBytes, unlocked.keyEpoch)
        syncDataStore.clearStaleEpochBlobs(unlocked.keyEpoch)
        codeStore.store(cipherManager, code)
        syncDataStore.saveBackupSettings(settings.copy(keyEpoch = unlocked.keyEpoch))
        _backupState.value = BackupState.Ready(settings.accountEmail, settings.lastSyncAt ?: 0L)
        Result.success(Unit)
    }

    override suspend fun unlockWithCode(cipherManager: CipherManager, code: CharArray): Result<Unit> =
        mutex.withLock { unlockWithCodeLocked(cipherManager, code) }

    // Callers already holding the mutex (syncNow's auto-unlock) call this
    // directly instead of unlockWithCode, to avoid re-entrant deadlock.
    private suspend fun unlockWithCodeLocked(cipherManager: CipherManager, code: CharArray): Result<Unit> {
        ensureStateRestored()
        val settings = syncDataStore.backupSettings()
            ?: return Result.failure(IllegalStateException("No account linked"))
        val token = cachedAccessToken
            ?: return Result.failure(IllegalStateException("Reauthorize via sync or linkAccount first"))

        val unlocked = vaultMetaStore.unlockWithPassword(token, code).getOrElse { return Result.failure(it) }
        keyHolder.unlock(unlocked.derivedKey.keyBytes, unlocked.keyEpoch)
        syncDataStore.clearStaleEpochBlobs(unlocked.keyEpoch)
        codeStore.store(cipherManager, code)
        syncDataStore.saveBackupSettings(settings.copy(keyEpoch = unlocked.keyEpoch))
        _backupState.value = BackupState.Ready(settings.accountEmail, settings.lastSyncAt ?: 0L)
        return Result.success(Unit)
    }

    override fun lockBackupKey() {
        keyHolder.lock()
        (_backupState.value as? BackupState.Ready)?.let {
            _backupState.value = BackupState.NeedsCode(it.accountEmail)
        }
    }

    override suspend fun revealStoredCode(cipherManager: CipherManager): Result<CharArray> = mutex.withLock {
        ensureStateRestored()
        codeStore.retrieve(cipherManager)
    }

    override suspend fun changeBackupKey(
        host: BackupAuthHost, cipherManager: CipherManager, oldCode: CharArray, newCode: CharArray
    ): Result<Unit> = mutex.withLock {
        ensureStateRestored()
        val settings = syncDataStore.backupSettings()
            ?: return@withLock Result.failure(IllegalStateException("No account linked"))
        val token = cachedAccessToken ?: authenticator.authorizeDriveAppData(host)
            .getOrElse { return@withLock Result.failure(it) }.also { cachedAccessToken = it }

        vaultMetaStore.unlockWithPassword(token, oldCode).getOrElse { return@withLock Result.failure(it) }
        val unlocked = vaultMetaStore.setPassword(token, newCode).getOrElse { return@withLock Result.failure(it) }

        keyHolder.unlock(unlocked.derivedKey.keyBytes, unlocked.keyEpoch)
        // Every existing Drive file is still under the old key - force a
        // full re-push (opportunistic, as each secret is reopened; never a
        // bulk decrypt) rather than leaving old backups permanently unreadable.
        syncDataStore.bumpAllVersionsForRekey()
        codeStore.store(cipherManager, newCode)
        syncDataStore.saveBackupSettings(settings.copy(keyEpoch = unlocked.keyEpoch))
        _backupState.value = BackupState.Ready(settings.accountEmail, settings.lastSyncAt ?: 0L)
        Result.success(Unit)
    }

    override suspend fun disableBackup(deleteCloudCopy: Boolean): Result<Unit> = mutex.withLock {
        ensureStateRestored()
        if (deleteCloudCopy) {
            cachedAccessToken?.let { token ->
                driveClient.listAppDataFiles(token).getOrNull()?.forEach { file ->
                    driveClient.deleteFile(token, file.fileId)
                }
            }
            // No cached token (cold start, never reauthorized this session):
            // best-effort only - disabling backup doesn't force a network
            // round trip just to delete the cloud copy.
        }
        keyHolder.lock()
        syncDataStore.wipeAllSyncState()
        syncDataStore.clearBackupSettings()
        cachedAccessToken = null
        _backupState.value = BackupState.NotConfigured
        _syncStatus.value = SyncStatus.Disabled
        Result.success(Unit)
    }

    override suspend fun syncNow(
        host: BackupAuthHost, cipherManager: CipherManager, trigger: SyncTrigger
    ): SyncReport = mutex.withLock {
        ensureStateRestored()
        val settings = syncDataStore.backupSettings() ?: return@withLock emptyReport()
        _syncStatus.value = SyncStatus.InProgress

        val token = authenticator.authorizeDriveAppData(host).getOrElse {
            _syncStatus.value = SyncStatus.Failed(it.toSafeMessage())
            return@withLock emptyReport(failed = 1)
        }
        cachedAccessToken = token

        // A backup key changed on another device shows up as Drive's vault
        // epoch no longer matching the key we're currently holding.
        val remoteMeta = vaultMetaStore.fetch(token).getOrNull()
        if (remoteMeta != null && keyHolder.available.value && keyHolder.keyEpoch != remoteMeta.keyEpoch) {
            keyHolder.lock()
            _backupState.value = BackupState.PasswordChangedElsewhere(settings.accountEmail)
            _syncStatus.value = SyncStatus.Failed("password_changed_elsewhere")
            return@withLock emptyReport()
        }

        // Not held in memory yet this session - try the on-device stored
        // code first (one biometric prompt) before asking the user to type
        // anything. A mismatch here means the wrong code or the wrong
        // Google account, not a transient failure - never keep retrying.
        if (!keyHolder.available.value) {
            val autoUnlocked = codeStore.retrieve(cipherManager).fold(
                onSuccess = { code ->
                    val result = unlockWithCodeLocked(cipherManager, code)
                    code.fill(' ')
                    result.isSuccess
                },
                onFailure = { false }
            )
            if (!autoUnlocked) {
                _backupState.value = if (syncDataStore.backupSettings()?.storedCode != null) {
                    BackupState.CodeMismatch(settings.accountEmail)
                } else {
                    BackupState.NeedsCode(settings.accountEmail)
                }
                _syncStatus.value = SyncStatus.Idle(settings.lastSyncAt ?: 0L)
                return@withLock emptyReport()
            }
        }

        backupStager.stageAllPendingTombstones()
        val outcome = syncEngine.run(token, cipherManager)
        val now = System.currentTimeMillis()
        syncDataStore.saveBackupSettings(settings.copy(lastSyncAt = now))
        _backupState.value = BackupState.Ready(settings.accountEmail, now)
        val issueCount = outcome.failed + outcome.skippedNotStaged + outcome.conflicts
        _syncStatus.value = if (issueCount > 0) SyncStatus.PartiallyCompleted(now, issueCount) else SyncStatus.Idle(now)

        SyncReport(outcome.pushed, outcome.pulled, outcome.conflicts, outcome.failed, outcome.skippedNotStaged, now)
    }

    override suspend fun resolveIssue(
        secretId: String, resolution: ConflictResolution, host: BackupAuthHost, cipherManager: CipherManager
    ): Result<Unit> = mutex.withLock {
        ensureStateRestored()
        if (resolution == ConflictResolution.DEFER_TO_USER) return@withLock Result.success(Unit)

        val token = authenticator.authorizeDriveAppData(host).getOrElse { return@withLock Result.failure(it) }
        cachedAccessToken = token
        if (!keyHolder.available.value) {
            return@withLock Result.failure(IllegalStateException("Backup key is locked"))
        }

        when (resolution) {
            ConflictResolution.ACCEPT_REMOTE -> {
                val parked = syncDataStore.parkedRemote(secretId)
                    ?: return@withLock Result.failure(IllegalStateException("Nothing parked for $secretId"))
                if (parked.remoteDeleted) {
                    syncDataStore.applyRemoteTombstone(secretId, parked.remoteVersion, parked.remoteUpdatedAt)
                } else {
                    val ref = SecretSyncRef(
                        secretId, parked.title, parked.remoteVersion, parked.remoteUpdatedAt, null, parked.remoteVersion
                    )
                    val entry = EncryptedDataEntry(parked.title, parked.encryptedBody, parked.iv)
                    val blob = StagedBackupBlob(
                        secretId, parked.remoteVersion, parked.backupCipherText, parked.backupIv,
                        keyHolder.keyEpoch ?: return@withLock Result.failure(IllegalStateException("Backup key is locked"))
                    )
                    syncDataStore.applyRemoteSecret(ref, entry, blob, parked.remoteFileId)
                }
                syncDataStore.clearParkedRemote(secretId)
            }

            ConflictResolution.ACCEPT_LOCAL -> {
                val ref = syncDataStore.syncRef(secretId)
                    ?: return@withLock Result.failure(IllegalStateException("Secret $secretId not found"))
                val issue = syncDataStore.issueRecord(secretId)
                val newVersion = maxOf(ref.version, issue?.remoteVersion ?: 0L) + 1
                val bumped = syncDataStore.bumpVersionForOverride(secretId, newVersion)
                if (bumped.isDeleted) {
                    backupStager.stageFor(secretId, bumped.version, "", ByteArray(0))
                } else {
                    val item = dataStore.getDataById(secretId)
                        ?: return@withLock Result.failure(IllegalStateException("Secret $secretId not found"))
                    val plaintext = cipherManager.decryptData(item.encryptedBody, item.initializationVector)
                        .getOrElse { return@withLock Result.failure(it) }
                    backupStager.stageFor(secretId, bumped.version, item.title, plaintext)
                }
                syncDataStore.clearParkedRemote(secretId)
            }

            ConflictResolution.DEFER_TO_USER -> Unit
        }

        val outcome = syncEngine.run(token, cipherManager)
        if (outcome.failed > 0) {
            Result.failure(IllegalStateException("Resolution succeeded locally but re-sync reported failures"))
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun retryAllFailed(host: BackupAuthHost, cipherManager: CipherManager): SyncReport =
        syncNow(host, cipherManager, SyncTrigger.ISSUE_RETRY)

    private fun emptyReport(failed: Int = 0) = SyncReport(0, 0, 0, failed, 0, System.currentTimeMillis())

    private fun Throwable.toSafeMessage(): String = when (this) {
        is DriveException.NetworkFailure -> "network_error"
        is DriveException.AuthRequired -> "auth_error"
        is DriveException.RateLimited -> "rate_limited"
        is DriveException.ServerError, is DriveException.UnexpectedResponse -> "server_error"
        is DriveException.NotFound -> "not_found"
        is GoogleAuthException -> "google_auth_error"
        is WrongBackupPasswordException -> "code_mismatch"
        else -> "unknown_error"
    }

    private fun SyncItemRecord.toIssue(): SyncIssue {
        val kind = when (status) {
            SyncItemStatus.CONFLICT -> SyncIssueKind.CONFLICT
            SyncItemStatus.NOT_STAGED -> SyncIssueKind.NOT_STAGED
            else -> when (failureKind) {
                SyncFailureKind.AUTH -> SyncIssueKind.AUTH_REQUIRED
                SyncFailureKind.UNREADABLE_REMOTE -> SyncIssueKind.UNREADABLE_REMOTE
                else -> SyncIssueKind.UPLOAD_FAILED
            }
        }
        return SyncIssue(
            secretId = secretId,
            title = title,
            kind = kind,
            localVersion = localVersion,
            localUpdatedAt = localUpdatedAt,
            localDeleted = localDeleted,
            remoteVersion = remoteVersion,
            remoteUpdatedAt = remoteUpdatedAt,
            remoteDeleted = remoteDeleted,
            canPullFromCloud = kind == SyncIssueKind.CONFLICT,
            canOverrideCloud = kind != SyncIssueKind.AUTH_REQUIRED,
            retryable = kind != SyncIssueKind.CONFLICT,
            lastAttemptAt = lastAttemptAt
        )
    }
}
