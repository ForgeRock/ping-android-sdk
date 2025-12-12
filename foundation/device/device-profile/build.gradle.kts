/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "Device Profile library"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.device.profile"
}

dependencies {

    implementation(project(":foundation:device:device-id"))
    implementation(project(":foundation:device:device-root"))
    implementation(project(":foundation:journey-plugin"))
    implementation(project(":foundation:utils"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:logger"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.startup.runtime)

    // Make it optional for developer
    compileOnly(libs.play.services.location)
    compileOnly(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.core.ktx)
    testImplementation(libs.mockk)
    testImplementation(libs.play.services.location)
    testImplementation(libs.kotlinx.coroutines.play.services)
}