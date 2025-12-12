/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.utils

import android.os.Build
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Annotation to mark tests that should only run on physical devices.
 * Can be applied to individual functions or entire classes.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresDevice

/**
 * JUnit 4 Rule that checks for the @RequiresDevice annotation.
 * If present and running on an emulator, the test is ignored.
 */
class DeviceSkipRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                // Check if the specific method OR the class has the annotation
                val needsDevice = description.getAnnotation(RequiresDevice::class.java) != null ||
                        description.testClass.getAnnotation(RequiresDevice::class.java) != null

                if (needsDevice) {
                    // assumeFalse throws an AssumptionViolatedException if the condition is true.
                    // JUnit 4 catches this and marks the test as "Ignored".
                    Assume.assumeFalse("Skipping test: Physical Device Required", isEmulator)
                }

                // Proceed with the test
                base.evaluate()
            }
        }
    }

    // Robust emulator detection logic
    private val isEmulator: Boolean
        get() = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                (Build.BRAND.startsWith("google") && Build.DEVICE.startsWith("generic"))
}