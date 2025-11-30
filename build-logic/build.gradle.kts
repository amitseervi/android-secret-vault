plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("rignisCommonAndroidPlugin") {
            id = "rignis.common.android"
            implementationClass = "com.rignis.plugins.RignisCommonAndroidPlugin"
        }
    }
}

repositories {
    google()
    mavenCentral()
}
