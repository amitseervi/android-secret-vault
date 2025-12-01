plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("rignisCommonAndroidPlugin") {
            id = "rignis.common.android"
            implementationClass = "com.rignis.plugins.RignisCommonAndroidPlugin"
        }
        create("OpenSourceLicencePlugin") {
            id = "rignis.licences"
            implementationClass = "com.rignis.plugins.OpenSourceLicencePlugin"
        }
    }
}

repositories {
    google()
    mavenCentral()
}
