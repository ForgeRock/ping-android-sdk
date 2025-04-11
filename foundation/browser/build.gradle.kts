/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.pingidentity.browser"

    unitTestVariants.all {
        this.mergedFlavor.manifestPlaceholders["appRedirectUriScheme"] = "com.pingidentity.demo"
    }
    testVariants.all {
        this.mergedFlavor.manifestPlaceholders["appRedirectUriScheme"] = "com.pingidentity.demo"
    }
}

dependencies {

    implementation(project(":foundation:android"))
    implementation(project(":foundation:logger"))
    api(libs.androidx.browser)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.core.ktx)
}