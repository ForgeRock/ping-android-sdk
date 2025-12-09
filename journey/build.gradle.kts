/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

description = "Journey library"

plugins {
    id("com.pingidentity.convention.android.library")
    id("com.pingidentity.convention.centralPublish")
    id("com.pingidentity.convention.jacoco")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.pingidentity.journey"

    unitTestVariants.all {
        this.mergedFlavor.manifestPlaceholders["appRedirectUriScheme"] = "com.pingidentity.demo"
    }

    testVariants.all {
        this.mergedFlavor.manifestPlaceholders["appRedirectUriScheme"] = "com.pingidentity.demo"
    }

    buildTypes {
        debug {
            enableAndroidTestCoverage = true
            enableUnitTestCoverage = true
        }
    }

    packaging {
        resources {
            excludes += mutableSetOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md")
        }
    }
}

dependencies {
    api(project(":foundation:journey-plugin"))
    api(project(":foundation:utils"))
    api(project(":foundation:oidc"))
    api(project(":foundation:orchestrate"))
    api(project(":foundation:logger"))
    api(project(":foundation:storage"))
    implementation(project(":foundation:android"))

    testImplementation(project(":foundation:testrail"))
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.startup.runtime)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.ktor.client.mock)
    testImplementation(project(":protect"))
    testImplementation(project(":mfa:fido"))

    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(project(":foundation:testrail"))
    androidTestImplementation(project(":protect"))
    androidTestImplementation(project(":foundation:device:device-profile"))
    androidTestImplementation(project(":foundation:device:device-id"))
    androidTestImplementation(project(":recaptcha-enterprise"))
    androidTestImplementation(project(":mfa:binding"))
    androidTestImplementation(libs.bcpkix.jdk18on)
    androidTestImplementation(libs.ktor.client.core)
    androidTestImplementation(libs.nimbus.jose.jwt)
    androidTestImplementation(libs.mockk)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlin.test)
}
