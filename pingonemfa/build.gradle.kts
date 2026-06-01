/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
description = "Ping Identity PingOneMFA SDK for Android"

plugins {
    id("com.pingidentity.convention.android.library")
    id("kotlin-parcelize")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}
android {
    namespace = "com.pingidentity.pingonemfa"
}
dependencies {
    implementation(project(":foundation:android"))
    implementation(project(":foundation:logger"))
    implementation(libs.com.pingidentity.pingonemfa)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.google.gson.lib)
    implementation(libs.kotlinx.serialization.json)

    // Firebase Cloud Messaging for push notifications
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
