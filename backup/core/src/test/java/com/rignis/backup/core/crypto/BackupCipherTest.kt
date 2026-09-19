package com.rignis.backup.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

// Plain JVM tests - AES/GCM here is standard javax.crypto, no Android/JNI
// involved, so this runs in milliseconds without a device.
class BackupCipherTest {

    private fun randomKeyCipher(): BackupCipher {
        val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return BackupCipher(BackupCipher.keyFrom(rawKey))
    }

    @Test
    fun sealThenOpen_roundTripsPlaintext() {
        val cipher = randomKeyCipher()
        val plaintext = "hunter2".toByteArray()

        val sealed = cipher.seal("secret-1", 3L, plaintext)
        val opened = cipher.open("secret-1", 3L, sealed.iv, sealed.cipherText)

        assertArrayEquals(plaintext, opened)
    }

    @Test
    fun seal_producesUniqueIvAndCiphertextEachTime() {
        val cipher = randomKeyCipher()
        val plaintext = "same secret".toByteArray()

        val first = cipher.seal("secret-1", 1L, plaintext)
        val second = cipher.seal("secret-1", 1L, plaintext)

        assertFalse("IVs must never repeat", first.iv.contentEquals(second.iv))
        assertFalse(
            "ciphertext must differ when the IV differs",
            first.cipherText.contentEquals(second.cipherText)
        )
    }

    @Test
    fun open_failsOnTamperedCiphertext() {
        val cipher = randomKeyCipher()
        val sealed = cipher.seal("secret-1", 1L, "hunter2".toByteArray())
        val tampered = sealed.cipherText.copyOf()
        tampered[0] = (tampered[0] + 1).toByte()

        assertThrows(AEADBadTagException::class.java) {
            cipher.open("secret-1", 1L, sealed.iv, tampered)
        }
    }

    @Test
    fun open_failsWhenSecretIdAadDoesNotMatch() {
        val cipher = randomKeyCipher()
        val sealed = cipher.seal("secret-1", 1L, "hunter2".toByteArray())

        assertThrows(AEADBadTagException::class.java) {
            cipher.open("secret-2", 1L, sealed.iv, sealed.cipherText)
        }
    }

    @Test
    fun open_failsWhenVersionAadDoesNotMatch() {
        val cipher = randomKeyCipher()
        val sealed = cipher.seal("secret-1", 1L, "hunter2".toByteArray())

        assertThrows(AEADBadTagException::class.java) {
            cipher.open("secret-1", 2L, sealed.iv, sealed.cipherText)
        }
    }

    @Test
    fun open_failsWithWrongKey() {
        val a = randomKeyCipher()
        val b = randomKeyCipher()
        val sealed = a.seal("secret-1", 1L, "hunter2".toByteArray())

        assertThrows(AEADBadTagException::class.java) {
            b.open("secret-1", 1L, sealed.iv, sealed.cipherText)
        }
    }

    @Test
    fun open_failsWithWrongIv() {
        val cipher = randomKeyCipher()
        val a = cipher.seal("secret-1", 1L, "secret A".toByteArray())
        val b = cipher.seal("secret-1", 1L, "secret B".toByteArray())

        assertThrows(AEADBadTagException::class.java) {
            cipher.open("secret-1", 1L, b.iv, a.cipherText)
        }
    }
}
