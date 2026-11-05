plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.hima"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.hima.filter"
        minSdk = 26
        targetSdk = 35
        val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = run
        versionName = "0.1-b$run"
    }

    signingConfigs {
        create("hima") {
            storeFile = file("hima.keystore")
            storePassword = System.getenv("HIMA_KEYSTORE_PASS")
            keyAlias = "hima"
            keyPassword = System.getenv("HIMA_KEYSTORE_PASS")
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("hima")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
