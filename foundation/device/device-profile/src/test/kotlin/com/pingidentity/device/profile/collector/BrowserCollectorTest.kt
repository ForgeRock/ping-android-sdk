/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.content.Context
import android.webkit.WebSettings
import com.pingidentity.android.ContextProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Comprehensive test suite for [BrowserCollector] functionality.
 *
 * This test class validates the browser and WebView user agent collection capabilities
 * of the BrowserCollector, ensuring accurate capture of browser-related device information
 * used for device fingerprinting and compatibility detection.
 *
 * **Test Coverage:**
 * - Collector key validation for proper identification
 * - User agent string collection from WebView system
 * - Mock-based testing for consistent cross-environment results
 * - WebSettings static method mocking and interaction
 * - Data structure validation and content verification
 *
 * **Testing Strategy:**
 * The tests use comprehensive mocking to simulate Android WebView behaviors:
 * - Mock Context for system service and WebView access
 * - Static mocking of WebSettings class for user agent retrieval
 * - ContextProvider initialization for global context management
 * - Controlled user agent string responses for predictable testing
 *
 * This approach ensures reliable test execution regardless of the actual device's
 * WebView implementation or version, allowing validation of user agent collection
 * across different Android versions and WebView configurations.
 *
 * **User Agent Significance:**
 * User agent strings contain valuable device fingerprinting information including:
 * - Android version and build details
 * - Device model and manufacturer information
 * - WebView/Chrome version and capabilities
 * - System architecture and platform details
 * - Locale and regional configuration
 *

 * @see BrowserCollector
 * @see BrowserData
 * @see WebSettings
 */
class BrowserCollectorTest {
    /**
     * Mock Android context used for simulating WebView and system service access.
     * Provides controlled environment for testing browser information collection.
     */
    private val mockContext = mockk<Context>()

    /**
     * Sets up the test environment before each test execution.
     *
     * Configures:
     * - Mock Android context with proper application context chain
     * - ContextProvider initialization for global context access
     * - Static mocking of WebSettings class for user agent method interception
     * - Clean testing environment for WebView-related operations
     *
     * The static mocking of WebSettings is essential because getDefaultUserAgent()
     * is a static method that requires system-level WebView initialization,
     * which is not available in unit test environments.
     */
    @BeforeTest
    fun setUp() {
        every { mockContext.applicationContext } returns mockContext
        ContextProvider.init(mockContext)
        mockkStatic(WebSettings::class)
    }

    /**
     * Cleans up test environment and removes all mocks after each test.
     *
     * This cleanup ensures:
     * - Static mocks are properly released to prevent interference
     * - Memory leaks from mock objects are avoided
     * - Each test starts with a clean mocking environment
     * - No cross-test contamination of mock configurations
     */
    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Validates that the BrowserCollector uses the correct identification key.
     *
     * This test ensures the collector is properly identified within the device
     * profiling system with the key "browser". This key is used for:
     * - JSON serialization and deserialization
     * - Collector lookup and management
     * - Device profile organization and structure
     * - API consistency across the profiling system
     */
    @Test
    fun `BrowserCollector has correct key`() {
        assertEquals("browser", BrowserCollector.key)
    }

    /**
     * Tests browser information collection with mock user agent data.
     *
     * This test validates the complete browser information collection process:
     * - Configures WebSettings.getDefaultUserAgent() to return predictable test data
     * - Executes the BrowserCollector.collect() method
     * - Verifies successful collection without exceptions or errors
     * - Validates that the returned BrowserData contains the expected user agent string
     * - Confirms proper data structure creation and population
     *
     * The test uses "MockUserAgent/1.0" as a controlled user agent string to ensure
     * predictable results while testing the collection mechanism. This simulates
     * the real-world scenario where WebSettings provides device-specific user agent
     * information for browser fingerprinting purposes.
     *
     * **Real-world User Agent Example:**
     * ```
     * Mozilla/5.0 (Linux; Android 12; sdk_gphone64_x86_64 Build/SPB5.210812.003; wv)
     * AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Mobile Safari/537.36
     * ```
     */
    @Test
    fun `BrowserCollector collects browser information`() = runTest {
        every { WebSettings.getDefaultUserAgent(mockContext) } returns "MockUserAgent/1.0"

        val browserInfo = BrowserCollector.collect()
        assertNotNull(browserInfo)
        assertEquals("MockUserAgent/1.0", browserInfo.userAgent)
    }
}