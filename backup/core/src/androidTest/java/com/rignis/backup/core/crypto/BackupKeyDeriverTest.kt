package com.rignis.backup.core.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

// argon2kt is JNI-backed and cannot load its native library in a plain JVM
// unit test - this needs a real device/emulator.
@RunWith(AndroidJUnit4::class)
class BackupKeyDeriverTest {

    // Small params so this test doesn't take forever on every run - the
    // real default (KdfParams()) is exercised implicitly wherever a test
    // omits these overrides.
    private val fastParams = KdfParams(
        salt = ByteArray(16) { it.toByte() }, tCostIterations = 2, mCostKib = 8_192, parallelism = 1
    )

    @Test
    fun derive_isDeterministicForSamePasswordSaltAndParams() {
        val deriver = BackupKeyDeriver()
        val password = "correct horse battery staple".toCharArray()

        val first = deriver.derive(password.copyOf(), fastParams)
        val second = deriver.derive(password.copyOf(), fastParams)

        assertArrayEquals(first.keyBytes, second.keyBytes)
        assertEquals(fastParams.keyLengthBytes, first.keyBytes.size)
    }

    @Test
    fun derive_producesDifferentKeysForDifferentPasswords() {
        val deriver = BackupKeyDeriver()

        val a = deriver.derive("password A".toCharArray(), fastParams)
        val b = deriver.derive("password B".toCharArray(), fastParams)

        assertFalse(a.keyBytes.contentEquals(b.keyBytes))
    }

    @Test
    fun derive_producesDifferentKeysForDifferentSalts() {
        val deriver = BackupKeyDeriver()
        val password = "same password".toCharArray()

        val a = deriver.derive(password.copyOf(), fastParams)
        val b = deriver.derive(password.copyOf(), fastParams.copy(salt = deriver.newSalt()))

        assertFalse(a.keyBytes.contentEquals(b.keyBytes))
    }

    @Test
    fun newSalt_producesDifferentValuesEachTime() {
        val deriver = BackupKeyDeriver()

        val a = deriver.newSalt()
        val b = deriver.newSalt()

        assertFalse(a.contentEquals(b))
    }

    @Test
    fun derivedKey_isUsableWithBackupCipher() {
        val deriver = BackupKeyDeriver()
        val derived = deriver.derive("hunter2-master-password".toCharArray(), fastParams)
        val cipher = BackupCipher(BackupCipher.keyFrom(derived.keyBytes))

        val sealed = cipher.seal("secret-1", 1L, "plaintext".toByteArray())
        val opened = cipher.open("secret-1", 1L, sealed.iv, sealed.cipherText)

        assertArrayEquals("plaintext".toByteArray(), opened)
    }
}
