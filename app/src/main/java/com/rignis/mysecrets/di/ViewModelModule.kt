package com.rignis.mysecrets.di

import com.rignis.core.ui.viewmodels.detail.DetailViewModel
import com.rignis.core.ui.viewmodels.home.HomeViewModel
import com.rignis.core.ui.viewmodels.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::DetailViewModel)
    viewModelOf(::SettingsViewModel)
}