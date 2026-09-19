package com.rignis.store.core.local

// Plain projection (not a Room @Entity) used for the sync version-comparison
// pass - no encrypted body/iv, just enough to compute local-vs-remote state.
data class SecretSyncRefRow(
    val id: String,
    val title: String,
    val version: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val lastSyncedVersion: Long?
)
