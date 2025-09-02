/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile

import android.content.Context
import android.telephony.TelephonyManager
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.profile.collector.TelephonyCollector
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [TelephonyCollector] to verify telephony and carrier information collection functionality.
 *
 * This test class covers various scenarios including:
 * - Successful telephony data collection with valid carrier information
 * - Error handling when telephony services are unavailable (tablets, WiFi-only devices)
 * - Verification of correct key identifier usage
 * - Network country ISO and carrier name extraction
 *
 * The tests use MockK to mock Android telephony services to isolate the telephony
 * collection logic from platform dependencies and device-specific telephony capabilities.
 *
 * **Test Coverage:**
 * - Happy path: Valid telephony service with carrier and country information
 * - Error handling: UnsupportedOperationException for devices without telephony
 * - Data structure: Correct mapping of telephony data to TelephonyInfo
 */
class TelephonyCollectorTest {
    private val mockContext = mockk<Context>()
    private val mockTelephonyManager = mockk<TelephonyManager>()

    /**
     * Sets up mock objects and stubs for each test case.
     *
     * Configures mocks for:
     * - Android Context through [ContextProvider]
     * - TelephonyManager for accessing carrier and network information
     */
    @BeforeTest
    fun setUp() {
        mockkObject(ContextProvider)
        every { ContextProvider.context } returns mockContext
    }

    /**
     * Cleans up all mocks after each test to prevent interference between tests.
     * This ensures test isolation and prevents mock state from leaking between test cases.
     */
    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Verifies that the telephony collector uses the correct key identifier.
     *
     * The key is used to identify this collector's data in the device profile
     * and should consistently return "telephony".
     */
    @Test
    fun `TelephonyCollector has the correct key`() {
        assertEquals("telephony", TelephonyCollector.key)
    }

    /**
     * Verifies successful telephony information collection when telephony services are available.
     *
     * Tests the complete flow of gathering:
     * - Network country ISO code (e.g., "US", "CA", "GB")
     * - Carrier/operator name (e.g., "Verizon", "AT&T", "Rogers")
     *
     * This represents the happy path for smartphones and devices with active
     * cellular connections where TelephonyManager provides valid data.
     */
    @Test
    fun `TelephonyCollector collects telephony information`() = runTest {
        every {
            mockContext.getSystemService(Context.TELEPHONY_SERVICE)
        } returns mockTelephonyManager
        every { mockTelephonyManager.networkOperatorName } returns "Verizon"
        every { mockTelephonyManager.networkCountryIso } returns "US"

        val result = TelephonyCollector.collect()

        assertEquals("Verizon", result?.carrierName)
        assertEquals("US", result?.networkCountryIso)
    }

    /**
     * Verifies graceful handling of devices that don't support telephony services.
     *
     * Tests error handling for [UnsupportedOperationException] which can occur on:
     * - WiFi-only tablets
     * - Devices without cellular hardware
     * - Emulators without telephony simulation
     * - Devices where telephony services are disabled
     *
     * When telephony is unsupported, the collector should return null rather than
     * crashing or throwing exceptions, allowing other device profile collectors
     * to continue functioning normally.
     */
    @Test
    fun `TelephonyCollector handles UnsupportedOperationException gracefully`() = runTest {
        every {
            mockContext.getSystemService(Context.TELEPHONY_SERVICE)
        } throws UnsupportedOperationException("Telephony not supported")

        val result = TelephonyCollector.collect()

        // Expecting null result when telephony is not supported
        assertNull(result)
    }

    /**
     * Verifies telephony collection when TelephonyManager returns null values.
     *
     * Tests handling of edge cases where the TelephonyManager is available but
     * returns null for network information, which can happen when:
     * - Device is not connected to a cellular network
     * - SIM card is not inserted or not activated
     * - Device is in airplane mode
     * - Network registration has failed
     */
    @Test
    fun `TelephonyCollector handles null telephony values`() = runTest {
        every {
            mockContext.getSystemService(Context.TELEPHONY_SERVICE)
        } returns mockTelephonyManager
        every { mockTelephonyManager.networkOperatorName } returns null
        every { mockTelephonyManager.networkCountryIso } returns null

        val result = TelephonyCollector.collect()

        // Should still return TelephonyInfo object but with null values
        assertEquals(null, result?.carrierName)
        assertEquals(null, result?.networkCountryIso)
    }

    /**
     * Verifies telephony collection when only partial information is available.
     *
     * Tests scenarios where some telephony information is available but not all,
     * such as having a country code but no carrier name, which can occur during
     * network transitions or with certain carrier configurations.
     */
    @Test
    fun `TelephonyCollector handles partial telephony information`() = runTest {
        every {
            mockContext.getSystemService(Context.TELEPHONY_SERVICE)
        } returns mockTelephonyManager
        every { mockTelephonyManager.networkOperatorName } returns "Carrier"
        every { mockTelephonyManager.networkCountryIso } returns null

        val result = TelephonyCollector.collect()

        assertEquals("Carrier", result?.carrierName)
        assertNull(result?.networkCountryIso)
    }
}