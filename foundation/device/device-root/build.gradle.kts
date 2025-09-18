/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "Device Root Detection library"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.device.root"
}

dependencies {
    implementation(project(":foundation:device:device-id"))
    implementation(project(":foundation:journey-plugin"))
    implementation(project(":foundation:utils"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:logger"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
}