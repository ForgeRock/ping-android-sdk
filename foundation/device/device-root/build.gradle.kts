/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "Device Root Detection library"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.pingidentity.device.root"

    /**
     * Comment this to debug instrument Test,
     * There is an issue for AS to run NDK with instrument Test
     */
    externalNativeBuild {
        ndkBuild {
            path("src/main/jni/Android.mk")
        }
    }

    packaging {
        jniLibs {
            pickFirsts.add("**/*.so")
        }
    }
}

dependencies {
    implementation(project(":foundation:utils"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:logger"))

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
}