/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.profile.collector.LocationCollector
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

class LocationCollectorTest {
    private val mockContext = mockk<Context>()
    private val mockFusedLocationClient = mockk<FusedLocationProviderClient>()
    private val mockLocationTask = mockk<Task<Location>>()
    private val mockLocation = mockk<Location>()
    private val locationCollector = LocationCollector()

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

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

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