/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
description = "MFA Commons library"

plugins {
    id("com.pingidentity.convention.android.library")
    // id("com.pingidentity.convention.centralPublish") // we are not publishing this module for now
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.mfa.commons"
    
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":foundation:logger"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:storage"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.sqlite)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // Biometric authentication support
    implementation(libs.androidx.biometric)

    // JWT Generation and Validation
    implementation(libs.nimbus.jose.jwt)

    // HTTP Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.cio)

    // SQL Cipher for encrypted database
    implementation(libs.sqlcipher)

    // Testing dependencies
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.core.ktx)
}