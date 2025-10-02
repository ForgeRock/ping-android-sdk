/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test class for [DangerousPropertyDetector].
 *
 * This class tests the functionality of the DangerousPropertyDetector which analyzes Android
 * system properties to detect potentially dangerous configurations. The tests verify that the detector
 * properly reads system properties and identifies specific property values that may indicate
 * security risks.
 *
 * The test uses a concrete implementation of DangerousPropertyDetector to verify its behavior
 * and property checking logic.
 */
class DangerousPropertyDetectorTest {
    /**
     * Tests that DangerousPropertyDetector returns the correct properties.
     *
     * This test verifies that the detector correctly identifies and returns a map of
     * dangerous properties, specifically checking for "ro.debuggable=1" and "ro.secure=0".
     * These properties are commonly associated with insecure device configurations.
     */
    @Test
    fun `DangerousPropertyDetector returns correct properties`() {
        val detector = DangerousPropertyDetector()
        val properties = detector.getProperties()

        assertEquals(2, properties.size)
        assert(properties["ro.debuggable"] == "1")
        assert(properties["ro.secure"] == "0")
    }
}