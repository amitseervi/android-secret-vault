package com.rignis.backup.api

enum class SyncIssueKind {
    CONFLICT, UPLOAD_FAILED, DOWNLOAD_FAILED, NOT_STAGED, UNREADABLE_REMOTE, AUTH_REQUIRED
}

// UI-facing view of one secret's sync problem. title is plaintext-safe (it's
// already stored unencrypted locally) - the decrypted body must never be
// carried here.
data class SyncIssue(
    val secretId: String,
    val title: String?,
    val kind: SyncIssueKind,
    val localVersion: Long?,
    val localUpdatedAt: Long?,
    val localDeleted: Boolean,
    val remoteVersion: Long?,
    val remoteUpdatedAt: Long?,
    val remoteDeleted: Boolean,
    val canPullFromCloud: Boolean,
    val canOverrideCloud: Boolean,
    val retryable: Boolean,
    val lastAttemptAt: Long
)
