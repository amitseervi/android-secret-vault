package com.rignis.store.api

data class EncryptedDataItem(
    val id: String,
    val title: String,
    val encryptedBody: ByteArray,
    val initializationVector: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedDataItem

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "EncryptedDataItem(id='$id', title='$title')"
    }
}