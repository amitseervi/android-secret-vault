package com.rignis.mysecret.analytics.api

sealed interface AnalyticsParam {
    val name: String

    object ScreenName : AnalyticsParam {
        override val name: String = "app_screen_name"
    }
}