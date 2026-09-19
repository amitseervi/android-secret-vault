package com.rignis.backup.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS = 128
private const val IV_LENGTH_BYTES = 12

// AES-256-GCM under the Argon2-derived backup key - fully independent of the
// on-device Keystore key (auth:data's CipherManagerImpl), since Keystore
// keys are hardware-bound and can never leave the device.
//
// The AAD binds each ciphertext to "secretId|version": this stops a
// corrupted or malicious Drive write from grafting one secret's ciphertext
// onto another id, or replaying an old version under a new version number -
// the GCM tag check fails instead of silently decrypting the wrong thing.
class BackupCipher(private val key: SecretKeySpec) {

    data class Sealed(val iv: ByteArray, val cipherText: ByteArray)

    fun seal(secretId: String, version: Long, plaintext: ByteArray): Sealed {
        val iv = ByteArray(IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad(secretId, version))
        return Sealed(iv, cipher.doFinal(plaintext))
    }

    fun open(secretId: String, version: Long, iv: ByteArray, cipherText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad(secretId, version))
        return cipher.doFinal(cipherText)
    }

    private fun aad(secretId: String, version: Long): ByteArray = "$secretId|$version".toByteArray()

    companion object {
        fun keyFrom(rawKey: ByteArray): SecretKeySpec = SecretKeySpec(rawKey, "AES")
    }
}
