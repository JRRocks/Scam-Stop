import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.scamstop.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scamstop.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "2.1.0"

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("personalRelease") {
                storeFile = rootProject.file(requireNotNull(keystoreProperties["storeFile"]))
                storePassword = requireNotNull(keystoreProperties["storePassword"]).toString()
                keyAlias = requireNotNull(keystoreProperties["keyAlias"]).toString()
                keyPassword = requireNotNull(keystoreProperties["keyPassword"]).toString()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("personalRelease")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric reads the manifest and the resources of the app under test.
        unitTests.isIncludeAndroidResources = true
    }
}

// Android Studio's bundled JDK is 21; without this Kotlin targets 21 while Java targets 17.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")

    // Android-backed tests on the JVM (Robolectric): the screening service, the store, and the
    // screen captures. A block of its own: the voice workstream may add the same lines, keep
    // one copy when merging.
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
