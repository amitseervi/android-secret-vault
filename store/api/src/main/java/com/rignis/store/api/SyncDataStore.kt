package com.rignis.store.api

import kotlinx.coroutines.flow.Flow

// Local-persistence side of cloud backup: versions, staged (already
// backup-encrypted) blobs, parked conflicting remote copies, and per-item
// sync outcomes. Deliberately separate from DataStore so store:core never
// needs to depend on any network/Google/crypto library - only :backup:core
// depends on this.
interface SyncDataStore {
    suspend fun localSyncRefs(): List<SecretSyncRef>
    suspend fun syncRef(secretId: String): SecretSyncRef?
    fun observeNotBackedUpCount(): Flow<Int>
    fun observeIssues(): Flow<List<SyncItemRecord>>
    suspend fun issueRecord(secretId: String): SyncItemRecord?

    suspend fun stagedBlob(secretId: String): StagedBackupBlob?
    suspend fun stageBlob(blob: StagedBackupBlob)
    suspend fun idsNeedingStaging(): List<String>
    suspend fun idsNeedingTombstoneStaging(): List<String>
    suspend fun clearAllStagedBlobs()
    suspend fun clearStaleEpochBlobs(currentEpoch: String)

    suspend fun applyRemoteSecret(
        ref: SecretSyncRef, local: EncryptedDataEntry, alreadyBackedUp: StagedBackupBlob, remoteFileId: String
    )

    suspend fun applyRemoteTombstone(secretId: String, remoteVersion: Long, at: Long)
    suspend fun bumpVersionForOverride(secretId: String, newVersion: Long): SecretSyncRef

    suspend fun parkRemote(parked: ParkedRemoteSecret)
    suspend fun parkedRemote(secretId: String): ParkedRemoteSecret?
    suspend fun clearParkedRemote(secretId: String)

    suspend fun markSynced(secretId: String, version: Long, remoteFileId: String)
    suspend fun recordNotStaged(secretId: String)
    suspend fun recordFailure(secretId: String, kind: SyncFailureKind, detail: String?)
    suspend fun recordConflict(
        secretId: String, remoteVersion: Long, remoteUpdatedAt: Long, remoteDeleted: Boolean, remoteFileId: String
    )

    suspend fun purgeConfirmedTombstones(olderThanMillis: Long)
    suspend fun wipeAllSyncState()
    suspend fun bumpAllVersionsForRekey()

    suspend fun backupSettings(): BackupSettings?
    suspend fun saveBackupSettings(settings: BackupSettings)
    suspend fun clearBackupSettings()
}
