package com.rignis.backup.core.drive

// Metadata-only view of a file in the app's hidden appDataFolder - never
// carries file content. appProperties is how callers stash small key/value
// data (e.g. secretId/version/deleted) that's queryable without a download.
data class DriveFileMeta(
    val fileId: String,
    val name: String,
    val appProperties: Map<String, String>,
    val modifiedAtEpochMillis: Long
)
