import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// GOOGLE_SERVER_CLIENT_ID is the Web OAuth client ID from Google Cloud
// Console (needed for Credential Manager sign-in). Kept out of git the same
// way app/google-services.json is: read from local.properties, never
// hardcoded. Left blank until that console setup is done - sign-in simply
// fails at runtime with a clear error instead of failing the build.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        FileInputStream(file).use { load(it) }
    }
}
val googleServerClientId: String = localProperties.getProperty("GOOGLE_SERVER_CLIENT_ID").orEmpty()

android {
    namespace = "com.rignis.backup.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(project(":backup:api"))
    implementation(project(":store:api"))
    implementation(project(":auth:domain"))
    implementation(project(":common"))

    implementation(libs.argon2kt)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    implementation(libs.okhttp)

    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // android.jar stubs org.json for plain JVM unit tests (throws "Stub!");
    // this is the real implementation, same API, so DriveClientImpl/
    // VaultMetaStore need no test-only code path.
    testImplementation("org.json:json:20260814")
}
