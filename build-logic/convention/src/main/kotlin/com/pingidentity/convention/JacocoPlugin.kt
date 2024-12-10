/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.convention

import com.android.build.gradle.BaseExtension
import java.util.Locale
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

class JacocoPlugin : Plugin<Project> {
    private val Project.android: BaseExtension
        get() = extensions.findByName("android") as? BaseExtension
            ?: error("Not an Android module: $name")

    private val Project.jacoco: JacocoPluginExtension
        get() = extensions.findByName("jacoco") as? JacocoPluginExtension
            ?: error("Not a Jacoco module: $name")

    private val excludedFiles = mutableSetOf(
        "**/R.class",
        "**/R$*.class",
        "**/*\$ViewInjector*.*",
        "**/*\$ViewBinder*.*",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Factory*",
        "**/*_MembersInjector*",
        "**/*Module*",
        "**/*Component*",
        "**android**",
        "**/BR.class"
    )

    override fun apply(project: Project) =
        with(project) {
            plugins.run {
                apply("jacoco")
            }
            jacocoAfterEvaluate()
        }

    private fun Project.jacocoAfterEvaluate() = afterEvaluate {
        val buildTypes = android.buildTypes.map { type -> type.name }
        var productFlavors = android.productFlavors.map { flavor -> flavor.name }

        if (productFlavors.isEmpty()) {
            productFlavors = productFlavors + ""
        }

        productFlavors.forEach { flavorName ->
            buildTypes.forEach { buildTypeName ->
                val sourceName: String
                val sourcePath: String

                if (flavorName.isEmpty()) {
                    sourceName = buildTypeName
                    sourcePath = buildTypeName
                } else {
                    sourceName = "${flavorName}${buildTypeName.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(
                            Locale.ENGLISH
                        ) else it.toString()
                    }}"
                    sourcePath = "${flavorName}/${buildTypeName}"
                }

                val testTaskName = "test${sourceName.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        Locale.ENGLISH
                    ) else it.toString()
                }}UnitTest"

                registerCodeCoverageTask(
                    testTaskName = testTaskName,
                    sourceName = sourceName,
                    sourcePath = sourcePath,
                    flavorName = flavorName,
                    buildTypeName = buildTypeName
                )

                val connectedCheckTaskName = "connected${sourceName.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        Locale.ENGLISH
                    ) else it.toString()
                }}AndroidTest"

                registerCodeCoverageTask(
                    testTaskName = connectedCheckTaskName,
                    sourceName = sourceName,
                    sourcePath = sourcePath,
                    flavorName = flavorName,
                    buildTypeName = buildTypeName
                )
            }
        }
    }

    private fun Project.registerCodeCoverageTask(
        testTaskName: String,
        sourceName: String,
        sourcePath: String,
        flavorName: String,
        buildTypeName: String
    ) {
        tasks.register<JacocoReport>("${testTaskName}Coverage") {
            dependsOn(testTaskName)
            group = "Reporting"
            description = "Generate Jacoco coverage reports on the ${sourceName.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.ENGLISH
                ) else it.toString()
            }} build."

            val javaDirectories = fileTree(
                "${project.layout.buildDirectory.get().asFile}/intermediates/classes/${sourcePath}"
            ) { exclude(excludedFiles) }

            val kotlinDirectories = fileTree(
                "${project.layout.buildDirectory.get().asFile}/tmp/kotlin-classes/${sourcePath}"
            ) { exclude(excludedFiles) }

            val coverageSrcDirectories = listOf(
                "src/main/java",
                "src/$flavorName/java",
                "src/$buildTypeName/java"
            )

            classDirectories.setFrom(files(javaDirectories, kotlinDirectories))
            additionalClassDirs.setFrom(files(coverageSrcDirectories))
            sourceDirectories.setFrom(files(coverageSrcDirectories))
            executionData.setFrom(files(
                fileTree(layout.buildDirectory) { include(listOf("**/*.exec", "**/*.ec")) }
            ))

            reports {
                xml.required.set(true)
                html.required.set(true)
                xml.outputLocation.set(file("${project.layout.buildDirectory.get().asFile}/coverage-report/${testTaskName}-test-coverage.xml"))
            }
        }
    }
}