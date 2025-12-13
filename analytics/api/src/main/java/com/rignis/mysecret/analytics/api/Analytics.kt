package com.rignis.mysecret.analytics.api

import android.os.Bundle


class ParamBuilder() {
    private val bundle = Bundle()
    fun param(param: AnalyticsParam, value: String) {
        bundle.putString(param.name, value)
    }

    fun param(param: AnalyticsParam, value: Int) {
        bundle.putInt(param.name, value)
    }

    fun param(param: AnalyticsParam, value: Long) {
        bundle.putLong(param.name, value)
    }

    fun param(param: AnalyticsParam, value: Float) {
        bundle.putFloat(param.name, value)
    }

    fun param(param: AnalyticsParam, value: Double) {
        bundle.putDouble(param.name, value)
    }

    fun param(param: AnalyticsParam, value: Boolean) {
        bundle.putBoolean(param.name, value)
    }

    fun build(): Bundle = bundle
}

interface Analytics {
    fun logEvent(event: AnalyticsEvent, builder: ParamBuilder.() -> Unit)
}