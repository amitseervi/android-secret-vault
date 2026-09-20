package com.rignis.store.api

// A conflicting remote version, already downloaded, decrypted with the
// backup key, and re-encrypted with the on-device Keystore key (encryptedBody/iv
// here are Keystore-protected, same as a normal secret) - so accepting it is
// an instant, offline, biometric-free copy into local storage. backupCipherText/
// backupIv are the original backup-encrypted bytes as downloaded, kept
// alongside so accepting this version can re-mark it "already backed up"
// without re-fetching from Drive. Both are empty when remoteDeleted (a
// tombstone carries no content to keep).
data class ParkedRemoteSecret(
    val secretId: String,
    val remoteVersion: Long,
    val remoteUpdatedAt: Long,
    val remoteDeleted: Boolean,
    val remoteFileId: String,
    val title: String,
    val encryptedBody: ByteArray,
    val iv: ByteArray,
    val backupCipherText: ByteArray,
    val backupIv: ByteArray,
    val fetchedAt: Long
)
