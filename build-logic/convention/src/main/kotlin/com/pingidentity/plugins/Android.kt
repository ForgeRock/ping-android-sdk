/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.plugins

import com.android.build.api.dsl.CommonExtension
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the

fun Project.configureKotlinAndroid(extension: CommonExtension) {
    val libs = the<LibrariesForLibs>()

    val releaseTagName = System.getenv("RELEASE_TAG_NAME") ?: "0.0.0"

    extension.apply {
        compileSdk = libs.versions.compileSdk.get().toInt()

        defaultConfig.apply {
            minSdk = libs.versions.minSdk.get().toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            buildConfigField(
                "String",
                "VERSION_NAME",
                "\"$releaseTagName\""
            )
        }

        testOptions.apply {
            // Robolectric 4.16.1 + Java 17: SDK 36 requires Java 21, so cap at 35.
            targetSdk = 35
            unitTests.apply {
                isIncludeAndroidResources = true
                isReturnDefaultValues = true
            }
        }

        buildFeatures.apply {
            buildConfig = true
        }
    }
}