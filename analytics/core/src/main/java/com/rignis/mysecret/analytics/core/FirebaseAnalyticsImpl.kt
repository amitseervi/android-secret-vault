package com.rignis.mysecret.analytics.core

import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.rignis.mysecret.analytics.api.Analytics
import com.rignis.mysecret.analytics.api.AnalyticsEvent
import com.rignis.mysecret.analytics.api.ParamBuilder

class FirebaseAnalyticsImpl(private val analytics: FirebaseAnalytics = Firebase.analytics) :
    Analytics {
    override fun logEvent(
        event: AnalyticsEvent, builder: ParamBuilder.() -> Unit
    ) {
        val paramBuilder = ParamBuilder()
        paramBuilder.builder()
        analytics.logEvent(event.name, paramBuilder.build())
    }
}