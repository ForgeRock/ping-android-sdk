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
}

android {
    namespace = "com.pingidentity.journey.plugin"
}

dependencies {

    api(project(":foundation:orchestrate"))
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
}