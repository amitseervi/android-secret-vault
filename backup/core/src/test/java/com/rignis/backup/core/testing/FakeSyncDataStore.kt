package com.rignis.backup.core.testing

import com.rignis.store.api.BackupSettings
import com.rignis.store.api.EncryptedDataEntry
import com.rignis.store.api.ParkedRemoteSecret
import com.rignis.store.api.SecretSyncRef
import com.rignis.store.api.StagedBackupBlob
import com.rignis.store.api.SyncDataStore
import com.rignis.store.api.SyncFailureKind
import com.rignis.store.api.SyncItemRecord
import com.rignis.store.api.SyncItemStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// In-memory stand-in for the whole SyncDataStore contract - shared by
// BackupStagerImplTest and SyncEngineTest so neither needs a real Room DB
// (that's already covered separately by store:core's own instrumented tests).
class FakeSyncDataStore : SyncDataStore {
    val secrets = mutableMapOf<String, SecretSyncRef>()
    val stagedBlobs = mutableMapOf<String, StagedBackupBlob>()
    val parkedRemotes = mutableMapOf<String, ParkedRemoteSecret>()
    val itemStates = mutableMapOf<String, SyncItemRecord>()
    val appliedEntries = mutableMapOf<String, EncryptedDataEntry>()
    var backupSettingsValue: BackupSettings? = null

    fun putSecret(ref: SecretSyncRef) {
        secrets[ref.id] = ref
    }

    override suspend fun localSyncRefs(): List<SecretSyncRef> = secrets.values.toList()
    override suspend fun syncRef(secretId: String): SecretSyncRef? = secrets[secretId]
    override fun observeNotBackedUpCount(): Flow<Int> = MutableStateFlow(0)
    override fun observeIssues(): Flow<List<SyncItemRecord>> = MutableStateFlow(itemStates.values.toList())
    override suspend fun issueRecord(secretId: String): SyncItemRecord? = itemStates[secretId]

    override suspend fun stagedBlob(secretId: String): StagedBackupBlob? = stagedBlobs[secretId]
    override suspend fun stageBlob(blob: StagedBackupBlob) {
        stagedBlobs[blob.secretId] = blob
    }

    override suspend fun idsNeedingStaging(): List<String> =
        secrets.values.filter { !it.isDeleted && stagedBlobs[it.id]?.version != it.version }.map { it.id }

    override suspend fun idsNeedingTombstoneStaging(): List<String> =
        secrets.values.filter { it.isDeleted && stagedBlobs[it.id]?.version != it.version }.map { it.id }

    override suspend fun clearAllStagedBlobs() = stagedBlobs.clear()
    override suspend fun clearStaleEpochBlobs(currentEpoch: String) {
        stagedBlobs.entries.removeAll { it.value.keyEpoch != currentEpoch }
    }

    override suspend fun applyRemoteSecret(
        ref: SecretSyncRef, local: EncryptedDataEntry, alreadyBackedUp: StagedBackupBlob, remoteFileId: String
    ) {
        secrets[ref.id] = ref
        stagedBlobs[ref.id] = alreadyBackedUp
        appliedEntries[ref.id] = local
        itemStates[ref.id] = blankRecord(ref.id).copy(
            title = ref.title, status = SyncItemStatus.SYNCED, localVersion = ref.version,
            localUpdatedAt = ref.updatedAt, remoteVersion = ref.version, remoteUpdatedAt = ref.updatedAt
        )
    }

    override suspend fun applyRemoteTombstone(secretId: String, remoteVersion: Long, at: Long) {
        val existing = secrets[secretId]
        secrets[secretId] = SecretSyncRef(secretId, existing?.title ?: "", remoteVersion, at, at, remoteVersion)
        itemStates[secretId] = blankRecord(secretId).copy(
            status = SyncItemStatus.SYNCED, localVersion = remoteVersion, localUpdatedAt = at, localDeleted = true,
            remoteVersion = remoteVersion, remoteUpdatedAt = at, remoteDeleted = true
        )
    }

    override suspend fun bumpVersionForOverride(secretId: String, newVersion: Long): SecretSyncRef {
        val existing = secrets[secretId] ?: error("secret $secretId not found")
        val updated = existing.copy(version = newVersion, updatedAt = System.currentTimeMillis())
        secrets[secretId] = updated
        return updated
    }

    override suspend fun parkRemote(parked: ParkedRemoteSecret) {
        parkedRemotes[parked.secretId] = parked
    }

    override suspend fun parkedRemote(secretId: String): ParkedRemoteSecret? = parkedRemotes[secretId]
    override suspend fun clearParkedRemote(secretId: String) {
        parkedRemotes.remove(secretId)
    }

    override suspend fun markSynced(secretId: String, version: Long, remoteFileId: String) {
        val existing = secrets[secretId] ?: return
        secrets[secretId] = existing.copy(lastSyncedVersion = version)
        itemStates[secretId] = blankRecord(secretId).copy(
            title = existing.title, status = SyncItemStatus.SYNCED, localVersion = version,
            localUpdatedAt = existing.updatedAt, localDeleted = existing.isDeleted,
            remoteVersion = version, remoteUpdatedAt = existing.updatedAt
        )
    }

    override suspend fun recordNotStaged(secretId: String) {
        val existing = itemStates[secretId] ?: blankRecord(secretId)
        itemStates[secretId] = existing.copy(status = SyncItemStatus.NOT_STAGED)
    }

    override suspend fun recordFailure(secretId: String, kind: SyncFailureKind, detail: String?) {
        val existing = itemStates[secretId] ?: blankRecord(secretId)
        itemStates[secretId] = existing.copy(
            status = SyncItemStatus.FAILED, failureKind = kind, failureDetail = detail,
            attemptCount = existing.attemptCount + 1, lastAttemptAt = System.currentTimeMillis()
        )
    }

    override suspend fun recordConflict(
        secretId: String, remoteVersion: Long, remoteUpdatedAt: Long, remoteDeleted: Boolean, remoteFileId: String
    ) {
        val existing = itemStates[secretId] ?: blankRecord(secretId)
        itemStates[secretId] = existing.copy(
            status = SyncItemStatus.CONFLICT, remoteVersion = remoteVersion, remoteUpdatedAt = remoteUpdatedAt,
            remoteDeleted = remoteDeleted, lastAttemptAt = System.currentTimeMillis()
        )
    }

    override suspend fun purgeConfirmedTombstones(olderThanMillis: Long) = Unit
    override suspend fun wipeAllSyncState() {
        stagedBlobs.clear()
        parkedRemotes.clear()
        itemStates.clear()
    }

    override suspend fun bumpAllVersionsForRekey() {
        stagedBlobs.clear()
        secrets.replaceAll { _, ref -> ref.copy(version = ref.version + 1) }
    }

    override suspend fun backupSettings(): BackupSettings? = backupSettingsValue
    override suspend fun saveBackupSettings(settings: BackupSettings) {
        backupSettingsValue = settings
    }

    override suspend fun clearBackupSettings() {
        backupSettingsValue = null
    }

    private fun blankRecord(secretId: String) = SyncItemRecord(
        secretId = secretId, title = null, status = SyncItemStatus.NOT_STAGED, localVersion = null,
        localUpdatedAt = null, localDeleted = false, remoteVersion = null, remoteUpdatedAt = null,
        remoteDeleted = false, failureKind = null, failureDetail = null, attemptCount = 0, lastAttemptAt = 0L
    )
}
