/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "Recaptcha Enterprise Library"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.pingidentity.recaptcha.enterprise"
}

dependencies {
    implementation(project(":foundation:journey-plugin"))
    implementation(project(":foundation:utils"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:logger"))
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.startup.runtime)
    api(libs.google.android.recaptcha)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.core.ktx)
    testImplementation(libs.mockk)
    testImplementation(libs.google.android.recaptcha)
}
