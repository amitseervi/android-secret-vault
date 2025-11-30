package com.rignis.auth.domain

class EncryptedData(val encryptedBody: ByteArray, val iv: ByteArray)
interface CipherManager {

    suspend fun encryptData(data: ByteArray): EncryptedData
    suspend fun decryptData(
        body: ByteArray, iv: ByteArray
    ): Result<ByteArray>
}