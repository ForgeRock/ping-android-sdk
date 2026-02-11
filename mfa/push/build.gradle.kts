/*
 * Copyright (c) 2025-2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "PUSH library"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.mfa.push"

    buildTypes {
        debug {
            enableAndroidTestCoverage = true
            enableUnitTestCoverage = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Add JVM arguments to exclude Android framework classes from instrumentation
                // This applies whether running via Gradle or Android Studio's coverage runner
                it.jvmArgs(
                    "-noverify",
                    "-XX:+IgnoreUnrecognizedVMOptions"
                )
            }
        }
    }
}

dependencies {
    api(project(":mfa:commons"))
    api(project(":foundation:storage"))
    implementation(project(":foundation:logger"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:utils"))
    implementation(project(":foundation:network"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Base64 implementation
    implementation(libs.commons.codec)

    // SQLite implementation
    implementation(libs.androidx.sqlite)

    // SQL Cipher for encrypted database
    implementation(libs.sqlcipher)

    // Firebase Cloud Messaging for push notifications
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Testing dependencies
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(project(":foundation:storage"))
}
