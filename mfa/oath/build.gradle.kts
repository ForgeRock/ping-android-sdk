/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
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
    namespace = "com.pingidentity.mfa.oath"
    
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(project(":mfa:commons"))
    implementation(project(":foundation:logger"))
    implementation(project(":foundation:android"))
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.core)
    
    // Base64 implementation
    implementation(libs.commons.codec)

    // SQLite implementation
    implementation(libs.androidx.sqlite)

    // SQL Cipher for encrypted database
    implementation(libs.sqlcipher)
    
    // Testing dependencies
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    
    // ForgeRock dependency for compatibility tests
    androidTestImplementation("org.forgerock:forgerock-authenticator:4.8.0") {
        exclude(group = "com.google.android.gms") // Exclude GMS to avoid conflicts
    }
}
