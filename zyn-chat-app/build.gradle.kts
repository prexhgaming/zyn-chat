import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
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

    // A single universal APK is built on purpose: the app carries almost no native code, so the
    // per-ABI splits only added release and download overhead without a meaningful size win.
    // Re-introduce `splits { abi { ... } }` here if a large native dependency is ever added.

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        // Robolectric and Paparazzi need the merged resources inside JVM unit tests.
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        // Carried over from the merged library modules.
        freeCompilerArgs.add("-progressive")
    }
}

// Paparazzi and Robolectric can't share a JVM: a Robolectric class that runs first breaks
// Paparazzi's setup for every class after it.
tasks.withType<Test>().configureEach {
    forkEvery = 1
}

dependencies {
    implementation(libs.bundles.stream.chat)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.androidx.compose)

    implementation(libs.bundles.retrofit)

    // Merged from the former zyn-chat-compose module.
    implementation(libs.androidx.exifinterface)
    implementation(libs.bundles.markdown.renderer)
    // Merged from the former zyn-chat-ondevice module.
    implementation(libs.mlkit.genai.prompt)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    // The JVM implementation of org.json, which android.jar only stubs in unit tests.
    testImplementation(libs.org.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
