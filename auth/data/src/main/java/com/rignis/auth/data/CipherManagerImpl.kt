package com.rignis.auth.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.rignis.auth.domain.CanAuthenticate
import com.rignis.auth.domain.CipherManager
import com.rignis.auth.domain.EncryptedData
import com.rignis.common.ExecutorFactory
import com.rignis.common.callbackToSuspend
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec


class CipherManagerImpl(
    private val executorFactory: ExecutorFactory, private val activity: FragmentActivity
) : CipherManager {
    companion object {

        private const val KEY_STORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "rignis_secure_key"
        private const val CIPHER_TRANSFORMATION =
            KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE

        private fun getSecretKey(): SecretKey? {
            val keyStore = KeyStore.getInstance(KEY_STORE_PROVIDER)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                return null
            }
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }

        private fun generateKey() {
            val existingKey = getSecretKey()
            if (existingKey != null) {
                return
            }

            val keyGen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KEY_STORE_PROVIDER
            )
            val specBuilder = KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setUserAuthenticationRequired(false)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                specBuilder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                specBuilder.setUserAuthenticationValidityDurationSeconds(0)
            }

            keyGen.init(specBuilder.build())
            keyGen.generateKey()
        }
    }

    init {
        generateKey()
    }

    override suspend fun encryptData(data: ByteArray): EncryptedData {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(data)

        return EncryptedData(cipherBytes, iv)

    }

    override suspend fun decryptData(
        body: ByteArray,
        iv: ByteArray,
    ): Result<ByteArray> {
        return callbackToSuspend { onSuccess, onError ->
            decryptDataInternal(body, iv, { decrypted ->
                onSuccess(Result.success(decrypted))
            }, { exception ->
                onError(exception)
            })
        }
    }

    private fun decryptDataInternal(
        body: ByteArray, iv: ByteArray, onSuccess: (ByteArray) -> Unit, onError: (Throwable) -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            executorFactory.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val cipher = result.cryptoObject?.cipher
                    if (cipher == null) {
                        onError(RuntimeException("Unknown exception - 01"))
                        return
                    }
                    try {
                        onSuccess(cipher.doFinal(body))
                    } catch (e: Exception) {
                        onError(e)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(RuntimeException("$errorCode - $errString"))
                }
            })

        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Secure Login")
            .setAllowedAuthenticators(BIOMETRIC_STRONG).setNegativeButtonText("Negative")
            .setDescription("Authenticate to continue").build()

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    override suspend fun canAuthenticate(): CanAuthenticate {
        val biometricManager = BiometricManager.from(activity)
        return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> CanAuthenticate.HW_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> CanAuthenticate.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> CanAuthenticate.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> CanAuthenticate.UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> CanAuthenticate.NOT_SUPPORTED
            BiometricManager.BIOMETRIC_SUCCESS -> CanAuthenticate.YES
            else -> CanAuthenticate.UNKNOWN
        }
    }
}