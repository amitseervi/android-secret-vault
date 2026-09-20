package com.rignis.backup.core.meta

import com.rignis.backup.core.crypto.BackupCipher
import com.rignis.backup.core.crypto.BackupKeyDeriver
import com.rignis.backup.core.crypto.DerivedKey
import com.rignis.backup.core.crypto.KdfParams
import com.rignis.backup.core.crypto.newBackupSalt
import com.rignis.backup.core.drive.DriveClient
import org.json.JSONObject
import java.util.UUID

class WrongBackupPasswordException : Exception("The backup password does not match this vault")
class VaultNotConfiguredException : Exception("No backup vault exists on Drive yet")

// keyEpoch travels with the derived key (not just inside VaultMeta) so a
// caller never needs a second Drive round-trip just to learn which
// "generation" of the password it now holds.
data class UnlockedVault(val derivedKey: DerivedKey, val keyEpoch: String)

// Manages the single _vault_meta.json file every vault has in appDataFolder:
// the Argon2 salt/cost params (public, not secret) plus a password verifier
// ciphertext, so a password can be validated - or a change-elsewhere
// detected via keyEpoch - before touching any actual secret's ciphertext.
class VaultMetaStore(
    private val driveClient: DriveClient,
    private val deriveKey: (password: CharArray, params: KdfParams) -> DerivedKey =
        { password, params -> BackupKeyDeriver().derive(password, params) }
) {
    suspend fun fetch(accessToken: String): Result<VaultMeta?> = runCatching {
        val file = driveClient.findFileByName(accessToken, VAULT_META_FILE_NAME).getOrThrow() ?: return@runCatching null
        val bytes = driveClient.downloadFile(accessToken, file.fileId).getOrThrow()
        parse(bytes)
    }

    // Used both to enable backup for the first time and to change the
    // password later - both cases mint a fresh salt and a fresh keyEpoch,
    // then overwrite whatever vault meta (if any) already exists on Drive.
    suspend fun setPassword(accessToken: String, password: CharArray): Result<UnlockedVault> = runCatching {
        val params = KdfParams(salt = newBackupSalt())
        val derived = deriveKey(password, params)
        val cipher = BackupCipher(BackupCipher.keyFrom(derived.keyBytes))
        val sealed = cipher.seal(VERIFIER_SECRET_ID, VERIFIER_VERSION, VERIFIER_PLAINTEXT)
        val epoch = UUID.randomUUID().toString()
        val meta = VaultMeta(
            kdfParams = derived.paramsUsed, keyEpoch = epoch, verifierIv = sealed.iv, verifierCipherText = sealed.cipherText
        )

        upload(accessToken, meta)
        UnlockedVault(derived, epoch)
    }

    suspend fun unlockWithPassword(accessToken: String, password: CharArray): Result<UnlockedVault> = runCatching {
        val meta = fetch(accessToken).getOrThrow() ?: throw VaultNotConfiguredException()
        val derived = deriveKey(password, meta.kdfParams)
        val cipher = BackupCipher(BackupCipher.keyFrom(derived.keyBytes))
        try {
            cipher.open(VERIFIER_SECRET_ID, VERIFIER_VERSION, meta.verifierIv, meta.verifierCipherText)
        } catch (e: Exception) {
            throw WrongBackupPasswordException()
        }
        UnlockedVault(derived, meta.keyEpoch)
    }

    private suspend fun upload(accessToken: String, meta: VaultMeta) {
        val bytes = serialize(meta)
        val existing = driveClient.findFileByName(accessToken, VAULT_META_FILE_NAME).getOrThrow()
        if (existing == null) {
            driveClient.createFile(accessToken, VAULT_META_FILE_NAME, emptyMap(), bytes).getOrThrow()
        } else {
            driveClient.patchFile(accessToken, existing.fileId, emptyMap(), bytes).getOrThrow()
        }
    }

    private fun serialize(meta: VaultMeta): ByteArray {
        val json = JSONObject().apply {
            put("salt", meta.kdfParams.salt.toHex())
            put("tCostIterations", meta.kdfParams.tCostIterations)
            put("mCostKib", meta.kdfParams.mCostKib)
            put("parallelism", meta.kdfParams.parallelism)
            put("keyLengthBytes", meta.kdfParams.keyLengthBytes)
            put("keyEpoch", meta.keyEpoch)
            put("verifierIv", meta.verifierIv.toHex())
            put("verifierCipherText", meta.verifierCipherText.toHex())
        }
        return json.toString().toByteArray()
    }

    private fun parse(bytes: ByteArray): VaultMeta {
        val json = JSONObject(String(bytes))
        val params = KdfParams(
            salt = json.getString("salt").fromHex(),
            tCostIterations = json.getInt("tCostIterations"),
            mCostKib = json.getInt("mCostKib"),
            parallelism = json.getInt("parallelism"),
            keyLengthBytes = json.getInt("keyLengthBytes")
        )
        return VaultMeta(
            kdfParams = params,
            keyEpoch = json.getString("keyEpoch"),
            verifierIv = json.getString("verifierIv").fromHex(),
            verifierCipherText = json.getString("verifierCipherText").fromHex()
        )
    }

    // Plain hex, not Base64: java.util.Base64 needs API 26 (minSdk here is
    // 24) and android.util.Base64 is a framework stub in plain JVM unit
    // tests - hex sidesteps both for these small blobs.
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun String.fromHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        const val VAULT_META_FILE_NAME = "_vault_meta.json"
        private const val VERIFIER_SECRET_ID = "__vault_meta_verifier__"
        private const val VERIFIER_VERSION = 1L
        private val VERIFIER_PLAINTEXT = "rignis-backup-vault-v1".toByteArray()
    }
}
