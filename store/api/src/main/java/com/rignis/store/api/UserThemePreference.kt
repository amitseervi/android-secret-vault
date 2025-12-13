package com.rignis.store.api

import androidx.annotation.StringRes

enum class UserThemePreference(@param:StringRes val label: Int) {
    DARK(R.string.dark), LIGHT(R.string.light), SYSTEM(R.string.system_default)
}