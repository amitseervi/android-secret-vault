package com.rignis.store.api

// The backup key, Keystore-encrypted (same primitive as a secret's own
// body) so it can be retrieved with a single biometric prompt instead of
// the user retyping it every sync. Only ever leaves this device manually
// (the user transcribing it for a second device) - Keystore keys are
// hardware-bound and never travel on their own.
data class StoredBackupCode(val cipherText: ByteArray, val iv: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StoredBackupCode

        if (!cipherText.contentEquals(other.cipherText)) return false
        if (!iv.contentEquals(other.iv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cipherText.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}

// Everything about the backup link that must survive a process restart -
// unlike the Argon2-derived encryption key itself, which is deliberately
// never persisted. keyEpoch is whatever this device last confirmed
// unlocking with, so a mismatch against Drive's vault meta means the
// backup key changed elsewhere.
data class BackupSettings(
    val accountEmail: String,
    val keyEpoch: String?,
    val lastSyncAt: Long?,
    val storedCode: StoredBackupCode?
)
