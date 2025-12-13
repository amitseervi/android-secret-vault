package com.rignis.core.ui.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.rignis.mysecret.analytics.api.Analytics
import com.rignis.mysecret.analytics.api.AnalyticsEvent
import com.rignis.mysecret.analytics.api.AnalyticsParam

@Composable
fun TrackScreen(analytics: Analytics, screenName: String) {
    DisposableEffect(Unit) {
        analytics.logEvent(AnalyticsEvent.ScreenView) {
            param(AnalyticsParam.ScreenName, screenName)
        }
        onDispose {
            analytics.logEvent(AnalyticsEvent.ScreenExit) {
                param(AnalyticsParam.ScreenName, screenName)
            }
        }
    }
}