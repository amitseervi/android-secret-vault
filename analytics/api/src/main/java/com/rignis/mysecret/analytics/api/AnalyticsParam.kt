package com.rignis.mysecret.analytics.api

// Never attach a secret's title, body, or anything derived from them to a
// param - these must only ever carry structural/UI facts (e.g. a theme
// name, a screen name).
sealed interface AnalyticsParam {
    val name: String

    object ScreenName : AnalyticsParam {
        override val name: String = "app_screen_name"
    }

    object ThemeValue : AnalyticsParam {
        override val name: String = "theme_value"
    }

    // Enum name only - trigger kind (APP_OPEN/USER_REQUESTED/...), never a
    // pushed/pulled/conflict count, since those are derived from vault content.
    object SyncTriggerValue : AnalyticsParam {
        override val name: String = "sync_trigger"
    }

    // "success" | "partial" | "failed" - never the underlying counts.
    object SyncResultValue : AnalyticsParam {
        override val name: String = "sync_result"
    }

    // ACCEPT_LOCAL | ACCEPT_REMOTE - which side of a conflict the user chose.
    object ConflictResolutionValue : AnalyticsParam {
        override val name: String = "conflict_resolution"
    }
}
