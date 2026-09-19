package com.rignis.store.core.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// Ciphertext here is already encrypted with the backup password's derived key
// (never the Keystore key) - safe to persist without additional gating. One
// row per secret; replaced wholesale whenever the secret is staged again.
@Entity(tableName = "secret_backup_blob")
data class SecretBackupBlob(
    @PrimaryKey @ColumnInfo("secret_id") val secretId: String,
    @ColumnInfo("version") val version: Long,
    @ColumnInfo("bed") val backupEncryptedData: ByteArray,
    @ColumnInfo("biv") val backupIv: ByteArray,
    @ColumnInfo("key_epoch") val keyEpoch: String,
    @ColumnInfo("staged_at") val stagedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SecretBackupBlob

        if (secretId != other.secretId) return false
        if (version != other.version) return false
        if (!backupEncryptedData.contentEquals(other.backupEncryptedData)) return false
        if (!backupIv.contentEquals(other.backupIv)) return false
        if (keyEpoch != other.keyEpoch) return false
        if (stagedAt != other.stagedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = secretId.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + backupEncryptedData.contentHashCode()
        result = 31 * result + backupIv.contentHashCode()
        result = 31 * result + keyEpoch.hashCode()
        result = 31 * result + stagedAt.hashCode()
        return result
    }

    override fun toString(): String {
        return "SecretBackupBlob(secretId='$secretId', version=$version)"
    }
}
