package com.rignis.settings.api

import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val themePreference: StateFlow<ThemePreference>
}