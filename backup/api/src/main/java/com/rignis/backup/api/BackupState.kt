package com.rignis.backup.api

sealed interface BackupState {
    data object NotConfigured : BackupState

    // Account linked, but the Argon2-derived key isn't in memory this
    // session (app restart, or explicitly locked) - and either there's no
    // on-device stored code yet, or syncNow hasn't tried auto-unlocking yet.
    data class NeedsCode(val accountEmail: String) : BackupState

    data class Ready(val accountEmail: String, val lastSyncAt: Long) : BackupState

    // The Drive authorization was revoked (e.g. in the user's Google
    // account settings) and needs to be granted again.
    data class NeedsReauth(val accountEmail: String) : BackupState

    // The vault's key epoch on Drive no longer matches the key held in
    // memory - the backup key was changed from another device.
    data class PasswordChangedElsewhere(val accountEmail: String) : BackupState

    // syncNow's automatic unlock (using the on-device stored code) failed
    // verification against Drive's vault meta - either the stored code is
    // stale/wrong, or this Google account holds a different vault than the
    // one that code belongs to. Prompts the user to re-enter their backup
    // key or switch accounts, rather than silently retrying forever.
    data class CodeMismatch(val accountEmail: String) : BackupState
}
