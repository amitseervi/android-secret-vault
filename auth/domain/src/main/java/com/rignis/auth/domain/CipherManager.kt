package com.rignis.auth.domain

class EncryptedData(val encryptedBody: ByteArray, val iv: ByteArray)

enum class CanAuthenticate {
    YES, NO_HARDWARE, HW_UNAVAILABLE, NOT_ENROLLED, NOT_SUPPORTED, UPDATE_REQUIRED, UNKNOWN
}

interface CipherManager {

    suspend fun encryptData(data: ByteArray): EncryptedData
    suspend fun decryptData(
        body: ByteArray, iv: ByteArray
    ): Result<ByteArray>

    suspend fun canAuthenticate(): CanAuthenticate
}