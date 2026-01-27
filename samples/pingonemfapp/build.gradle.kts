import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.kotlinSerialization)
}
@Suppress("UnstableApiUsage")
android {
    namespace = "com.pingidentity.pingonemfapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pingidentity.pingonemfapp"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    lint {
        disable += "NullSafeMutableLiveData"
        // To avoid lint errors during the build
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        // Matching the Compose plugin version
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("com.google.android.gms:play-services-basement:18.4.0")
        force("com.google.android.gms:play-services-tasks:18.2.0")
        force("com.google.android.gms:play-services-base:18.5.0")
    }
}

dependencies {

    // Ping SDK dependencies
    implementation(project(":pingonemfa"))
    implementation(project(":foundation:logger"))

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.extended)

    // CameraX dependencies for QR scanning
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.barcode.scanning)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Firebase Cloud Messaging for push notifications
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Biometric
    implementation(libs.androidx.biometric)
}
