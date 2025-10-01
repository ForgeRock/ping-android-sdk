/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.pingidentity.android.ContextProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [LocationCollector] to verify location data collection functionality.
 *
 * This test class covers various scenarios including:
 * - Successful location collection with granted permissions
 * - Error handling when location services fail
 * - Permission denial scenarios
 *
 * The tests use MockK to mock Android system services and Google Play Services
 * to isolate the location collection logic from platform dependencies.
 */
class LocationCollectorTest {
    private val mockContext = mockk<Context>()
    private val mockFusedLocationClient = mockk<FusedLocationProviderClient>()
    private val mockLocationTask = mockk<Task<Location>>()
    private val mockLocation = mockk<Location>()
    private val locationCollector = LocationCollector()

    /**
     * Sets up mock objects and stubs for each test case.
     *
     * Configures mocks for:
     * - Android Context and system services
     * - Google Play Services location client
     * - Coroutines Task extensions
     */
    @BeforeTest
    fun setUp() {
        mockkObject(ContextProvider)
        mockkStatic(LocationServices::class)
        // Mock the file containing the await() extension function
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")

        every { ContextProvider.context } returns mockContext
        every { LocationServices.getFusedLocationProviderClient(mockContext) } returns mockFusedLocationClient
        every { mockFusedLocationClient.lastLocation } returns mockLocationTask

        every { mockLocation.latitude } returns MOCK_LATITUDE
        every { mockLocation.longitude } returns MOCK_LONGITUDE
    }

    /**
     * Cleans up all mocks after each test to prevent interference between tests.
     */
    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Verifies that location collection succeeds when permissions are granted
     * and location data is available from the fused location provider.
     */
    @Test
    fun `getLocation returns LocationInfo when permission is granted and location is available`() = runTest {
            // Arrange
            every { mockContext.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
            coEvery { mockLocationTask.await() } returns mockLocation

            // Act
            val locationInfo = locationCollector.collect()

            // Assert
            assertEquals(MOCK_LATITUDE, locationInfo?.latitude)
            assertEquals(MOCK_LONGITUDE, locationInfo?.longitude)
        }

    /**
     * Verifies that the location collector uses the correct key identifier
     * and serializer for LocationInfo.
     */
    @Test
    fun `Location collector has correct key and serializer`() {
        assertEquals("location", locationCollector.key)
        assertEquals(LocationInfo.serializer(), locationCollector.serializer)
    }

    /**
     * Verifies that location collection returns null when permissions are granted
     * but the location task fails (e.g., location services disabled, GPS unavailable).
     */
    @Test
    fun `getLocation returns null when permission is granted but task fails`() = runTest {
        // Arrange
        every { mockContext.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
        coEvery { mockLocationTask.await() } throws SecurityException("Location disabled")

        // Act
        val locationInfo = locationCollector.collect()

        // Assert
        assertNull(locationInfo)
    }

    /**
     * Verifies that location collection returns null when both fine and coarse
     * location permissions are denied by the user.
     *
     * Note: This test doesn't cover the permission request flow since that
     * requires testing the activity interaction separately.
     */
    @Test
    fun `getLocation returns null when permission is denied`() = runTest {
        // Arrange
        every { mockContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns PackageManager.PERMISSION_DENIED
        every { mockContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) } returns PackageManager.PERMISSION_DENIED

        // Act
        val locationInfo = locationCollector.collect()

        // Assert
        assertNull(locationInfo)
    }

    companion object {
        private const val MOCK_LATITUDE = 49.2827
        private const val MOCK_LONGITUDE = -123.1207
    }
}