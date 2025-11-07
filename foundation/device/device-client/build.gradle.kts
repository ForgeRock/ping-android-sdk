/*
 * Copyright (c) 2025 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "Device Client"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.jacoco")
    id("com.pingidentity.convention.centralPublish")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.device.client"
}

dependencies {
    implementation(project(":foundation:utils"))
    implementation(project(":foundation:logger"))
    implementation(project(":foundation:network"))

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)
}