/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test class for [RootAppDetector].
 *
 * This class tests the functionality of the RootAppDetector which identifies device rooting
 * by checking for known root management applications. The test verifies that the detector
 * returns the correct list of known root application package names.
 */
class RootAppDetectorTest {
    /**
     * Tests that RootAppDetector returns the correct known root application packages.
     *
     * This test verifies that the detector's package list matches the expected
     * comprehensive list of known root management application package names.
     * The presence of any of these packages typically indicates the device has been rooted.
     */
    @Test
    fun `RootAppDetector returns correct known packages`() {
        val detector = RootAppDetector
        val packages = detector.getPackages()

        // Check that the list is not empty
        assert(packages.isNotEmpty())
        assertEquals(RootAppDetector.CURRENT_KNOWN_ROOT_APPS, packages)
    }
}