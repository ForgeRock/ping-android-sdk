/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.util

import android.content.Context
import com.pingidentity.logger.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeviceUtilsTest {

    private lateinit var mockContext: Context
    private lateinit var mockLogger: Logger
    private lateinit var mockSettingsProvider: PlatformSettingsProvider
    private lateinit var mockBuildInfoProvider: PlatformBuildInfoProvider

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockSettingsProvider = mockk(relaxed = true)
        mockBuildInfoProvider = mockk(relaxed = true)

        // Inject our mock providers into DeviceUtils
        DeviceUtils.setProviders(mockSettingsProvider, mockBuildInfoProvider)

        // Reset to default logger before each test
        DeviceUtils.setLogger(Logger.logger)
    }

    @After
    fun tearDown() {
        // Reset to default providers after each test
        DeviceUtils.resetProviders()
    }

    @Test
    fun `getDeviceName should return name from Settings when available`() {
        // Given
        val expectedDeviceName = "Test Device"
        every {
            mockSettingsProvider.getDeviceNameFromSettings(mockContext)
        } returns expectedDeviceName

        // When
        val result = DeviceUtils.getDeviceName(mockContext)

        // Then
        assertEquals(expectedDeviceName, result)
    }

    @Test
    fun `getDeviceName should use Build info when Settings returns null`() {
        // Given
        every {
            mockSettingsProvider.getDeviceNameFromSettings(mockContext)
        } returns null

        // Mock Build properties
        every { mockBuildInfoProvider.getManufacturer() } returns "samsung"
        every { mockBuildInfoProvider.getModel() } returns "Galaxy S21"

        // When
        val result = DeviceUtils.getDeviceName(mockContext)

        // Then
        assertEquals("Samsung Galaxy S21", result)
    }

    @Test
    fun `getDeviceName should capitalize manufacturer when model doesn't include it`() {
        // Given
        every {
            mockSettingsProvider.getDeviceNameFromSettings(mockContext)
        } returns null

        // Mock Build properties
        every { mockBuildInfoProvider.getManufacturer() } returns "google"
        every { mockBuildInfoProvider.getModel() } returns "Pixel 6"

        // When
        val result = DeviceUtils.getDeviceName(mockContext)

        // Then
        assertEquals("Google Pixel 6", result)
    }

    @Test
    fun `getDeviceName should not duplicate manufacturer when model starts with it`() {
        // Given
        every {
            mockSettingsProvider.getDeviceNameFromSettings(mockContext)
        } returns null

        // Mock Build properties
        every { mockBuildInfoProvider.getManufacturer() } returns "oneplus"
        every { mockBuildInfoProvider.getModel() } returns "OnePlus 9 Pro"

        // When
        val result = DeviceUtils.getDeviceName(mockContext)

        // Then
        assertEquals("OnePlus 9 Pro", result)
    }

    @Test
    fun `getDeviceName should return default name when both methods fail`() {
        // Given
        every {
            mockSettingsProvider.getDeviceNameFromSettings(mockContext)
        } returns null

        // Mock Build to throw exception
        every { mockBuildInfoProvider.getManufacturer() } throws RuntimeException("Test exception")

        // When
        val result = DeviceUtils.getDeviceName(mockContext)

        // Then
        assertEquals("Unknown Android Device", result)
    }

    @Test
    fun `setLogger should use custom logger for logging errors`() {
        // Given
        DeviceUtils.setLogger(mockLogger)
        val messageSlot = slot<String>()
        every { mockLogger.w(capture(messageSlot)) } returns Unit

        every {
            mockSettingsProvider.getDeviceNameFromSettings(mockContext)
        } throws RuntimeException("Settings error")

        // When
        DeviceUtils.getDeviceName(mockContext)

        // Then
        verify { mockLogger.w(any()) }
        assert(messageSlot.captured.contains("Error getting device name from Settings"))
    }

    @Test
    fun `capitalize should handle empty string`() {
        assertEquals("", DeviceUtils.capitalize(""))
    }

    @Test
    fun `capitalize should not change already capitalized string`() {
        assertEquals("Google", DeviceUtils.capitalize("Google"))
    }

    @Test
    fun `capitalize should capitalize first letter`() {
        assertEquals("Google", DeviceUtils.capitalize("google"))
    }
}