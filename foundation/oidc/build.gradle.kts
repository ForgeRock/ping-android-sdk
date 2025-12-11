/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "OIDC library"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.oidc"

    unitTestVariants.all {
        this.mergedFlavor.manifestPlaceholders["appRedirectUriScheme"] = "com.pingidentity.demo"
    }
    testVariants.all {
        this.mergedFlavor.manifestPlaceholders["appRedirectUriScheme"] = "com.pingidentity.demo"
    }
}

dependencies {
    api(project(":foundation:utils"))
    api(project(":foundation:logger"))
    api(project(":foundation:orchestrate"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:storage"))
    implementation(project(":foundation:network"))

    //Make it optional for using centralize login
    compileOnly(project(":foundation:browser"))

    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.browser)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.activity)

    testImplementation(libs.androidx.junit.ktx)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)
    testImplementation(kotlin("reflect"))
    testImplementation(project(":foundation:browser"))

    testImplementation(project(":foundation:testrail"))
}
