package com.rignis.store.core.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// A conflicting remote version, already downloaded, decrypted with the backup
// key, and re-encrypted with the on-device Keystore key (ed/iv here are
// Keystore-protected, same as secret_data) - so "Pull from cloud" on the Sync
// Issues screen is an instant, offline, biometric-free copy into secret_data.
@Entity(tableName = "secret_remote_staged")
data class SecretRemoteStaged(
    @PrimaryKey @ColumnInfo("secret_id") val secretId: String,
    @ColumnInfo("remote_version") val remoteVersion: Long,
    @ColumnInfo("remote_updated_at") val remoteUpdatedAt: Long,
    @ColumnInfo("remote_deleted") val remoteDeleted: Boolean,
    @ColumnInfo("remote_file_id") val remoteFileId: String,
    @ColumnInfo("title") val title: String,
    @ColumnInfo("ed") val encryptedData: ByteArray,
    @ColumnInfo("iv") val initializationVector: ByteArray,
    @ColumnInfo("fetched_at") val fetchedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SecretRemoteStaged

        if (secretId != other.secretId) return false
        if (remoteVersion != other.remoteVersion) return false
        if (remoteUpdatedAt != other.remoteUpdatedAt) return false
        if (remoteDeleted != other.remoteDeleted) return false
        if (remoteFileId != other.remoteFileId) return false
        if (title != other.title) return false
        if (!encryptedData.contentEquals(other.encryptedData)) return false
        if (!initializationVector.contentEquals(other.initializationVector)) return false
        if (fetchedAt != other.fetchedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = secretId.hashCode()
        result = 31 * result + remoteVersion.hashCode()
        result = 31 * result + remoteUpdatedAt.hashCode()
        result = 31 * result + remoteDeleted.hashCode()
        result = 31 * result + remoteFileId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + encryptedData.contentHashCode()
        result = 31 * result + initializationVector.contentHashCode()
        result = 31 * result + fetchedAt.hashCode()
        return result
    }

    override fun toString(): String {
        return "SecretRemoteStaged(secretId='$secretId', remoteVersion=$remoteVersion)"
    }
}
