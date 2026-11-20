plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

val upstream = file("../UPSTREAM_VERSION").readText().trim()
val appVersion = System.getenv("APP_VERSION") ?: "0.2.1"
val appCode = (System.getenv("APP_VERSION_CODE") ?: "1").toInt()

android {
    namespace = "app.tgwsproxy"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "app.tgwsproxy"
        minSdk = 24
        targetSdk = 34
        versionCode = appCode
        versionName = appVersion
        buildConfigField("String", "UPSTREAM", "\"$upstream\"")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_FILE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null)
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            install("httpx==0.28.1")
            install("h2")
            install("certifi")
            install("cryptography")
        }
    }
}
