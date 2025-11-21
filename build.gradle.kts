/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

import com.adarshr.gradle.testlogger.TestLoggerExtension
import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    //trick: for the same plugin versions in all sub-modules
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.jetbrainsKotlinJvm) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.testLogger) apply false
    alias(libs.plugins.nexusPublish)
    alias(libs.plugins.dokka)
}

//According to https://github.com/gradle-nexus/publish-plugin
//It is important to set the group and the version to the root project, so the plugin can detect if
// it is a snapshot version or not in order to select the correct repository where artifacts will be published.
group = "com.pingidentity.sdks"
version = System.getenv("RELEASE_TAG_NAME") ?: "0.0.0"

nexusPublishing {
    repositories {

        sonatype {
            username = System.getenv("OSS_USERNAME") //Token Id
            password = System.getenv("OSS_PASSWORD") //Token
            //stagingProfileId = System.getenv("OSS_STAGING_PROFILE_ID")
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}

allprojects {
    configurations.all {

        resolutionStrategy {
            // Due to vulnerability [WS-2022-0468] from dokka project.
            force("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.0")
            force("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.15.0")
            force("com.fasterxml.jackson.core:jackson-databind:2.15.0")

            // Force secure version of netty-codec to address security vulnerabilities
            // Used transitively by ktor-client-cio
            // Updated to 4.1.125.Final per Mend SCA recommendation (Nov 2025)
            force("io.netty:netty-codec:4.1.125.Final")
            force("io.netty:netty-codec-http:4.1.125.Final")
            force("io.netty:netty-codec-http2:4.1.125.Final")
            force("io.netty:netty-all:4.1.125.Final")

            // Force secure version of protobuf to address security vulnerabilities
            // Used transitively by various Google dependencies
            // Updated to 4.29.2 (latest stable) which fixes CVE-2024-7254 and all known CVEs
            force("com.google.protobuf:protobuf-java:4.29.2")
            force("com.google.protobuf:protobuf-kotlin:4.29.2")
            force("com.google.protobuf:protobuf-javalite:4.29.2")
            force("com.google.protobuf:protobuf-kotlin-lite:4.29.2")
        }
    }
}

subprojects {

    apply {
        plugin("com.adarshr.test-logger")
    }

    configure<TestLoggerExtension> {
        theme = ThemeType.MOCHA
    }
}

// Commend to generate all doc ./gradlew dokkaGenerate
dependencies {
    subprojects
        .filter {
            //it.plugins does not work
            val buildFile = it.buildFile
            buildFile.exists() && buildFile.readText()
                .contains("com.pingidentity.convention.centralPublish")
        }
        .forEach { subproject ->
            dokka(subproject)
        }
}