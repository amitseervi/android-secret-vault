package com.rignis.backup.core.code

import com.rignis.auth.domain.CipherManager
import com.rignis.store.api.StoredBackupCode
import com.rignis.store.api.SyncDataStore
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

class NoStoredBackupCodeException : Exception("No backup key is stored on this device")

// Keystore-encrypts the backup key (the same primitive already used for a
// secret's own body) so routine syncs need one biometric prompt instead of
// the user retyping a long code every time. This is never the Argon2-derived
// encryption key itself - that still never persists (see BackupKeyHolder).
class BackupCodeStore(private val syncDataStore: SyncDataStore) {

    suspend fun store(cipherManager: CipherManager, code: CharArray) {
        val bytes = code.toUtf8Bytes()
        try {
            val encrypted = cipherManager.encryptData(bytes)
            val settings = syncDataStore.backupSettings() ?: return
            syncDataStore.saveBackupSettings(
                settings.copy(storedCode = StoredBackupCode(encrypted.encryptedBody, encrypted.iv))
            )
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun retrieve(cipherManager: CipherManager): Result<CharArray> = runCatching {
        val stored = syncDataStore.backupSettings()?.storedCode ?: throw NoStoredBackupCodeException()
        val bytes = cipherManager.decryptData(stored.cipherText, stored.iv).getOrThrow()
        try {
            bytes.toCharArrayUtf8()
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun clear() {
        val settings = syncDataStore.backupSettings() ?: return
        syncDataStore.saveBackupSettings(settings.copy(storedCode = null))
    }

    private fun CharArray.toUtf8Bytes(): ByteArray {
        val buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(this))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    private fun ByteArray.toCharArrayUtf8(): CharArray {
        val buffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(this))
        val chars = CharArray(buffer.remaining())
        buffer.get(chars)
        return chars
    }
}
