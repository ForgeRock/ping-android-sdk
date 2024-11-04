/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

plugins {
    id("com.pingidentity.convention.android.library")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.journey"

    unitTestVariants.all {
        this.mergedFlavor.manifestPlaceholders["appAuthRedirectScheme"] = "com.pingidentity.demo"
    }

    testVariants.all {
        this.mergedFlavor.manifestPlaceholders["appAuthRedirectScheme"] = "com.pingidentity.demo"
    }
}

dependencies {
    api(project(":foundation:journey-plugin"))
    api(project(":foundation:utils"))
    api(project(":foundation:oidc"))
    api(project(":foundation:orchestrate"))
    api(project(":foundation:logger"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:storage"))

    implementation(libs.androidx.datastore)
    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.ktor.client.mock)
}
