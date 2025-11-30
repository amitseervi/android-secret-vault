package com.rignis.store.api

enum class ConflictResolution {
    ACCEPT_LOCAL, ACCEPT_REMOTE
}

interface ConflictResolver {
    fun resolveConflict(local: EncryptedDataRef, remote: EncryptedDataRef): ConflictResolution
}