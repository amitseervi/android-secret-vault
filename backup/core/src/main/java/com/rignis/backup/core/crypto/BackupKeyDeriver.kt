package com.rignis.backup.core.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.nio.CharBuffer
import java.security.SecureRandom

private const val SALT_LENGTH_BYTES = 16
private const val LOW_MEMORY_M_COST_KIB = 32_768

// Argon2id parameters used to derive the backup key from the user's backup
// password. These (including the salt) are not secret and must be recorded
// alongside the vault (see the future VaultMetaStore in Phase 4) - every
// device restoring this vault has to derive with identical params.
data class KdfParams(
    val salt: ByteArray,
    val tCostIterations: Int = 3,
    val mCostKib: Int = 65_536,
    val parallelism: Int = 2,
    val keyLengthBytes: Int = 32
)

data class DerivedKey(val keyBytes: ByteArray, val paramsUsed: KdfParams)

// Free-standing so callers (e.g. VaultMetaStore) can generate a salt without
// constructing a BackupKeyDeriver - and without pulling in argon2kt's JNI
// shim - since salt generation has nothing to do with Argon2 itself.
fun newBackupSalt(): ByteArray = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }

class BackupKeyDeriver(private val argon2Kt: Argon2Kt = Argon2Kt()) {

    fun newSalt(): ByteArray = newBackupSalt()

    // Tries [params] first and falls back to a lower memory cost on OOM
    // (constrained devices). The caller must persist whichever KdfParams
    // actually succeeded, since it becomes part of what every device needs
    // to derive the same key.
    fun derive(password: CharArray, params: KdfParams): DerivedKey {
        return try {
            DerivedKey(deriveRaw(password, params), params)
        } catch (e: OutOfMemoryError) {
            val fallback = params.copy(mCostKib = LOW_MEMORY_M_COST_KIB)
            DerivedKey(deriveRaw(password, fallback), fallback)
        }
    }

    private fun deriveRaw(password: CharArray, params: KdfParams): ByteArray {
        val passwordBytes = utf8BytesOf(password)
        try {
            val result = argon2Kt.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = passwordBytes,
                salt = params.salt,
                tCostInIterations = params.tCostIterations,
                mCostInKibibyte = params.mCostKib,
                parallelism = params.parallelism,
                hashLengthInBytes = params.keyLengthBytes
            )
            return result.rawHashAsByteArray()
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun utf8BytesOf(chars: CharArray): ByteArray {
        val byteBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        return bytes
    }
}
