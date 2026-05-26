/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.convention

import com.android.build.gradle.LibraryExtension
import com.pingidentity.plugins.configureJava
import com.pingidentity.plugins.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                namespace = "com.pingidentity.${project.name.replace("-", ".")}"
                // bcprov-jdk18on 1.83+ contains Java 25 multi-release class files (major version 69).
                // AGP's default JaCoCo 0.8.12 (ASM 9.7) cannot instrument them; 0.8.13 (ASM 9.8) can.
                testCoverage { jacocoVersion = "0.8.13" }
                publishing {
                    singleVariant("release") {
                        withSourcesJar()
                        //Issue https://github.com/Kotlin/dokka/issues/2956
                        //withJavadocJar()
                    }
                }
            }

            configureJava()
        }
    }
}