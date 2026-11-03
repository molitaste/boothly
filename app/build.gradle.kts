plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.boothly.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.boothly.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Fixed key so every new build installs as an UPDATE (your saved data stays).
    signingConfigs {
        create("boothly") {
            storeFile = file("boothly.jks")
            storePassword = "boothly2026"
            keyAlias = "boothly"
            keyPassword = "boothly2026"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("boothly")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("boothly")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Offline OCR: Chinese + English (bundled model, works without Google Play)
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    // QR codes printed on cards (vCard / WeChat / WhatsApp links)
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    // Smart card scanner with auto edge detection (needs Google Play services, falls back to camera)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    // Offline OCR: Arabic
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.7.0")
}
