package com.rignis.backup.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupEnvelopeTest {

    @Test
    fun packThenUnpack_roundTripsIvAndCipherText() {
        val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val cipherText = "not-really-ciphertext-but-arbitrary-bytes".toByteArray()

        val envelope = BackupEnvelope.pack(iv, cipherText)
        val unpacked = BackupEnvelope.unpack(envelope)

        assertArrayEquals(iv, unpacked.iv)
        assertArrayEquals(cipherText, unpacked.cipherTextWithTag)
    }

    @Test
    fun unpack_rejectsBadMagic() {
        val envelope = BackupEnvelope.pack(ByteArray(12), byteArrayOf(1, 2, 3))
        envelope[0] = envelope[0].inc()

        assertThrows(IllegalArgumentException::class.java) {
            BackupEnvelope.unpack(envelope)
        }
    }

    @Test
    fun unpack_rejectsEnvelopeTruncatedWithinTheIv() {
        val envelope = BackupEnvelope.pack(ByteArray(12), byteArrayOf(1, 2, 3, 4, 5))

        // 6-byte header + a 12-byte iv means anything shorter than 18 bytes
        // has been cut short inside the iv itself.
        assertThrows(IllegalArgumentException::class.java) {
            BackupEnvelope.unpack(envelope.copyOf(10))
        }
    }

    @Test
    fun unpack_rejectsTooShortInput() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupEnvelope.unpack(byteArrayOf(1, 2, 3))
        }
    }
}
