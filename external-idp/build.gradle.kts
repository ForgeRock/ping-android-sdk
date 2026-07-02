/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "External IDP library"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.pingidentity.idp"
}

dependencies {
    api(project(":foundation:utils"))
    api(project(":foundation:browser"))
    implementation(project(":foundation:logger"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:network"))
    implementation(project(":foundation:davinci-plugin"))
    implementation(project(":foundation:journey-plugin"))
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.kotlinx.coroutines.core)

    //Make it optional for developer
    compileOnly(libs.googleid)
    compileOnly(libs.facebook.login)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)

    testImplementation(libs.googleid)
}
