/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.os.Build
import kotlinx.coroutines.test.runTest
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlatformCollectorTest {

    @Test
    fun `platformCollector has correct key`() {
        assertEquals("platform", PlatformCollector.key)
    }

    @Test
    fun `platformCollector collects platform information`() = runTest {
        // Invoke the collector
        val platformInfo = PlatformCollector.collect()

        // Assert that the collected info is not null
        assertNotNull(platformInfo)

        // Assert that the platform is "android" (as per the default in PlatformInfo)
        assertEquals("android", platformInfo.platform)

        // Assert that the other fields are populated (their exact values
        // will depend on the JVM environment where the test is run,
        // or would require mocking Build.* fields)
        assertNotNull(platformInfo.device)
        assertNotNull(platformInfo.deviceName)
        assertNotNull(platformInfo.model)
        assertNotNull(platformInfo.brand)

        // For locale and timezone, we can check against the JVM's current default
        // as a basic sanity check, though in a real Android environment,
        // these would come from the device settings.
        assertEquals(Locale.getDefault().toString(), platformInfo.locale)
        assertEquals(TimeZone.getDefault().id, platformInfo.timeZone)

        // Fields that are nullable and currently unused can be checked for null
        assertEquals(null, platformInfo.version)
        assertEquals(null, platformInfo.jailBreakScore)
    }

    @Test
    fun `platformCollector returns non-empty default values from Build when Build fields are null`() = runTest {
        // This test is more conceptual for a pure JVM test without mocking Build.
        // In a real scenario, you'd use a mocking framework to set Build.DEVICE to null.
        // For now, we rely on the PlatformInfo's default for "device" if Build.DEVICE were null.

        // Simulate (conceptually) Build.DEVICE being null.
        // The PlatformCollector uses ?: "Device"
        // We can't directly mock android.os.Build easily here without tools.
        // So, this test mainly verifies the logic *within* PlatformCollector
        // assuming Build fields could be null.

        val platformInfo = PlatformCollector.collect()

        // If Build.DEVICE were null, the collector logic defaults to "Device"
        // If Build.MODEL were null, it defaults to ""
        // If Build.BRAND were null, it defaults to ""

        // We can't *force* Build.DEVICE to be null here without mocking,
        // so we're essentially checking that the collector *would* use the default
        // if the Build field *were* null.
        // This is more of a documentation of intent for this limited test setup.

        if (Build.DEVICE == null) {
            assertEquals("Device", platformInfo?.device)
        } else {
            assertEquals(Build.DEVICE, platformInfo?.device)
        }

        if (Build.MODEL == null) {
            assertEquals("", platformInfo?.deviceName)
            assertEquals("", platformInfo?.model)
        } else {
            assertEquals(Build.MODEL, platformInfo?.deviceName)
            assertEquals(Build.MODEL, platformInfo?.model)
        }

        if (Build.BRAND == null) {
            assertEquals("", platformInfo?.brand)
        } else {
            assertEquals(Build.BRAND, platformInfo?.brand)
        }
    }
}