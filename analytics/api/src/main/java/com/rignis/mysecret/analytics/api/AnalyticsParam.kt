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
}
