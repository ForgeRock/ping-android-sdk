/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import android.content.pm.PackageManager
import com.pingidentity.android.ContextProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test class for [RootRequiredAppDetector].
 *
 * This class tests the functionality of the RootRequiredAppDetector which analyzes installed
 * applications to detect those that require root access. The tests verify that the detector
 * properly identifies known root-required apps and handles scenarios where no such apps are present.
 *
 * The test uses a concrete implementation of RootRequiredAppDetector to verify its behavior
 * and package checking logic.
 */
class RootRequiredAppDetectorTest {
    private val context: Context = mockk()

    /**
     * Sets up the test environment by initializing the ContextProvider with a mocked context.
     * This ensures that the detector has access to the required Android context for package analysis.
     */
    @BeforeTest
    fun setup() {
        every { context.applicationContext } returns mockk()
        ContextProvider.init(context)
    }

    /**
     * Tests that RootRequiredAppDetector returns the correct list of known root-required apps.
     *
     * This test verifies that the detector correctly identifies and returns a list of
     * package names that are known to require root access. The expected list is compared
     * against the CURRENT_KNOWN_APPS_REQUIRE_ROOT constant to ensure accuracy.
     */
    @Test
    fun `RootRequiredAppDetector returns correct packages`() {
        val detector = RootRequiredAppDetector
        val packages = detector.getPackages()

        assert(packages.isNotEmpty())
        assertEquals(RootRequiredAppDetector.CURRENT_KNOWN_APPS_REQUIRE_ROOT, packages)
    }

    /**
     * Tests that RootRequiredAppDetector returns 0.0 when no root-required apps are found.
     *
     * This test sets up a mocked PackageManager that simulates the absence of any
     * installed applications requiring root access. The detector should return 0.0,
     * indicating that no root-required apps were detected on the device.
     */
    @Test
    fun `RootRequiredAppDetector analyze returns 0_0 when no root required apps found`() = runTest {
        mockPackageManager()
        val detector = RootRequiredAppDetector
        val result = detector.analyze(context)
        assertEquals(0.0, result)
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
    private fun mockPackageManager() {
        val packageManager = mockk<PackageManager>()
        every { context.packageManager } returns packageManager
        every { context.applicationContext.packageManager } returns packageManager
        every { ContextProvider.context.packageManager } returns packageManager
        every {
            packageManager.getPackageInfo(any<String>(), any<Int>())
        } throws PackageManager.NameNotFoundException()
    }
}