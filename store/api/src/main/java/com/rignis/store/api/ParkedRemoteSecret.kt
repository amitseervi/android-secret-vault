package com.rignis.store.api

// A conflicting remote version, already downloaded, decrypted with the
// backup key, and re-encrypted with the on-device Keystore key (encryptedBody/iv
// here are Keystore-protected, same as a normal secret) - so accepting it is
// an instant, offline, biometric-free copy into local storage.
data class ParkedRemoteSecret(
    val secretId: String,
    val remoteVersion: Long,
    val remoteUpdatedAt: Long,
    val remoteDeleted: Boolean,
    val remoteFileId: String,
    val title: String,
    val encryptedBody: ByteArray,
    val iv: ByteArray,
    val fetchedAt: Long
)
