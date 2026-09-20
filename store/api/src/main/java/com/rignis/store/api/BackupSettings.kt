package com.rignis.store.api

// Everything about the backup link that must survive a process restart -
// unlike the derived key itself, which is deliberately never persisted.
// keyEpoch is whatever this device last confirmed unlocking with, so a
// mismatch against Drive's vault meta means the password changed elsewhere.
data class BackupSettings(
    val accountEmail: String,
    val keyEpoch: String?,
    val lastSyncAt: Long?
)
