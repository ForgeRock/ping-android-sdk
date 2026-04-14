/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "Device Binding Migration"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.device.binding.migration"

    packaging {
        resources.excludes += setOf(
            "META-INF/LICENSE.md",
            "META-INF/LICENSE-notice.md",
        )
    }
}

dependencies {
    implementation(project(":foundation:android"))
    implementation(project(":foundation:logger"))
    implementation(project(":foundation:migration"))
    implementation(project(":foundation:storage"))
    implementation(project(":mfa:binding"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.core.ktx)
    implementation(libs.androidx.startup.runtime)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)

    androidTestImplementation(project(":foundation:journey-plugin"))
    androidTestImplementation(libs.mockk)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.forgerock.auth)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)

}