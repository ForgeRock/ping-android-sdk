/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

plugins {
    id("com.pingidentity.convention.android.library")
//    id("com.pingidentity.convention.centralPublish") // we are not publishing this module for now
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
}

dependencies {
    api(project(":mfa:commons"))
    api(project(":foundation:storage"))
    implementation(project(":foundation:logger"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:utils"))

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

    // HTTP Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.cio)

    // Firebase Cloud Messaging for push notifications
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
