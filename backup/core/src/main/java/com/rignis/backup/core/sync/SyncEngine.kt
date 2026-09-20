package com.rignis.backup.core.sync

import com.rignis.auth.domain.CipherManager
import com.rignis.backup.api.RemoteSecretMeta
import com.rignis.backup.core.crypto.BackupCipher
import com.rignis.backup.core.crypto.BackupEnvelope
import com.rignis.backup.core.crypto.BackupKeyHolder
import com.rignis.backup.core.crypto.SecretPayload
import com.rignis.backup.core.drive.DriveClient
import com.rignis.backup.core.drive.DriveException
import com.rignis.backup.core.drive.DriveFileMeta
import com.rignis.store.api.EncryptedDataEntry
import com.rignis.store.api.ParkedRemoteSecret
import com.rignis.store.api.SecretSyncRef
import com.rignis.store.api.StagedBackupBlob
import com.rignis.store.api.SyncDataStore
import com.rignis.store.api.SyncFailureKind
import javax.crypto.AEADBadTagException

data class SyncOutcome(
    val pushed: Int, val pulled: Int, val conflicts: Int, val failed: Int, val skippedNotStaged: Int
)

// The per-secret version-comparison pass. Assumes the backup key is already
// unlocked (BackupManagerImpl's job to check) and that pending tombstones
// were already staged (also BackupManagerImpl, via BackupStager) - this
// class only ever reads staged blobs, never derives or holds key material.
class SyncEngine(
    private val driveClient: DriveClient, private val syncDataStore: SyncDataStore, private val keyHolder: BackupKeyHolder
) {
    suspend fun run(accessToken: String, cipherManager: CipherManager): SyncOutcome {
        val cipher = keyHolder.cipherOrNull() ?: return SyncOutcome(0, 0, 0, 0, 0)

        val remoteFiles = driveClient.listAppDataFiles(accessToken).getOrElse {
            return SyncOutcome(0, 0, 0, 1, 0)
        }
        val remoteBySecretId = remoteFiles.mapNotNull { it.toRemoteMeta() }.associateBy { it.secretId }
        val localRefs = syncDataStore.localSyncRefs().associateBy { it.id }

        var pushed = 0
        var pulled = 0
        var conflicts = 0
        var failed = 0
        var skippedNotStaged = 0

        for (id in localRefs.keys + remoteBySecretId.keys) {
            val local = localRefs[id]
            val remote = remoteBySecretId[id]
            try {
                when {
                    local == null && remote != null -> {
                        if (!remote.deleted && pull(accessToken, id, remote, cipher, cipherManager)) pulled++
                    }

                    local != null && remote == null -> when (push(accessToken, local, null)) {
                        PushResult.PUSHED -> pushed++
                        PushResult.NOT_STAGED -> skippedNotStaged++
                    }

                    local != null && remote != null -> {
                        val localChanged = local.lastSyncedVersion != local.version
                        val remoteChanged = local.lastSyncedVersion != remote.version
                        when {
                            !localChanged && !remoteChanged -> Unit
                            localChanged && !remoteChanged -> when (push(accessToken, local, remote.fileId)) {
                                PushResult.PUSHED -> pushed++
                                PushResult.NOT_STAGED -> skippedNotStaged++
                            }

                            !localChanged && remoteChanged -> {
                                if (pull(accessToken, id, remote, cipher, cipherManager)) pulled++
                            }

                            local.version == remote.version -> syncDataStore.markSynced(id, local.version, remote.fileId)
                            else -> {
                                park(accessToken, local, remote, cipher, cipherManager)
                                conflicts++
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                syncDataStore.recordFailure(id, e.toFailureKind(), e.driveDetailOrNull())
                failed++
            }
        }
        return SyncOutcome(pushed, pulled, conflicts, failed, skippedNotStaged)
    }

    private enum class PushResult { PUSHED, NOT_STAGED }

    private suspend fun push(accessToken: String, local: SecretSyncRef, existingFileId: String?): PushResult {
        val blob = syncDataStore.stagedBlob(local.id)
        if (blob == null || blob.version != local.version) {
            syncDataStore.recordNotStaged(local.id)
            return PushResult.NOT_STAGED
        }
        val content = BackupEnvelope.pack(blob.iv, blob.cipherText)
        val appProperties = mapOf(
            "secretId" to local.id,
            "version" to local.version.toString(),
            "deleted" to local.isDeleted.toString(),
            "updatedAt" to local.updatedAt.toString()
        )
        val fileMeta = if (existingFileId != null) {
            driveClient.patchFile(accessToken, existingFileId, appProperties, content).getOrThrow()
        } else {
            driveClient.createFile(accessToken, "${local.id}.bin", appProperties, content).getOrThrow()
        }
        syncDataStore.markSynced(local.id, local.version, fileMeta.fileId)
        return PushResult.PUSHED
    }

    private suspend fun pull(
        accessToken: String, secretId: String, remote: RemoteSecretMeta, cipher: BackupCipher, cipherManager: CipherManager
    ): Boolean {
        if (remote.deleted) {
            syncDataStore.applyRemoteTombstone(secretId, remote.version, remote.updatedAt)
            return true
        }
        val bytes = driveClient.downloadFile(accessToken, remote.fileId).getOrThrow()
        val unpacked = BackupEnvelope.unpack(bytes)
        val plaintext = cipher.open(secretId, remote.version, unpacked.iv, unpacked.cipherTextWithTag)
        val payload = SecretPayload.unpack(plaintext)
        val encrypted = cipherManager.encryptData(payload.body)
        val ref = SecretSyncRef(secretId, payload.title, remote.version, remote.updatedAt, null, remote.version)
        val entry = EncryptedDataEntry(payload.title, encrypted.encryptedBody, encrypted.iv)
        val stagedBlob = StagedBackupBlob(secretId, remote.version, unpacked.cipherTextWithTag, unpacked.iv, keyHolder.keyEpoch!!)
        syncDataStore.applyRemoteSecret(ref, entry, stagedBlob, remote.fileId)
        return true
    }

    private suspend fun park(
        accessToken: String, local: SecretSyncRef, remote: RemoteSecretMeta, cipher: BackupCipher, cipherManager: CipherManager
    ) {
        if (remote.deleted) {
            // A tombstone carries no content to park - the placeholders here
            // are what let resolveIssue(ACCEPT_REMOTE) use one uniform code
            // path regardless of whether the conflicting remote side is a
            // real edit or a deletion.
            syncDataStore.parkRemote(
                ParkedRemoteSecret(
                    local.id, remote.version, remote.updatedAt, true, remote.fileId,
                    "", ByteArray(0), ByteArray(0), ByteArray(0), ByteArray(0), System.currentTimeMillis()
                )
            )
            syncDataStore.recordConflict(local.id, remote.version, remote.updatedAt, true, remote.fileId)
            return
        }
        val bytes = driveClient.downloadFile(accessToken, remote.fileId).getOrThrow()
        val unpacked = BackupEnvelope.unpack(bytes)
        val plaintext = cipher.open(local.id, remote.version, unpacked.iv, unpacked.cipherTextWithTag)
        val payload = SecretPayload.unpack(plaintext)
        val encrypted = cipherManager.encryptData(payload.body)
        syncDataStore.parkRemote(
            ParkedRemoteSecret(
                local.id, remote.version, remote.updatedAt, false, remote.fileId, payload.title,
                encrypted.encryptedBody, encrypted.iv, unpacked.cipherTextWithTag, unpacked.iv,
                System.currentTimeMillis()
            )
        )
        syncDataStore.recordConflict(local.id, remote.version, remote.updatedAt, false, remote.fileId)
    }

    private fun DriveFileMeta.toRemoteMeta(): RemoteSecretMeta? {
        val secretId = appProperties["secretId"] ?: return null
        val version = appProperties["version"]?.toLongOrNull() ?: return null
        val deleted = appProperties["deleted"]?.toBoolean() ?: false
        val updatedAt = appProperties["updatedAt"]?.toLongOrNull() ?: modifiedAtEpochMillis
        return RemoteSecretMeta(secretId, fileId, version, updatedAt, deleted, modifiedAtEpochMillis)
    }

    // failureDetail must only ever be an HTTP status / provider error code -
    // never a message derived from plaintext, so this never touches e.message.
    private fun Throwable.toFailureKind(): SyncFailureKind = when (this) {
        is DriveException.NetworkFailure -> SyncFailureKind.NETWORK
        is DriveException.AuthRequired -> SyncFailureKind.AUTH
        is DriveException.RateLimited -> SyncFailureKind.QUOTA
        is DriveException.ServerError, is DriveException.UnexpectedResponse, is DriveException.NotFound ->
            SyncFailureKind.SERVER

        is AEADBadTagException, is IllegalArgumentException -> SyncFailureKind.UNREADABLE_REMOTE
        else -> SyncFailureKind.UNKNOWN
    }

    private fun Throwable.driveDetailOrNull(): String? = when (this) {
        is DriveException.AuthRequired -> "HTTP $httpCode"
        is DriveException.RateLimited -> "HTTP $httpCode"
        is DriveException.ServerError -> "HTTP $httpCode"
        is DriveException.UnexpectedResponse -> "HTTP $httpCode"
        is DriveException.NotFound -> "not_found"
        is AEADBadTagException, is IllegalArgumentException -> "undecryptable"
        else -> this::class.simpleName
    }
}
