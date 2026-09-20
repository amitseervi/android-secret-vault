package com.rignis.store.core

import androidx.room.withTransaction
import com.rignis.common.ExecutorFactory
import com.rignis.store.api.BackupSettings
import com.rignis.store.api.EncryptedDataEntry
import com.rignis.store.api.ParkedRemoteSecret
import com.rignis.store.api.SecretSyncRef
import com.rignis.store.api.StagedBackupBlob
import com.rignis.store.api.StoredBackupCode
import com.rignis.store.api.SyncDataStore
import com.rignis.store.api.SyncFailureKind
import com.rignis.store.api.SyncItemRecord
import com.rignis.store.api.SyncItemStatus
import com.rignis.store.core.local.SecretBackupBlob
import com.rignis.store.core.local.SecretData
import com.rignis.store.core.local.SecretRemoteStaged
import com.rignis.store.core.local.SyncIssueRow
import com.rignis.store.core.local.SyncItemState
import com.rignis.store.core.local.UserSetting
import com.rignis.store.core.local.UserSettingKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SyncDataStoreImpl(
    private val dataStoreFactory: DataStoreFactory, private val executorFactory: ExecutorFactory
) : SyncDataStore {

    private val db get() = dataStoreFactory.db
    private val secretDao get() = db.secretStoreDao()
    private val syncDao get() = db.syncDao()
    private val settingDao get() = db.userSettingDao()

    override suspend fun localSyncRefs(): List<SecretSyncRef> = withContext(executorFactory.backgroundDispatcher) {
        syncDao.allSyncRefs().map {
            SecretSyncRef(it.id, it.title, it.version, it.updatedAt, it.deletedAt, it.lastSyncedVersion)
        }
    }

    override suspend fun syncRef(secretId: String): SecretSyncRef? =
        withContext(executorFactory.backgroundDispatcher) {
            syncDao.syncRef(secretId)?.let {
                SecretSyncRef(it.id, it.title, it.version, it.updatedAt, it.deletedAt, it.lastSyncedVersion)
            }
        }

    override fun observeNotBackedUpCount(): Flow<Int> = syncDao.observeNotBackedUpCount()

    override fun observeIssues(): Flow<List<SyncItemRecord>> = syncDao.observeIssues().map { rows ->
        rows.map { it.toRecord() }
    }

    override suspend fun issueRecord(secretId: String): SyncItemRecord? =
        withContext(executorFactory.backgroundDispatcher) {
            syncDao.issueRow(secretId)?.toRecord()
        }

    override suspend fun stagedBlob(secretId: String): StagedBackupBlob? =
        withContext(executorFactory.backgroundDispatcher) {
            syncDao.stagedBlob(secretId)?.toApi()
        }

    override suspend fun stageBlob(blob: StagedBackupBlob) = withContext(executorFactory.backgroundDispatcher) {
        syncDao.stageBlob(
            SecretBackupBlob(
                secretId = blob.secretId,
                version = blob.version,
                backupEncryptedData = blob.cipherText,
                backupIv = blob.iv,
                keyEpoch = blob.keyEpoch,
                stagedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun idsNeedingStaging(): List<String> = withContext(executorFactory.backgroundDispatcher) {
        syncDao.idsNeedingStaging()
    }

    override suspend fun idsNeedingTombstoneStaging(): List<String> =
        withContext(executorFactory.backgroundDispatcher) {
            syncDao.idsNeedingTombstoneStaging()
        }

    override suspend fun clearAllStagedBlobs() = withContext(executorFactory.backgroundDispatcher) {
        syncDao.clearAllStagedBlobs()
    }

    override suspend fun clearStaleEpochBlobs(currentEpoch: String) =
        withContext(executorFactory.backgroundDispatcher) {
            syncDao.clearStaleEpochBlobs(currentEpoch)
        }

    override suspend fun applyRemoteSecret(
        ref: SecretSyncRef, local: EncryptedDataEntry, alreadyBackedUp: StagedBackupBlob, remoteFileId: String
    ) = withContext(executorFactory.backgroundDispatcher) {
        db.withTransaction {
            secretDao.insertItem(
                SecretData(
                    id = ref.id,
                    title = local.title,
                    encryptedData = local.encryptedBody,
                    initializationVector = local.initializationVector,
                    version = ref.version,
                    updatedAt = ref.updatedAt,
                    deletedAt = null,
                    lastSyncedVersion = ref.version
                )
            )
            syncDao.stageBlob(
                SecretBackupBlob(
                    secretId = ref.id,
                    version = alreadyBackedUp.version,
                    backupEncryptedData = alreadyBackedUp.cipherText,
                    backupIv = alreadyBackedUp.iv,
                    keyEpoch = alreadyBackedUp.keyEpoch,
                    stagedAt = System.currentTimeMillis()
                )
            )
            syncDao.upsertItemState(syncedState(ref.id, remoteFileId, ref.version, ref.updatedAt, false))
        }
    }

    override suspend fun applyRemoteTombstone(secretId: String, remoteVersion: Long, at: Long) =
        withContext(executorFactory.backgroundDispatcher) {
            db.withTransaction {
                secretDao.applyRemoteTombstone(secretId, remoteVersion, at)
                syncDao.upsertItemState(syncedState(secretId, null, remoteVersion, at, true))
            }
        }

    override suspend fun bumpVersionForOverride(secretId: String, newVersion: Long): SecretSyncRef =
        withContext(executorFactory.backgroundDispatcher) {
            val now = System.currentTimeMillis()
            secretDao.bumpVersionForOverride(secretId, newVersion, now)
            val updated = secretDao.getRawById(secretId) ?: error("Secret $secretId vanished during override")
            SecretSyncRef(
                updated.id, updated.title, updated.version, updated.updatedAt, updated.deletedAt,
                updated.lastSyncedVersion
            )
        }

    override suspend fun parkRemote(parked: ParkedRemoteSecret) = withContext(executorFactory.backgroundDispatcher) {
        syncDao.parkRemote(
            SecretRemoteStaged(
                secretId = parked.secretId,
                remoteVersion = parked.remoteVersion,
                remoteUpdatedAt = parked.remoteUpdatedAt,
                remoteDeleted = parked.remoteDeleted,
                remoteFileId = parked.remoteFileId,
                title = parked.title,
                encryptedData = parked.encryptedBody,
                initializationVector = parked.iv,
                backupCipherText = parked.backupCipherText,
                backupIv = parked.backupIv,
                fetchedAt = parked.fetchedAt
            )
        )
    }

    override suspend fun parkedRemote(secretId: String): ParkedRemoteSecret? =
        withContext(executorFactory.backgroundDispatcher) {
            syncDao.parkedRemote(secretId)?.let {
                ParkedRemoteSecret(
                    it.secretId, it.remoteVersion, it.remoteUpdatedAt, it.remoteDeleted, it.remoteFileId,
                    it.title, it.encryptedData, it.initializationVector, it.backupCipherText, it.backupIv, it.fetchedAt
                )
            }
        }

    override suspend fun clearParkedRemote(secretId: String) = withContext(executorFactory.backgroundDispatcher) {
        syncDao.clearParkedRemote(secretId)
    }

    override suspend fun markSynced(secretId: String, version: Long, remoteFileId: String) =
        withContext(executorFactory.backgroundDispatcher) {
            db.withTransaction {
                secretDao.markSynced(secretId, version)
                val local = secretDao.getRawById(secretId)
                syncDao.upsertItemState(
                    syncedState(secretId, remoteFileId, version, local?.updatedAt ?: 0L, local?.deletedAt != null)
                )
            }
        }

    override suspend fun recordNotStaged(secretId: String) = withContext(executorFactory.backgroundDispatcher) {
        val existing = syncDao.getItemState(secretId)
        syncDao.upsertItemState(
            SyncItemState(
                secretId = secretId,
                remoteFileId = existing?.remoteFileId,
                remoteVersion = existing?.remoteVersion,
                remoteUpdatedAt = existing?.remoteUpdatedAt,
                remoteDeleted = existing?.remoteDeleted ?: false,
                status = SyncItemStatus.NOT_STAGED.name,
                failureKind = null,
                failureDetail = null,
                attemptCount = existing?.attemptCount ?: 0,
                lastAttemptAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun recordFailure(secretId: String, kind: SyncFailureKind, detail: String?) =
        withContext(executorFactory.backgroundDispatcher) {
            val existing = syncDao.getItemState(secretId)
            val local = secretDao.getRawById(secretId)
            syncDao.upsertItemState(
                SyncItemState(
                    secretId = secretId,
                    remoteFileId = existing?.remoteFileId,
                    remoteVersion = existing?.remoteVersion,
                    remoteUpdatedAt = existing?.remoteUpdatedAt,
                    remoteDeleted = existing?.remoteDeleted ?: false,
                    status = SyncItemStatus.FAILED.name,
                    failureKind = kind.name,
                    failureDetail = detail,
                    attemptCount = (existing?.attemptCount ?: 0) + 1,
                    lastAttemptAt = System.currentTimeMillis()
                )
            )
        }

    override suspend fun recordConflict(
        secretId: String, remoteVersion: Long, remoteUpdatedAt: Long, remoteDeleted: Boolean, remoteFileId: String
    ) = withContext(executorFactory.backgroundDispatcher) {
        val existing = syncDao.getItemState(secretId)
        syncDao.upsertItemState(
            SyncItemState(
                secretId = secretId,
                remoteFileId = remoteFileId,
                remoteVersion = remoteVersion,
                remoteUpdatedAt = remoteUpdatedAt,
                remoteDeleted = remoteDeleted,
                status = SyncItemStatus.CONFLICT.name,
                failureKind = null,
                failureDetail = null,
                attemptCount = (existing?.attemptCount ?: 0) + 1,
                lastAttemptAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun purgeConfirmedTombstones(olderThanMillis: Long) =
        withContext(executorFactory.backgroundDispatcher) {
            secretDao.purgeConfirmedTombstones(System.currentTimeMillis() - olderThanMillis)
        }

    override suspend fun wipeAllSyncState() = withContext(executorFactory.backgroundDispatcher) {
        db.withTransaction {
            syncDao.clearAllStagedBlobs()
            syncDao.clearAllParkedRemote()
            syncDao.clearAllItemState()
            secretDao.clearAllLastSyncedVersions()
        }
    }

    override suspend fun bumpAllVersionsForRekey() = withContext(executorFactory.backgroundDispatcher) {
        db.withTransaction {
            syncDao.clearAllStagedBlobs()
            secretDao.bumpAllVersionsForRekey(System.currentTimeMillis())
        }
    }

    override suspend fun backupSettings(): BackupSettings? = withContext(executorFactory.backgroundDispatcher) {
        val email = settingDao.getUserSettingOnce(UserSettingKey.BACKUP_ACCOUNT_EMAIL)?.value ?: return@withContext null
        val cipherHex = settingDao.getUserSettingOnce(UserSettingKey.BACKUP_CODE_CIPHERTEXT)?.value
        val ivHex = settingDao.getUserSettingOnce(UserSettingKey.BACKUP_CODE_IV)?.value
        BackupSettings(
            accountEmail = email,
            keyEpoch = settingDao.getUserSettingOnce(UserSettingKey.BACKUP_KEY_EPOCH)?.value,
            lastSyncAt = settingDao.getUserSettingOnce(UserSettingKey.BACKUP_LAST_SYNC_AT)?.value?.toLongOrNull(),
            storedCode = if (cipherHex != null && ivHex != null) {
                StoredBackupCode(cipherHex.fromHex(), ivHex.fromHex())
            } else {
                null
            }
        )
    }

    override suspend fun saveBackupSettings(settings: BackupSettings) =
        withContext(executorFactory.backgroundDispatcher) {
            settingDao.insert(UserSetting(UserSettingKey.BACKUP_ACCOUNT_EMAIL, settings.accountEmail))
            settings.keyEpoch?.let { settingDao.insert(UserSetting(UserSettingKey.BACKUP_KEY_EPOCH, it)) }
            settings.lastSyncAt?.let {
                settingDao.insert(UserSetting(UserSettingKey.BACKUP_LAST_SYNC_AT, it.toString()))
            }
            val code = settings.storedCode
            if (code != null) {
                settingDao.insert(UserSetting(UserSettingKey.BACKUP_CODE_CIPHERTEXT, code.cipherText.toHex()))
                settingDao.insert(UserSetting(UserSettingKey.BACKUP_CODE_IV, code.iv.toHex()))
            } else {
                settingDao.delete(UserSettingKey.BACKUP_CODE_CIPHERTEXT)
                settingDao.delete(UserSettingKey.BACKUP_CODE_IV)
            }
            Unit
        }

    // Plain hex, not Base64 - see the identical note in VaultMetaStore.
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun String.fromHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    override suspend fun clearBackupSettings() = withContext(executorFactory.backgroundDispatcher) {
        settingDao.delete(UserSettingKey.BACKUP_ACCOUNT_EMAIL)
        settingDao.delete(UserSettingKey.BACKUP_KEY_EPOCH)
        settingDao.delete(UserSettingKey.BACKUP_LAST_SYNC_AT)
        settingDao.delete(UserSettingKey.BACKUP_CODE_CIPHERTEXT)
        settingDao.delete(UserSettingKey.BACKUP_CODE_IV)
    }

    private fun syncedState(
        secretId: String, remoteFileId: String?, remoteVersion: Long, remoteUpdatedAt: Long, remoteDeleted: Boolean
    ) = SyncItemState(
        secretId = secretId,
        remoteFileId = remoteFileId,
        remoteVersion = remoteVersion,
        remoteUpdatedAt = remoteUpdatedAt,
        remoteDeleted = remoteDeleted,
        status = SyncItemStatus.SYNCED.name,
        failureKind = null,
        failureDetail = null,
        attemptCount = 0,
        lastAttemptAt = System.currentTimeMillis()
    )

    private fun SecretBackupBlob.toApi() = StagedBackupBlob(secretId, version, backupEncryptedData, backupIv, keyEpoch)

    private fun SyncIssueRow.toRecord() = SyncItemRecord(
        secretId = secretId,
        title = title,
        status = SyncItemStatus.valueOf(status),
        localVersion = localVersion,
        localUpdatedAt = localUpdatedAt,
        localDeleted = localDeleted,
        remoteVersion = remoteVersion,
        remoteUpdatedAt = remoteUpdatedAt,
        remoteDeleted = remoteDeleted,
        failureKind = failureKind?.let { SyncFailureKind.valueOf(it) },
        failureDetail = failureDetail,
        attemptCount = attemptCount,
        lastAttemptAt = lastAttemptAt
    )
}
