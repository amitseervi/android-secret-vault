package com.rignis.backup.api

// Metadata-only view of a secret's Drive file, read from appProperties
// without downloading file content.
data class RemoteSecretMeta(
    val secretId: String,
    val fileId: String,
    val version: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    val driveModifiedTime: Long
)
