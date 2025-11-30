package com.rignis.store.api

data class EncryptedDataEntry(
    val title: String, val encryptedBody: ByteArray, val initializationVector: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedDataEntry

        if (title != other.title) return false
        if (!encryptedBody.contentEquals(other.encryptedBody)) return false
        if (!initializationVector.contentEquals(other.initializationVector)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + encryptedBody.contentHashCode()
        result = 31 * result + initializationVector.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "EncryptedDataEntry(title='$title')"
    }


}