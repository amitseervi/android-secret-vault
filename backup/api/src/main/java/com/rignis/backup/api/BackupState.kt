package com.rignis.backup.api

sealed interface BackupState {
    data object NotConfigured : BackupState

    // Account linked, but the Argon2-derived key isn't in memory this
    // session (app restart, or explicitly locked).
    data class NeedsPassword(val accountEmail: String) : BackupState

    data class Ready(val accountEmail: String, val lastSyncAt: Long) : BackupState

    // The Drive authorization was revoked (e.g. in the user's Google
    // account settings) and needs to be granted again.
    data class NeedsReauth(val accountEmail: String) : BackupState

    // The vault's key epoch on Drive no longer matches the key held in
    // memory - the backup password was changed from another device.
    data class PasswordChangedElsewhere(val accountEmail: String) : BackupState
}
