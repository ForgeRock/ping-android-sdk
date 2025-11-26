/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "Device Binding"

plugins {
    id("com.pingidentity.convention.android.library")
    // id("com.pingidentity.convention.centralPublish") // we are not publishing this module for now
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.device.binding"
}

dependencies {

    implementation(project(":foundation:device:device-id"))
    implementation(project(":foundation:utils"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:storage"))
    implementation(project(":foundation:journey-plugin"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.androidx.startup.runtime)
    // For Pin Collect and User Key Select
    compileOnly(project(":mfa:binding-ui"))

    // Biometric
    implementation(libs.androidx.biometric.ktx)
    implementation(libs.androidx.appcompat)

    //Application Pin
    compileOnly(libs.bcpkix.jdk18on)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.core.ktx)
    testImplementation(project(":mfa:binding-ui"))
    testImplementation(libs.bcpkix.jdk18on)

    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}