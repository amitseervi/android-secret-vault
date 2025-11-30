package com.rignis.store.core.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "secret_data")
data class SecretData(
    @PrimaryKey @ColumnInfo("id") val id: String,
    @ColumnInfo("title") val title: String,
    @ColumnInfo("ed") val encryptedData: ByteArray,
    @ColumnInfo("iv") val initializationVector: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SecretData

        if (id != other.id) return false
        if (title != other.title) return false
        if (!encryptedData.contentEquals(other.encryptedData)) return false
        if (!initializationVector.contentEquals(other.initializationVector)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + encryptedData.contentHashCode()
        result = 31 * result + initializationVector.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "SecretDataEntity(id='$id', title='$title')"
    }

}