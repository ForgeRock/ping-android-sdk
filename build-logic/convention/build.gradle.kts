/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
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

// build-logic is an included build: the root project's resolutionStrategy blocks
// do not reach this classpath. Force the same secure versions here.
// asm is used transitively by AGP (gradle-api); fixes CVE-2026-110257..110263.
// jackson is declared by dokka-core's POM (2.15.3) for its runtime variant; the
// forces are defensive — jackson is absent from the compileOnly classpath today
// but would appear unforced if the dokka dependency scope changes.
// guava is likewise a transitive of the build toolchain; fixes CVE-2026-102554.
configurations.all {
    resolutionStrategy {
        force(libs.asm)
        force(libs.asm.commons)
        force(libs.asm.analysis)
        force(libs.asm.tree)
        force(libs.asm.util)
        force(libs.jackson.module.kotlin)
        force(libs.jackson.dataformat.xml)
        force(libs.jackson.databind)
        force(libs.guava)
    }
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