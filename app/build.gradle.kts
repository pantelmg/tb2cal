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
        // versionCode = Minutes since 1 Jan 2024 (UTC): Every newer build, on GitHub or on a PC, is higher,
        // so it always installs over the old one.
        versionCode = (System.currentTimeMillis() / 60_000L - 28_401_120L).toInt()
        val run = System.getenv("GITHUB_RUN_NUMBER")
        versionName = if (run != null) "1.0.$run" else "1.0.pc"
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
