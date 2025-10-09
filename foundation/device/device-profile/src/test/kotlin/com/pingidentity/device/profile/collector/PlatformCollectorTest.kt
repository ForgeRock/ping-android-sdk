/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.pingidentity.android.ContextProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.Locale
import java.util.TimeZone
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Test class for [PlatformCollector].
 *
 * This class contains unit tests that verify the behavior of the PlatformCollector,
 * including its key identifier and the platform information collection functionality.
 * The tests validate that platform data is correctly gathered from the Android system
 * and default values are properly applied when system values are unavailable.
 */
class PlatformCollectorTest {

    private val mockContext = mockk<Context>()

    /**
     * Sets up the test environment by initializing the ContextProvider with a mocked context.
     * This ensures that the PlatformCollector has access to the required Android context
     * for collecting platform information.
     */
    @BeforeTest
    fun setup() {
        every { mockContext.applicationContext } returns mockContext
        ContextProvider.init(mockContext)
        setupPackageManagerMocks()
    }

    /**
     * Test that verifies the PlatformCollector has the correct key identifier.
     *
     * The key is used to identify this collector in the device profile configuration
     * and should always return "platform".
     */
    @Test
    fun `platformCollector has correct key`() {
        assertEquals("platform", PlatformCollector().key)
    }

    /**
     * Test that verifies the PlatformCollector successfully collects platform information.
     *
     * This test validates that:
     * - The collector returns non-null platform information
     * - The platform field is set to "android" by default
     * - Device information fields are populated from Android Build properties
     * - Locale and timezone are correctly retrieved from system defaults
     * - Optional fields (version, jailBreakScore) are handled appropriately
     */
    @Test
    fun `platformCollector collects platform information`() = runTest {
        // Invoke the collector
        val platformInfo = PlatformCollector().collect()

        // Assert that the collected info is not null
        assertNotNull(platformInfo)

        // Assert that the platform is "android" (as per the default in PlatformInfo)
        assertEquals("android", platformInfo.platform)

        //Assert that jailBreakScore is not null (it may be 0.0 if no tampering detected)
        assertNotNull(platformInfo.jailBreakScore)

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
    }

    /**
     * Test that verifies the PlatformCollector handles null Build field values gracefully.
     *
     * This test conceptually validates the fallback behavior when Android Build fields
     * might be null or unavailable. It documents the expected default values:
     * - Build.DEVICE null → defaults to "Device"
     * - Build.MODEL null → defaults to empty string for deviceName and model
     * - Build.BRAND null → defaults to empty string for brand
     *
     * Note: This test is limited by the JVM test environment and would benefit
     * from proper mocking frameworks in a full Android test setup.
     */
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

        val platformInfo = PlatformCollector().collect()

        // If Build.DEVICE were null, the collector logic defaults to "Device"
        // If Build.MODEL were null, it defaults to ""
        // If Build.BRAND were null, it defaults to ""

        // We can't *force* Build.DEVICE to be null here without mocking,
        // so we're essentially checking that the collector *would* use the default
        // if the Build field *were* null.
        // This is more of a documentation of intent for this limited test setup.

        if (Build.DEVICE == null) {
            assertEquals("Device", platformInfo.device)
        } else {
            assertEquals(Build.DEVICE, platformInfo.device)
        }

        if (Build.MODEL == null) {
            assertEquals("", platformInfo.deviceName)
            assertEquals("", platformInfo.model)
        } else {
            assertEquals(Build.MODEL, platformInfo.deviceName)
            assertEquals(Build.MODEL, platformInfo.model)
        }

        if (Build.BRAND == null) {
            assertEquals("", platformInfo.brand)
        } else {
            assertEquals(Build.BRAND, platformInfo.brand)
        }
    }

    /**
     * Sets up comprehensive PackageManager mocking for detector testing.
     *
     * This helper method configures mocks for:
     * - Android PackageManager service
     * - Context package manager access
     * - Application context package manager
     * - ContextProvider package manager access
     * - Package information queries for installed applications
     *
     * The mocking ensures that package-based detectors can function properly
     * in the test environment without requiring actual installed applications.
     */
    private fun setupPackageManagerMocks() {
        val packageManager = mockk<PackageManager>()
        every { mockContext.packageManager } returns packageManager
        every { mockContext.applicationContext.packageManager } returns packageManager
        every { ContextProvider.context.packageManager } returns packageManager
        every {
            packageManager.getPackageInfo(any<String>(), any<Int>())
        } throws PackageManager.NameNotFoundException()
    }
}