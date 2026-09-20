package com.rignis.store.core.local

// Projection joining sync_item_state with its secret_data row, so the issues
// list can show a title/local version without a separate lookup per row.
// title/localVersion/localUpdatedAt are null if the local secret is gone
// (e.g. a tombstone already purged) - the issue itself still needs surfacing.
data class SyncIssueRow(
    val secretId: String,
    val title: String?,
    val localVersion: Long?,
    val localUpdatedAt: Long?,
    val localDeleted: Boolean,
    val remoteFileId: String?,
    val remoteVersion: Long?,
    val remoteUpdatedAt: Long?,
    val remoteDeleted: Boolean,
    val status: String,
    val failureKind: String?,
    val failureDetail: String?,
    val attemptCount: Int,
    val lastAttemptAt: Long
)
