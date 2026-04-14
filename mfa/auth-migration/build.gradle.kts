/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "Legacy Authentication Migration"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}
// Resolve duplicate .so files from multiple sources:
// 1. Local device-root NDK build (libtool-file.so)
// 2. Transitive forgerock-core-4.8.4-beta1 AAR from forgerock-auth dependency
// Pick the first occurrence when duplicates are found
androidComponents {
    onVariants { variant ->
        // Apply to androidTest variants
        variant.androidTest?.packaging?.jniLibs?.pickFirsts?.add("**/*.so")
    }
}

android {
    namespace = "com.pingidentity.auth.migration"
}

dependencies {
    implementation(project(":foundation:logger"))
    implementation(project(":foundation:migration"))
    implementation(project(":mfa:oath"))
    implementation(project(":mfa:push"))
    implementation(libs.forgerock.authenticator)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.core.ktx)
    implementation(libs.androidx.startup.runtime)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(project(":foundation:android"))

    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}