package com.rignis.plugins

import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.dsl.DependencyHandler

fun DependencyHandler.addImplementation(dependencyNotation: Any) {
    this.add("implementation", dependencyNotation)
}

val VersionCatalog.logging
    get() = this.findLibrary("timber-logging").get()