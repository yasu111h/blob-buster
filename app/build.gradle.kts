plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.teamhappslab.galaxyraid"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.teamhappslab.galaxyraid"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.2"
    }
    buildFeatures {
        // BuildConfig.DEBUG を参照するために必要（AGP 8以降はデフォルト無効）。
        // これで DEBUG_MODE をビルド種別に自動連動させ、リリース版でデバッグ機能を封じる。
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
