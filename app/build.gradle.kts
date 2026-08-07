plugins {
    id("com.android.application")
}

val releaseKeystoreFile = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull
    ?: "keystore/puzzmo-release.jks"
val releaseKeystorePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
    ?: "puzzmo2026"
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
    ?: "puzzmo"
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
    ?: "puzzmo2026"

android {
    namespace = "com.bradflaugher.puzzmo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bradflaugher.puzzmo"
        // Modern phones only — same floor as free-library-nyt (Android 16 / API 36).
        minSdk = 36
        targetSdk = 36
        versionCode = providers.environmentVariable("VERSION_CODE").orElse("1").get().toInt()
        versionName = providers.environmentVariable("VERSION_NAME").orElse("dev").get()
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(releaseKeystoreFile)
            storePassword = releaseKeystorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
