package com.rignis.store.api

// Ciphertext here is already encrypted with the backup password's derived
// key (never the on-device Keystore key) - safe to persist without
// additional gating. keyEpoch identifies which backup-password "generation"
// produced it, so stale blobs from a since-changed password can be detected
// and discarded.
class StagedBackupBlob(
    val secretId: String,
    val version: Long,
    val cipherText: ByteArray,
    val iv: ByteArray,
    val keyEpoch: String
) {
    override fun toString(): String = "StagedBackupBlob(secretId=$secretId, version=$version)"
}
