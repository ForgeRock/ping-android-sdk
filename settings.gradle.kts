/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        //mavenLocal()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

rootProject.name = "ping"

include(":davinci")
include(":foundation")
include(":foundation:android")
include(":foundation:davinci-plugin")
//include(":foundation:device")
//include(":foundation:device:device-binding")
//include(":foundation:device:device-id")
//include(":foundation:device:device-integrity")
//include(":foundation:fido")
include(":foundation:journey-plugin")
include(":foundation:logger")
//include(":foundation:network")
include(":foundation:oidc")
include(":foundation:orchestrate")
include(":foundation:storage")
include(":foundation:utils")
include(":journey")
//include(":mfa")
//include(":protect")
include(":external-idp")
//include(":verify")
//include(":wallet")
include(":foundation:testrail")

include(":samples:app")
include(":samples:journeyapp")
