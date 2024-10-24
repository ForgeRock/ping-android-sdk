/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

plugins {
    id("com.pingidentity.convention.android.library")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.pingidentity.idp"

    unitTestVariants.all {
        this.mergedFlavor.manifestPlaceholders["appRedirectUriScheme"] = "com.pingidentity.demo"
    }
    testVariants.all {
        this.mergedFlavor.manifestPlaceholders["appRedirectUriScheme"] = "com.pingidentity.demo"
    }
}

dependencies {
    api(project(":foundation:utils"))
    implementation(project(":foundation:logger"))
    implementation(project(":foundation:android"))
    implementation(project(":foundation:davinci-plugin"))
    implementation(libs.ktor.client.core)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.browser)

    //Make it optional for developer
    compileOnly(libs.googleid)
    compileOnly(libs.facebook.login)

    testImplementation(libs.kotlin.test)
}
