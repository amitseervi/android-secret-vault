package com.rignis.mysecret.analytics.api


sealed interface AnalyticsEvent {
    val name: String

    object ScreenView : AnalyticsEvent {
        override val name: String = "screen_view"
    }

    object ScreenExit : AnalyticsEvent {
        override val name: String = "screen_exit"
    }
}