package com.rignis.store.core.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "secret_data", indices = [Index(value = ["deleted_at"])])
data class SecretData(
    @PrimaryKey @ColumnInfo("id") val id: String,
    @ColumnInfo("title") val title: String,
    @ColumnInfo("ed") val encryptedData: ByteArray,
    @ColumnInfo("iv") val initializationVector: ByteArray,
    @ColumnInfo(name = "version", defaultValue = "1") val version: Long = 1L,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0L,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "last_synced_version") val lastSyncedVersion: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SecretData

        if (id != other.id) return false
        if (title != other.title) return false
        if (!encryptedData.contentEquals(other.encryptedData)) return false
        if (!initializationVector.contentEquals(other.initializationVector)) return false
        if (version != other.version) return false
        if (updatedAt != other.updatedAt) return false
        if (deletedAt != other.deletedAt) return false
        if (lastSyncedVersion != other.lastSyncedVersion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + encryptedData.contentHashCode()
        result = 31 * result + initializationVector.contentHashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + (deletedAt?.hashCode() ?: 0)
        result = 31 * result + (lastSyncedVersion?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "SecretDataEntity(id='$id', title='$title', version=$version)"
    }

}