package com.rignis.store.core

import com.rignis.store.api.SettingsRepository
import com.rignis.store.api.UserThemePreference
import com.rignis.store.core.local.UserSetting
import com.rignis.store.core.local.UserSettingKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(private val databaseFactory: DataStoreFactory) : SettingsRepository {
    override val userPreferredTheme: Flow<UserThemePreference>
        get() = databaseFactory.db.userSettingDao().getUserSetting(UserSettingKey.PREFERRED_THEME)
            .map { it?.let { UserThemePreference.valueOf(it.value) } ?: UserThemePreference.SYSTEM }

    override fun updateTheme(themePref: UserThemePreference) {
        databaseFactory.db.userSettingDao()
            .insert(UserSetting(UserSettingKey.PREFERRED_THEME, themePref.name))
    }
}