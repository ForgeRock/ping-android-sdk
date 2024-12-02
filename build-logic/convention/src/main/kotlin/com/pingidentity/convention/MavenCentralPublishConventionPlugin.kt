/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

class MavenCentralPublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("maven-publish")
                apply("kotlin-android")
                apply("signing")
                apply("org.jetbrains.dokka")
            }

            val javadocJar = tasks.register("javadocJar", Jar::class.java) {
                archiveClassifier.set("javadoc")
                from(tasks.getByName("dokkaHtml"))
            }

            //The source only includes the README.md, delete this if we want to include the whole source
            val sourcesJar = tasks.register<Jar>("sourcesJar") {
                archiveClassifier.set("sources")
                from("README.md")
            }

            extensions.configure<PublishingExtension> {

                publications {
                    create<MavenPublication>("release") {

                        pom {
                            groupId = rootProject.group.toString()
                            artifactId = project.name
                            name.set(project.name)
                            version = rootProject.version.toString()

                            val pom = this
                            project.afterEvaluate {
                                tasks.named("generateMetadataFileForReleasePublication") {
                                    dependsOn(tasks.named("sourcesJar"))
                                }

                                artifact(javadocJar)

                                //This to overwrite the default source artifact
                                artifacts {
                                    add("archives", sourcesJar)
                                }

                                pom.description.set(project.description)
                                from(components.getByName("release"))
                            }
                            url.set("https://github.com/ForgeRock/ping-android-sdk")
                            licenses {
                                license {
                                    name.set("MIT")
                                    url.set("https://opensource.org/licenses/MIT")
                                }
                                developers {
                                    developer {
                                        id.set("andy.witrisna")
                                        name.set("Andy Witrisna")
                                        email.set("andy.witrisna@pingidentity.com")
                                    }
                                    developer {
                                        id.set("stoyan.petrov")
                                        name.set("Stoyan Petrov")
                                        email.set("stoyan.petrov@pingidentity.com")
                                    }
                                    developer {
                                        id.set("jey.periyasamy")
                                        name.set("Jey Periyasamy")
                                        email.set("jey.periyasamy@pingidentity.com")
                                    }
                                }
                                scm {
                                    connection.set("https://github.com/ForgeRock/ping-android-sdk.git")
                                    developerConnection.set("https://github.com/ForgeRock/ping-android-sdk.git")
                                    url.set("https://github.com/ForgeRock/ping-android-sdk.git")
                                }
                            }
                        }
                    }
                }
            }

            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(
                    System.getenv("OSS_SIGNING_KEY_ID"),
                    System.getenv("OSS_SIGNING_KEY"),
                    System.getenv("OSS_SIGNING_PASSWORD")
                )
                val publishing = extensions.getByType<PublishingExtension>()
                sign(publishing.publications)
            }


            // https://github.com/gradle/gradle/issues/26091
            tasks.withType<AbstractPublishToMaven>().configureEach {
                val signingTasks = tasks.withType<Sign>()
                mustRunAfter(signingTasks)
            }
        }
    }
}