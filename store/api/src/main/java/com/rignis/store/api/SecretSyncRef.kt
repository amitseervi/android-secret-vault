package com.rignis.store.api

// Lightweight projection used for the sync version-comparison pass - no
// encrypted body/iv, just enough to compute local-vs-remote state and to
// display in a conflict/issue list (title is already stored in plaintext
// locally, so it carries no additional exposure here).
data class SecretSyncRef(
    val id: String,
    val title: String,
    val version: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val lastSyncedVersion: Long?
) {
    val isDeleted: Boolean get() = deletedAt != null
}
