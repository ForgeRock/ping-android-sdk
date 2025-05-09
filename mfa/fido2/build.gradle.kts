/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

plugins {
    id("com.pingidentity.convention.android.library")
    //TODO uncomment when ready to publish
    //id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.fido2"
}

dependencies {
    implementation(project(":foundation:android"))
    implementation(project(":foundation:logger"))
    implementation(project(":foundation:davinci-plugin"))
    implementation(project(":foundation:journey-plugin"))
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.startup.runtime)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)


}
