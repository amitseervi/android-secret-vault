package com.rignis.mysecret.analytics.api

// Every event here must stay free of secret titles, secret bodies, or any
// value derived from them (including counts, lengths, or error messages
// built from that content) - only structural/UI facts belong in analytics.
sealed interface AnalyticsEvent {
    val name: String

    object ScreenView : AnalyticsEvent {
        override val name: String = "screen_view"
    }

    object ScreenExit : AnalyticsEvent {
        override val name: String = "screen_exit"
    }

    object SecretCreated : AnalyticsEvent {
        override val name: String = "secret_created"
    }

    object SecretUpdated : AnalyticsEvent {
        override val name: String = "secret_updated"
    }

    object SecretDeleted : AnalyticsEvent {
        override val name: String = "secret_deleted"
    }

    object SecretUnlocked : AnalyticsEvent {
        override val name: String = "secret_unlocked"
    }

    object SecretCopied : AnalyticsEvent {
        override val name: String = "secret_copied"
    }

    object ThemeChanged : AnalyticsEvent {
        override val name: String = "theme_changed"
    }

    object BackupAccountLinked : AnalyticsEvent {
        override val name: String = "backup_account_linked"
    }

    object BackupEnabled : AnalyticsEvent {
        override val name: String = "backup_enabled"
    }

    object BackupSyncCompleted : AnalyticsEvent {
        override val name: String = "backup_sync_completed"
    }

    object BackupConflictResolved : AnalyticsEvent {
        override val name: String = "backup_conflict_resolved"
    }

    object BackupDisabled : AnalyticsEvent {
        override val name: String = "backup_disabled"
    }
}
