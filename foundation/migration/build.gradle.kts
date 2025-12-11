/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "Migration library"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.pingidentity.migration"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":foundation:logger"))


    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
}