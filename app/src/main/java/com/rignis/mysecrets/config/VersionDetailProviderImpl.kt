package com.rignis.mysecrets.config

import com.rignis.core.ui.routes.about.VersionDetailProvider
import com.rignis.mysecrets.BuildConfig

class VersionDetailProviderImpl : VersionDetailProvider {
    override val versionName: String
        get() = BuildConfig.VERSION_NAME
}