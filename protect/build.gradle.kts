/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "Ping Identity Protect SDK for Android"

plugins {
    id("com.pingidentity.convention.android.library")
    //TODO re-enable when central publish is ready
    //id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.pingidentity.protect"
}

dependencies {
    api(project(":foundation:utils"))
    implementation(project(":foundation:logger"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:davinci-plugin"))
    implementation(project(":foundation:journey-plugin"))
    implementation(libs.com.pingidentity.signals)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.startup.runtime)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)

}
