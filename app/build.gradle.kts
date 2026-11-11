plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pantelmg.tb2cal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pantelmg.tb2cal"
        minSdk = 26
        targetSdk = 35
        // Each GitHub Actions run gets a higher versionCode, so new APKs install over old ones.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "1.0.$run"
    }

    // Fixed key, so every build has the same signature and updates install in place.
    // It is NOT stored in the repo: GitHub Actions writes it from the KEYSTORE_BASE64 secret.
    // Without it (e.g. a local build), the debug key is used instead.
    val keystore = file("signing/tb2cal.jks")
    signingConfigs {
        create("tb2cal") {
            storeFile = keystore
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "tb2cal"
            keyAlias = "tb2cal"
            keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: "tb2cal"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystore.exists()) "tb2cal" else "debug")
        }
        debug {
            if (keystore.exists()) signingConfig = signingConfigs.getByName("tb2cal")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
}
