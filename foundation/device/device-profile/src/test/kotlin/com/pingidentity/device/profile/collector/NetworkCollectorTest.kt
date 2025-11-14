/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.pingidentity.android.ContextProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [NetworkCollector] to verify network connectivity detection functionality.
 *
 * This test class covers various scenarios including:
 * - Network connectivity detection for different Android API levels
 * - Modern NetworkCapabilities API (API 29+) testing
 * - Legacy NetworkInfo API (API 28 and below) testing
 * - Edge cases like null network objects and missing capabilities
 *
 * The tests use MockK to mock Android system services and SDK version provider
 * to isolate the network detection logic from platform dependencies.
 */
class NetworkCollectorTest {
    private val mockContext = mockk<Context>()
    private val mockConnectivityManager = mockk<ConnectivityManager>()
    private val mockNetwork = mockk<Network>()
    private val mockNetworkCapabilities = mockk<NetworkCapabilities>()

    /**
     * Sets up mock objects and stubs for each test case.
     *
     * Configures mocks for:
     * - Android Context and system services
     * - SDK version provider for API level testing
     */
    @BeforeTest
    fun setUp() {
        mockkObject(ContextProvider)
        every { ContextProvider.context } returns mockContext
        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager
    }

    /**
     * Cleans up all mocks after each test to prevent interference between tests.
     */
    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Verifies that the network collector uses the correct key identifier.
     */
    @Test
    fun `Network collector has correct key`() {
        val networkCollector = NetworkCollector()
        assertEquals("network", networkCollector.key)
    }

    /**
     * Verifies that the network collector uses the correct serializer for NetworkInfo.
     */
    @Test
    fun `Network collector has correct serializer`() {
        val networkCollector = NetworkCollector()
        assertEquals(
            NetworkInfo.serializer(),
            networkCollector.serializer,
        )
    }

    /**
     * Verifies network connectivity detection using modern NetworkCapabilities API.
     * Tests the case where the device has internet capability.
     */
    @Test
    fun `Network is connected when NetworkCapabilities has internet capability`() = runTest {
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockNetworkCapabilities
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true

        val networkCollector = NetworkCollector()
        val result = networkCollector.collect()

        assertTrue(result.connected)
    }

    /**
     * Verifies network connectivity detection using modern NetworkCapabilities API.
     * Tests the case where the device lacks internet capability.
     */
    @Test
    fun `Network is not connected when NetworkCapabilities lacks internet capability`() = runTest {
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockNetworkCapabilities
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false

        val networkCollector = NetworkCollector()
        val result = networkCollector.collect()

        assertFalse(result.connected)
    }

    /**
     * Verifies network connectivity detection when no active network is available.
     * Tests the edge case where ConnectivityManager.activeNetwork returns null.
     */
    @Test
    fun `Network is not connected when activeNetwork is null`() = runTest {
        every { mockConnectivityManager.activeNetwork } returns null
        every { mockConnectivityManager.getNetworkCapabilities(null) } returns mockNetworkCapabilities
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false

        val networkCollector = NetworkCollector()
        val result = networkCollector.collect()

        assertFalse(result.connected)
    }

    /**
     * Verifies network connectivity detection when NetworkCapabilities is unavailable.
     * Tests the edge case where getNetworkCapabilities returns null.
     */
    @Test
    fun `Network is not connected when NetworkCapabilities is null`() = runTest {
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns null

        val networkCollector = NetworkCollector()
        val result = networkCollector.collect()

        assertFalse(result.connected)
    }
}