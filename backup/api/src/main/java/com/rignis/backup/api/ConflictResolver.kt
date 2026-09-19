package com.rignis.backup.api

import com.rignis.store.api.SecretSyncRef

enum class ConflictResolution {
    ACCEPT_LOCAL, ACCEPT_REMOTE, DEFER_TO_USER
}

fun interface ConflictResolver {
    fun resolveConflict(local: SecretSyncRef, remote: RemoteSecretMeta): ConflictResolution
}

// The default the automatic sync pass uses: a true conflict (both sides
// changed since the last confirmed sync) is never auto-resolved - it's
// parked and surfaced on the Sync Issues screen for the user to decide.
object DeferToUserConflictResolver : ConflictResolver {
    override fun resolveConflict(local: SecretSyncRef, remote: RemoteSecretMeta) =
        ConflictResolution.DEFER_TO_USER
}

object AcceptLocalConflictResolver : ConflictResolver {
    override fun resolveConflict(local: SecretSyncRef, remote: RemoteSecretMeta) =
        ConflictResolution.ACCEPT_LOCAL
}

object AcceptRemoteConflictResolver : ConflictResolver {
    override fun resolveConflict(local: SecretSyncRef, remote: RemoteSecretMeta) =
        ConflictResolution.ACCEPT_REMOTE
}
