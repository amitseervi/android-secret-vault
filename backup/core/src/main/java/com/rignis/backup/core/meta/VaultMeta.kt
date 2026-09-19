package com.rignis.backup.core.meta

import com.rignis.backup.core.crypto.KdfParams

// Everything a brand-new device needs to validate a re-entered backup
// password and derive the same key, without ever touching real secret
// data. All fields here are public-safe: salt/KDF cost params are not
// secret, and the verifier only proves "this password is right" - it
// never reveals anything about vault content.
data class VaultMeta(
    val kdfParams: KdfParams,
    val keyEpoch: String,
    val verifierIv: ByteArray,
    val verifierCipherText: ByteArray
)
