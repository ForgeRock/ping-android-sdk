/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device

import android.content.Context
import android.content.pm.PackageManager
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.detector.BuildTagsDetector
import com.pingidentity.device.detector.TamperDetector
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Comprehensive test suite for the device tampering analysis functionality.
 *
 * This test class validates the behavior of the [analyze] function and its interaction
 * with various tamper detection mechanisms. It covers scenarios including:
 * - Custom detector integration and behavior
 * - Default detector functionality
 * - Mixed detection scenarios with multiple detectors
 * - Proper Android context and package manager mocking
 *
 * The tests use extensive mocking to simulate Android system components and ensure
 * consistent test results across different environments without requiring actual
 * device tampering or root access.
 *
 * @see analyze
 * @see TamperDetector
 * @see DeviceTamperConfig
 */
class AnalyzeTest {
    /**
     * Mock Android context used for testing detector functionality.
     * Provides necessary system services and package manager access.
     */
    private val context: Context = mockk()

    /**
     * Sets up the test environment before each test execution.
     *
     * Initializes:
     * - Mock Android context with application context
     * - ContextProvider for global context access
     * - Basic context mocking for system service access
     */
    @BeforeTest
    fun setup() {
        every { context.applicationContext } returns mockk()
        ContextProvider.init(context)
    }

    /**
     * Tests device analysis with a custom tamper detector.
     *
     * Verifies that:
     * - Custom detectors can be added to the analysis configuration
     * - The analyze function properly executes custom detection logic
     * - A detector that always returns true correctly indicates tampering
     * - The DSL configuration syntax works as expected
     */
    @Test
    fun `Test with custom detector`() = runTest {
        val result = analyze {
            detector {
                add(TamperDetector {
                    1.0
                })
            }
        }

        assertEquals(1.0, result, "Should be tampered")
    }

    /**
     * Tests device analysis using the default detector configuration.
     *
     * Validates that:
     * - The analyze function works without explicit configuration
     * - Default detectors are properly initialized and executed
     * - Package manager mocking supports default detector functionality
     * - The result reflects the behavior of default detection mechanisms
     */
    @Test
    fun testScanWithDefaultDetector() = runTest {
        mockPackageManager()
        val result = analyze()
        assertEquals(0.0, result) // Assuming no root detected by default detectors
    }

    /**
     * Tests device analysis with a mixture of custom and predefined detectors.
     *
     * This test verifies:
     * - Multiple detectors can be configured simultaneously
     * - Custom detectors (returning false) and predefined detectors work together
     * - The BuildTagsDetector integration functions correctly
     * - Analysis returns false when no detectors indicate tampering
     * - Complex detection scenarios are handled properly
     */
    @Test
    fun `mix with custom and predefined`() = runTest {
        val result = analyze {
            detector {
                add(TamperDetector { 0.0 })
                add(BuildTagsDetector())
            }
        }
        assertEquals(0.0, result,"Should not be tampered")
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