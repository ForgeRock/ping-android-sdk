/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.plugins

import com.android.build.api.dsl.CommonExtension
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the

fun Project.configureKotlinAndroid(extension: CommonExtension<*, *, *, *, *, *>) {
    val libs = the<LibrariesForLibs>()

    extension.apply {
        compileSdk = libs.versions.compileSdk.get().toInt()

        defaultConfig {
            minSdk = libs.versions.minSdk.get().toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        testOptions {
            unitTests {
                isIncludeAndroidResources = true
                isReturnDefaultValues = true
            }
        }

        buildFeatures {
            buildConfig = true
        }

        //val version: String by project
        val version: String  = System.getenv("RELEASE_TAG_NAME") ?: "0.0.0"

        defaultConfig {
            buildConfigField("String", "VERSION_NAME", "\"$version\"")
        }

    }
}