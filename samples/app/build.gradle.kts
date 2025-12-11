/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.pingidentity.samples.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pingidentity.samples.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["appRedirectUriScheme"] = "com.pingidentity.demo"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
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

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.9"
    }
}

dependencies {

    implementation(project(":foundation:android"))
    implementation(project(":foundation:device:device-id"))
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(project(":davinci"))
    //implementation("com.pingidentity.sdks:davinci:0.9.2-SNAPSHOT")

    // Social Login
    implementation(project(":external-idp"))

    //To enable Native Google Sign-In, fall back to browser if Google SDK is not available.

    implementation(libs.facebook.login)

    // FIDO2
    //To Support nod-discoverable fido2 credential
    implementation(libs.play.services.fido)
    implementation(libs.play.services.auth)
    implementation(project(":mfa:fido"))

    //Protect
    implementation(project(":protect"))

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)

    // Android Studio Preview support
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.core.splashscreen)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
