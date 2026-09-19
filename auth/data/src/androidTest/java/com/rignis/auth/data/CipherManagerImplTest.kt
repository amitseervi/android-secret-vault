package com.rignis.auth.data

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rignis.common.ExecutorFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor

private class ImmediateExecutorFactory : ExecutorFactory {
    override val mainExecutor = Executor { it.run() }
    override val backgroundExecutor = Executor { it.run() }
    override val mainDispatcher = Dispatchers.Unconfined
    override val backgroundDispatcher = Dispatchers.Unconfined
}

// CipherManagerImpl's debug build path (BuildConfig.DEBUG) skips the
// Android Keystore biometric requirement entirely, so encrypt/decrypt can be
// exercised end-to-end here without a fingerprint - this is the primary
// "test the crypto API without a screenshot" harness for this module.
@RunWith(AndroidJUnit4::class)
class CipherManagerImplTest {

    private fun withCipherManager(block: suspend (CipherManagerImpl) -> Unit) {
        ActivityScenario.launch(TestFragmentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val cipherManager = CipherManagerImpl(ImmediateExecutorFactory(), activity)
                runBlocking { block(cipherManager) }
            }
        }
    }

    @Test
    fun encryptThenDecrypt_roundTripsOriginalData() = withCipherManager { cipherManager ->
        val plaintext = "hunter2".toByteArray()

        val encrypted = cipherManager.encryptData(plaintext)
        assertFalse(
            "ciphertext should not equal plaintext",
            encrypted.encryptedBody.contentEquals(plaintext)
        )

        val decrypted = cipherManager.decryptData(encrypted.encryptedBody, encrypted.iv)
        assertTrue(decrypted.isSuccess)
        assertArrayEquals(plaintext, decrypted.getOrThrow())
    }

    @Test
    fun encrypt_producesDifferentIvsAndCiphertextForSamePlaintext() = withCipherManager { cipherManager ->
        val plaintext = "same secret".toByteArray()

        val first = cipherManager.encryptData(plaintext)
        val second = cipherManager.encryptData(plaintext)

        assertFalse("IVs must never repeat", first.iv.contentEquals(second.iv))
        assertFalse(
            "ciphertext must differ when the IV differs",
            first.encryptedBody.contentEquals(second.encryptedBody)
        )
    }

    @Test
    fun decrypt_failsOnTamperedCiphertext() = withCipherManager { cipherManager ->
        val encrypted = cipherManager.encryptData("hunter2".toByteArray())
        val tampered = encrypted.encryptedBody.copyOf()
        tampered[0] = (tampered[0] + 1).toByte()

        val decrypted = cipherManager.decryptData(tampered, encrypted.iv)

        assertTrue("GCM tag check must reject tampered ciphertext", decrypted.isFailure)
    }

    @Test
    fun decrypt_failsOnWrongIv() = withCipherManager { cipherManager ->
        val a = cipherManager.encryptData("secret A".toByteArray())
        val b = cipherManager.encryptData("secret B".toByteArray())

        val decrypted = cipherManager.decryptData(a.encryptedBody, b.iv)

        assertTrue(decrypted.isFailure)
    }
}
