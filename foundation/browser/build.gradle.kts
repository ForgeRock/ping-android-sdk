/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "Browser library"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.pingidentity.browser"

    // SDKS-5296: Scope the redirect-scheme placeholder to the debug build type only.
    // The release AAR (the published variant) intentionally leaves ${appRedirectUriScheme}
    // unresolved so that downstream apps can supply their own value via manifestPlaceholders.
    // The debugUnitTest and debugAndroidTest variants inherit this value from debug,
    // which is sufficient for all local test runs.
    buildTypes {
        debug {
            manifestPlaceholders["appRedirectUriScheme"] = "com.pingidentity.test"
        }
    }
}

dependencies {

    implementation(project(":foundation:android"))
    implementation(project(":foundation:logger"))
    api(libs.androidx.browser)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.core.ktx)
}