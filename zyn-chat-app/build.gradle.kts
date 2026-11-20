import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// --- Release signing -----------------------------------------------------------------------
// Credentials are resolved, in priority order, from:
//   1. environment variables — used by CI, where the keystore is restored from an encrypted
//      repository secret before Gradle starts;
//   2. a local signing.properties file in the repository root (gitignored).
// When neither is available the release variant falls back to the debug signing config, so a
// fresh clone still assembles without any signing material.
val signingProperties: Properties = Properties().apply {
    val file = rootProject.file("signing.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val envOrProperty: (String, String) -> String? = { envName, propertyName ->
    providers.environmentVariable(envName).orNull ?: signingProperties.getProperty(propertyName)
}

val releaseKeystoreFile: File? = envOrProperty("ANDROID_KEYSTORE_PATH", "storeFile")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }
val hasReleaseSigning = releaseKeystoreFile != null

android {
    namespace = "com.zyn.chat.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.zyn.chat"
        // The on-device model needs Android 8.0.
        minSdk = 26
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = envOrProperty("ANDROID_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = envOrProperty("ANDROID_KEY_ALIAS", "keyAlias")
                keyPassword = envOrProperty("ANDROID_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Debug key fallback keeps local builds working without a release keystore.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // One APK per ABI plus a universal APK: CI builds the variants and uploads every split
    // as its own artifact, so a single ABI can be downloaded and installed on its own.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":zyn-chat-compose"))
    implementation(project(":zyn-chat-ondevice"))

    implementation(libs.bundles.stream.chat)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.androidx.compose)

    implementation(libs.bundles.retrofit)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
