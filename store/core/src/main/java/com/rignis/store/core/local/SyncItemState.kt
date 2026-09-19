package com.rignis.store.core.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Per-secret sync outcome. `status`/`failureKind` are stored as plain strings
// (the name of an enum defined in :backup:api) so this table has no
// compile-time dependency on the backup module.
// failureDetail must only ever be an HTTP status / Drive error reason - never
// a message derived from plaintext secret content.
@Entity(tableName = "sync_item_state", indices = [Index(value = ["status"])])
data class SyncItemState(
    @PrimaryKey @ColumnInfo("secret_id") val secretId: String,
    @ColumnInfo("remote_file_id") val remoteFileId: String?,
    @ColumnInfo("remote_version") val remoteVersion: Long?,
    @ColumnInfo("remote_updated_at") val remoteUpdatedAt: Long?,
    @ColumnInfo(name = "remote_deleted", defaultValue = "0") val remoteDeleted: Boolean = false,
    @ColumnInfo("status") val status: String,
    @ColumnInfo("failure_kind") val failureKind: String? = null,
    @ColumnInfo("failure_detail") val failureDetail: String? = null,
    @ColumnInfo(name = "attempt_count", defaultValue = "0") val attemptCount: Int = 0,
    @ColumnInfo(name = "last_attempt_at", defaultValue = "0") val lastAttemptAt: Long = 0L
)
