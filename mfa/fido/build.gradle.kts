/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
description = "Fido library"

plugins {
    id("com.pingidentity.convention.android.library")
    //TODO uncomment when ready to publish
    //id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.fido"
}

dependencies {
    implementation(project(":foundation:android"))
    implementation(project(":foundation:utils"))
    implementation(project(":foundation:logger"))
    implementation(project(":foundation:davinci-plugin"))
    implementation(project(":foundation:journey-plugin"))
    compileOnly(libs.play.services.fido)
    implementation(libs.kotlinx.coroutines.play.services)
    //Expose the interface for customization
    api(libs.androidx.credentials)

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.startup.runtime)

    testImplementation(libs.play.services.fido)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)


}
