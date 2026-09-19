package com.rignis.store.api

// Raw per-secret sync outcome as persisted locally. failureDetail must only
// ever be an HTTP status / provider error reason - never a message derived
// from plaintext secret content.
data class SyncItemRecord(
    val secretId: String,
    val title: String?,
    val status: SyncItemStatus,
    val localVersion: Long?,
    val localUpdatedAt: Long?,
    val localDeleted: Boolean,
    val remoteVersion: Long?,
    val remoteUpdatedAt: Long?,
    val remoteDeleted: Boolean,
    val failureKind: SyncFailureKind?,
    val failureDetail: String?,
    val attemptCount: Int,
    val lastAttemptAt: Long
)
