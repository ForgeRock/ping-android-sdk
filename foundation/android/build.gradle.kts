/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "Android library"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.pingidentity.android"
    defaultConfig {
        manifestPlaceholders["appRedirectUriScheme"] = "com.pingidentity.test"
    }
}

tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().configureEach {
    failOnNoDiscoveredTests = false
}

dependencies {
    implementation(libs.androidx.startup.runtime)
}