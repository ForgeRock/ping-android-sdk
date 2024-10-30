/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
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
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.oidc"

    unitTestVariants.all {
        this.mergedFlavor.manifestPlaceholders["appAuthRedirectScheme"] = "com.pingidentity.demo"
    }
    testVariants.all {
        this.mergedFlavor.manifestPlaceholders["appAuthRedirectScheme"] = "com.pingidentity.demo"
    }
}

dependencies {
    api(project(":foundation:utils"))
    api(project(":foundation:logger"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:storage"))

    implementation(libs.androidx.datastore)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.cio)
    implementation(libs.androidx.appcompat)
    compileOnly(libs.appauth)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.androidx.activity)

    testImplementation(libs.androidx.junit.ktx)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)
    testImplementation(libs.appauth)

    testImplementation(project(":foundation:testrail"))
}
