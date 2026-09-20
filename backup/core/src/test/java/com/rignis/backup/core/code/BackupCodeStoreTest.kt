package com.rignis.backup.core.code

import com.rignis.auth.domain.CanAuthenticate
import com.rignis.auth.domain.CipherManager
import com.rignis.auth.domain.EncryptedData
import com.rignis.backup.core.testing.FakeSyncDataStore
import com.rignis.store.api.BackupSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// A reversible stand-in for the real Keystore cipher: XORs with a fixed
// pad, so encrypt/decrypt genuinely round-trip without touching Android's
// BiometricPrompt/Keystore (which can't run in a plain JVM unit test).
// canFail lets a test simulate a cancelled biometric prompt.
private class FakeCipherManager : CipherManager {
    var canFail = false
    private val pad = byteArrayOf(0x5A)

    override suspend fun encryptData(data: ByteArray): EncryptedData =
        EncryptedData(data.map { (it.toInt() xor pad[0].toInt()).toByte() }.toByteArray(), byteArrayOf(1))

    override suspend fun decryptData(body: ByteArray, iv: ByteArray): Result<ByteArray> {
        if (canFail) return Result.failure(IllegalStateException("biometric cancelled"))
        return Result.success(body.map { (it.toInt() xor pad[0].toInt()).toByte() }.toByteArray())
    }

    override suspend fun canAuthenticate(): CanAuthenticate = CanAuthenticate.YES
}

class BackupCodeStoreTest {

    private lateinit var syncDataStore: FakeSyncDataStore
    private lateinit var cipherManager: FakeCipherManager
    private lateinit var codeStore: BackupCodeStore

    @Before
    fun setUp() {
        syncDataStore = FakeSyncDataStore()
        cipherManager = FakeCipherManager()
        codeStore = BackupCodeStore(syncDataStore)
        syncDataStore.backupSettingsValue = BackupSettings("user@example.com", null, null, null)
    }

    @Test
    fun retrieve_noStoredCode_failsWithNoStoredBackupCodeException() = runTest {
        val error = codeStore.retrieve(cipherManager).exceptionOrNull()

        assertTrue(error is NoStoredBackupCodeException)
    }

    @Test
    fun storeThenRetrieve_roundTripsTheExactCode() = runTest {
        val code = "ABCD-EFGH-JKMN-PQRS".toCharArray()

        codeStore.store(cipherManager, code)
        val retrieved = codeStore.retrieve(cipherManager).getOrThrow()

        assertEquals(String(code), String(retrieved))
    }

    @Test
    fun retrieve_whenCipherManagerFails_propagatesFailure() = runTest {
        codeStore.store(cipherManager, "ABCD-EFGH-JKMN-PQRS".toCharArray())
        cipherManager.canFail = true

        val error = codeStore.retrieve(cipherManager).exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun clear_removesStoredCodeButKeepsRestOfSettings() = runTest {
        codeStore.store(cipherManager, "ABCD-EFGH-JKMN-PQRS".toCharArray())

        codeStore.clear()

        assertNull(syncDataStore.backupSettingsValue?.storedCode)
        assertEquals("user@example.com", syncDataStore.backupSettingsValue?.accountEmail)
    }

    @Test
    fun store_calledTwice_overwritesRatherThanKeepingBoth() = runTest {
        codeStore.store(cipherManager, "AAAA-AAAA-AAAA-AAAA".toCharArray())
        codeStore.store(cipherManager, "BBBB-BBBB-BBBB-BBBB".toCharArray())

        val retrieved = codeStore.retrieve(cipherManager).getOrThrow()

        assertEquals("BBBB-BBBB-BBBB-BBBB", String(retrieved))
    }
}
