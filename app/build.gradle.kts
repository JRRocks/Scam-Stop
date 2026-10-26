import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
        versionCode = 1
        versionName = "1.0.0"

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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Android Studio's bundled JDK is 21; without this Kotlin targets 21 while Java targets 17.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
