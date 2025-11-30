package com.rignis.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

class RignisCommonAndroidPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val libs: VersionCatalog =
            target.extensions.getByType<VersionCatalogsExtension>().named("libs")
        target.dependencies.addImplementation(libs.logging)
    }
}