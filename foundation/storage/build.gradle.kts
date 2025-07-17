/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "Storage library"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.storage"

    buildTypes {
        debug {
            enableAndroidTestCoverage = true
            enableUnitTestCoverage = true
        }
    }
}

dependencies {
    api(project(":foundation:logger"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:utils"))

    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.core)

    // Google BlockStore for cloud-backed secure storage
    implementation(libs.play.services.auth.blockstore)

    compileOnly(libs.androidx.datastore.preferences) //Make it optional for developer

    // SQL Cipher for encrypted database
    compileOnly(libs.sqlcipher)
    compileOnly(libs.androidx.sqlite)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.core.ktx)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.robolectric)
    testImplementation(project(":foundation:testrail"))

    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.datastore.preferences) //Make it optional for developer
    androidTestImplementation(project(":foundation:testrail"))
    
    // Add SQLCipher for tests
    androidTestImplementation(libs.sqlcipher)
    androidTestImplementation(libs.androidx.sqlite)
}