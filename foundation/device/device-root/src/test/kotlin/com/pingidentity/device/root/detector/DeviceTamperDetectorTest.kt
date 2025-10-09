/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.logger.WARN
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test class for TamperDetector implementations.
 *
 * This class tests the functionality of various TamperDetector implementations including
 * custom detectors created via the factory function. The tests verify that detectors
 * properly analyze device conditions and return expected confidence scores.
 *
 * The test uses mocking to simulate Android context and validate detector behavior
 * in isolation from actual device state.
 */
class DeviceTamperDetectorTest {
    private val mockContext = mockk<Context>()


    /**
     * Sets up the test environment by initializing the ContextProvider with a mocked context.
     * This ensures that the detectors have access to the required Android context for analysis.
     */
    @BeforeTest
    fun setup() {
        every { mockContext.applicationContext } returns mockContext
        ContextProvider.init(mockContext)
    }

    /**
     * Test implementation of TamperDetector for verifying abstract class behavior.
     *
     * This test detector always returns 0.0 indicating no tampering detected.
     * It is used to validate the analyze method and ensure proper integration
     * with the TamperDetector interface.
     */
    @Test
    fun `Test DeviceTamperDetector when created as an object`() = runTest {
        // Placeholder for actual tests
        val detector = object : TamperDetector {
            override suspend fun analyze(context: Context): Double {
                return 0.0
            }
            override var logger: Logger = Logger.WARN
        }
        assert(detector.analyze(mockContext) == 0.0)
    }

    /**
     * Tests that the TamperDetector factory function creates a detector with custom logic.
     *
     * This test verifies that a TamperDetector created via the factory function
     * correctly executes the provided lambda and returns the expected confidence score.
     */
    @Test
    fun `TamperDetector factory creates detector with custom logic`() = runTest {
        val detector = TamperDetector { 0.42 }
        val result = detector.analyze(mockContext)

        assertEquals(0.42, result)
    }
}