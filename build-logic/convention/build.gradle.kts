/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.UsesKotlinJavaToolchain

plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.dokka.gradlePlugin)
    //https://github.com/gradle/gradle/issues/15383
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "com.pingidentity.convention.android.library"
            implementationClass = "com.pingidentity.convention.AndroidLibraryConventionPlugin"
        }
        register("centralPublish") {
            id = "com.pingidentity.convention.centralPublish"
            implementationClass = "com.pingidentity.convention.MavenCentralPublishConventionPlugin"
        }
        register("jacoco-reports") {
            id = "com.pingidentity.convention.jacoco"
            implementationClass = "com.pingidentity.convention.JacocoPlugin"
        }
    }
}