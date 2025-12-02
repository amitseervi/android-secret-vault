package com.rignis.store.api

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val userPreferredTheme: Flow<UserThemePreference>

    suspend fun updateTheme(themePref: UserThemePreference)
}