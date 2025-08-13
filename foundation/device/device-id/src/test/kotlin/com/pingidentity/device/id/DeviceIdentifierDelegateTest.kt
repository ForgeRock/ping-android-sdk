/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.id

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DeviceIdentifierDelegateTest {

    private lateinit var mockDeviceIdentifier: DeviceIdentifier
    private lateinit var delegate: DeviceIdentifierDelegate
    private val testId = "test-device-id-12345"

    @Before
    fun setUp() {
        // Create a mock DeviceIdentifier
        mockDeviceIdentifier = mockk<DeviceIdentifier>()
        // Set up the mock to return our test ID
        coEvery { mockDeviceIdentifier.id() } returns testId

        // Create the delegate with our mock
        delegate = DeviceIdentifierDelegate(mockDeviceIdentifier)
    }

    @Test
    fun `id should return delegated value on first call`() = runTest {
        // Call the id function
        val result = delegate.id()

        // Verify the result is correct
        assertEquals(testId, result)

        // Verify that the delegate's id function was called exactly once
        coVerify(exactly = 1) { mockDeviceIdentifier.id() }
    }

    @Test
    fun `id should return cached value on subsequent calls`() = runTest {
        // First call to id should delegate
        val firstResult = delegate.id()
        assertEquals(testId, firstResult)

        // Second call should use cached value
        val secondResult = delegate.id()
        assertEquals(testId, secondResult)

        // Verify delegate was only called once despite making two calls
        coVerify(exactly = 1) { mockDeviceIdentifier.id() }
    }

    @Test
    fun `multiple threads should get same cached value`() = runTest {
        // Simulate multiple calls from different "threads"
        val results = List(5) {
            delegate.id()
        }

        // All results should match the test ID
        results.forEach { assertEquals(testId, it) }

        // The delegate should have been called exactly once
        coVerify(exactly = 1) { mockDeviceIdentifier.id() }
    }
}
