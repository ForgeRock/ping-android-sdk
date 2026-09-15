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

buildscript {
    configurations.all {
        resolutionStrategy {
            // Force secure version of bouncy castle to address security vulnerabilities
            // Fixes CVE-2026-8763; targets build-toolchain classpath (lint-gradle)
            force("org.bouncycastle:bcprov-jdk18on:1.85")
            force("org.bouncycastle:bcpkix-jdk18on:1.85")
            force("org.bouncycastle:bcutil-jdk18on:1.85")
        }
    }
}

allprojects {
    configurations.all {

        resolutionStrategy {
            // Force secure version of play-services-basement to address security vulnerabilities
            // Used transitively by recaptcha client and fido subprojects
            // Updated to 18.0.2 which fixes CVE-2022-2390
            force("com.google.android.gms:play-services-basement:18.0.2")

            // Force secure version of nimbus-jose-jwt to address security vulnerabilities
            // Used transitively by mfa:binding subproject; fixes CVE-2025-53864
            force("com.nimbusds:nimbus-jose-jwt:10.5")

            // Force secure version of bouncy castle to address security vulnerabilities
            // Fixes CVE-2026-8763; targets build-toolchain classpath (lint-gradle)
            force("org.bouncycastle:bcprov-jdk18on:1.85")
            force("org.bouncycastle:bcpkix-jdk18on:1.85")
            force("org.bouncycastle:bcutil-jdk18on:1.85")

            // Updated to 2.22.2 per Mend SCA vulnerability [CVE-2026-68494], [CVE-2026-54512], [CVE-2026-54513] from dokka project.
            force("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.2")
            force("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.22.2")
            force("com.fasterxml.jackson.core:jackson-databind:2.22.2")

            // Force secure version of netty-codec to address security vulnerabilities
            // Used transitively by com.android.tools.emulator:proto (build toolchain only)
            // Updated to 4.1.125.Final per Mend SCA recommendation (Nov 2025)
            force("io.netty:netty-codec:4.1.125.Final")
            force("io.netty:netty-codec-http:4.1.125.Final")
            force("io.netty:netty-codec-http2:4.1.125.Final")
            force("io.netty:netty-all:4.1.125.Final")

            // Force secure version of protobuf to address security vulnerabilities
            // Used transitively by AGP/build toolchain
            // Updated to 4.29.2 (latest stable) which fixes CVE-2024-7254 and all known CVEs
            force("com.google.protobuf:protobuf-java:4.29.2")
            force("com.google.protobuf:protobuf-kotlin:4.29.2")
            force("com.google.protobuf:protobuf-javalite:4.29.2")
            force("com.google.protobuf:protobuf-kotlin-lite:4.29.2")
            //Due to [CVE-2026-71497]:
            // org.jsoup:jsoup:1.16.1, transitive runtime dependency of
            // org.jetbrains.dokka:dokka-base:2.0.0 (build-time only, not shipped in the SDK).
            force("org.jsoup:jsoup:1.23.2")
            // Due to [CVE-2020-13956]:
            // org.apache.httpcomponents:httpclient:4.5.6, transitive runtime dependency of
            // org.jetbrains.dokka:dokka-base:2.0.0 (build-time only, not shipped in the SDK).
            force("org.apache.httpcomponents:httpclient:4.5.13")
            // Due to [CVE-2026-84939]:
            // freemarker-2.3.32, transitive runtime dependency of
            // org.jetbrains.dokka:dokka-base:2.0.0 (build-time only, not shipped in the SDK).
            force("org.freemarker:freemarker:2.3.35")
            // Due to [CVE-2025-48924]:
            // commons-lang3-3.16.0, transitive runtime dependency of
            // lint-gradle-32.2.1.jar (build-time only, not shipped in the SDK).
            force("org.apache.commons:commons-lang3:3.18.0")
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