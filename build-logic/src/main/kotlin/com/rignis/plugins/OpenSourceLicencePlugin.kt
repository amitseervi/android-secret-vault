package com.rignis.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

class OpenSourceLicencePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        try {
            val data = project.configurations.filter { it.isCanBeResolved }
            println("************* OpenSourceLicencePlugin ==> ${data.size}")
        } catch (e: Exception) {
            println("**********ERRROR***********")
        }
    }
}