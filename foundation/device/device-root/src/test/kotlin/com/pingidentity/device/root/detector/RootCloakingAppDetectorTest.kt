/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import com.pingidentity.device.detector.RootCloakingAppDetector
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test class for [RootCloakingAppDetector].
 *
 * This class tests the functionality of the RootCloakingAppDetector which identifies root cloaking
 * applications that hide root status from other apps. The test verifies that the detector
 * returns the correct list of known root cloaking application package names.
 */
class RootCloakingAppDetectorTest {
    /**
     * Tests that RootCloakingAppDetector returns the correct known root cloaking application packages.
     *
     * This test verifies that the detector's package list matches the expected
     * comprehensive list of known root cloaking application package names.
     * The presence of any of these packages typically indicates sophisticated attempts
     * to hide device tampering.
     */
    @Test
    fun `RootCloakingAppDetector returns correct known packages`() {
        val detector = RootCloakingAppDetector()
        val packages = detector.getPackages()

        // Check that the list is not empty
        assert(packages.isNotEmpty())
        assertEquals(RootCloakingAppDetector.CURRENT_ROOT_CLOAKING_APPS, packages)
    }
}