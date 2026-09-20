package com.rignis.backup.core.stage

import com.rignis.backup.api.BackupStager
import com.rignis.backup.core.crypto.BackupKeyHolder
import com.rignis.backup.core.crypto.SecretPayload
import com.rignis.store.api.StagedBackupBlob
import com.rignis.store.api.SyncDataStore
import kotlinx.coroutines.flow.StateFlow

class BackupStagerImpl(
    private val keyHolder: BackupKeyHolder, private val syncDataStore: SyncDataStore
) : BackupStager {

    override val isKeyAvailable: StateFlow<Boolean> get() = keyHolder.available

    override suspend fun stageFor(
        secretId: String, version: Long, title: String, plaintextBody: ByteArray
    ): StagedBackupBlob? {
        val cipher = keyHolder.cipherOrNull() ?: return null
        val epoch = keyHolder.keyEpoch ?: return null
        val sealed = cipher.seal(secretId, version, SecretPayload.pack(title, plaintextBody))
        val blob = StagedBackupBlob(secretId, version, sealed.cipherText, sealed.iv, epoch)
        syncDataStore.stageBlob(blob)
        return blob
    }

    override suspend fun restageOpportunistically(secretId: String, title: String, plaintextBody: ByteArray) {
        val ref = syncDataStore.syncRef(secretId) ?: return
        if (ref.isDeleted) return
        val already = syncDataStore.stagedBlob(secretId)
        if (already != null && already.version == ref.version) return
        stageFor(secretId, ref.version, title, plaintextBody)
    }

    override suspend fun stageAllPendingTombstones() {
        if (!keyHolder.available.value) return
        for (secretId in syncDataStore.idsNeedingTombstoneStaging()) {
            val ref = syncDataStore.syncRef(secretId) ?: continue
            // No real content to protect once deleted - title/body are both
            // empty, so this never needs a biometric prompt.
            stageFor(secretId, ref.version, title = "", plaintextBody = ByteArray(0))
        }
    }
}
