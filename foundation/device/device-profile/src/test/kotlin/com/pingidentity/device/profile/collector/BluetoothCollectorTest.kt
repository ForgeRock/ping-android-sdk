/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.bluetooth.BluetoothManager
import android.content.Context
import com.pingidentity.android.ContextProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for [BluetoothCollector] functionality.
 *
 * This test class validates the Bluetooth hardware detection capabilities of the BluetoothCollector,
 * ensuring accurate detection of Bluetooth support across different device configurations and
 * system states.
 *
 * **Test Coverage:**
 * - Collector key validation and consistency
 * - Bluetooth adapter presence detection
 * - Graceful handling of devices without Bluetooth hardware
 * - Mock-based testing for consistent results across environments
 * - Error handling and edge case scenarios
 *
 * **Testing Strategy:**
 * The tests use comprehensive mocking to simulate various Android system behaviors:
 * - Mock Context for system service access
 * - Mock BluetoothManager for hardware service simulation
 * - Mock BluetoothAdapter for hardware capability testing
 * - ContextProvider initialization for global context access
 *
 * This approach ensures consistent test results regardless of the actual device hardware
 * running the tests, allowing validation of both supported and unsupported scenarios.
 *

 * @see BluetoothCollector
 * @see BluetoothData
 */
class BluetoothCollectorTest {
    /**
     * Mock Android context used for simulating system service access.
     * Provides controlled environment for testing Bluetooth service interactions.
     */
    private val mockContext = mockk<Context>()

    /**
     * Mock BluetoothManager used for simulating Android Bluetooth system service.
     * Allows testing of various Bluetooth hardware availability scenarios.
     */
    private val mockBluetoothManager = mockk<BluetoothManager>()

    /**
     * Sets up the test environment before each test execution.
     *
     * Configures:
     * - Mock Android context with proper application context chain
     * - ContextProvider initialization for global context access
     * - BluetoothManager system service mock configuration
     * - Default system service behavior for Bluetooth operations
     *
     * This setup ensures each test has a clean, controlled environment
     * for testing Bluetooth detection functionality.
     */
    @BeforeTest
    fun setUp() {
        every { mockContext.applicationContext } returns mockContext
        ContextProvider.init(mockContext)
        every {
            mockContext.getSystemService(Context.BLUETOOTH_SERVICE)
        } returns mockBluetoothManager
    }

    /**
     * Validates that the BluetoothCollector uses the correct identification key.
     *
     * This test ensures the collector is properly identified within the device
     * profiling system and can be referenced consistently across the codebase.
     * The key "bluetooth" is used for JSON serialization and collector lookup.
     */
    @Test
    fun `BluetoothCollector has correct key`() {
        assertEquals("bluetooth", BluetoothCollector.key)
    }

    /**
     * Tests Bluetooth information collection when hardware support is available.
     *
     * This test validates the collector's behavior on devices with Bluetooth hardware:
     * - Simulates presence of BluetoothAdapter through mocking
     * - Verifies successful collection of Bluetooth capability information
     * - Confirms that supported flag is correctly set to true
     * - Ensures proper data structure creation and population
     *
     * The test uses a non-null BluetoothAdapter mock to simulate devices
     * with Bluetooth hardware capability.
     */
    @Test
    fun `BluetoothCollector collects bluetooth information`() = runTest {
        every { mockBluetoothManager.adapter } returns mockk() // Simulate Bluetooth adapter present
        val bluetoothInfo = BluetoothCollector.collect()
        assertNotNull(bluetoothInfo)
        assertTrue(bluetoothInfo.supported)
    }

    /**
     * Tests graceful handling of devices without Bluetooth hardware support.
     *
     * This test validates the collector's behavior on devices lacking Bluetooth:
     * - Simulates absence of BluetoothAdapter (null return value)
     * - Verifies graceful handling without exceptions or crashes
     * - Confirms that supported flag is correctly set to false
     * - Ensures proper fallback behavior for unsupported hardware
     *
     * This scenario is common on certain device types (tablets without Bluetooth,
     * embedded devices, or devices with disabled Bluetooth hardware).
     */
    @Test
    fun `BluetoothCollector handles absence of Bluetooth adapter`() = runTest {
        every { mockBluetoothManager.adapter } returns null // Simulate no Bluetooth adapter
        val bluetoothInfo = BluetoothCollector.collect()
        assertNotNull(bluetoothInfo)
        assertFalse(bluetoothInfo.supported)
    }
}