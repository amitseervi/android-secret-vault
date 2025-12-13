package com.rignis.mysecrets.di

import com.rignis.mysecret.analytics.api.Analytics
import com.rignis.mysecret.analytics.core.FirebaseAnalyticsImpl
import org.koin.dsl.bind
import org.koin.dsl.module

val analyticsModule = module {
    single {
        FirebaseAnalyticsImpl()
    } bind Analytics::class
}