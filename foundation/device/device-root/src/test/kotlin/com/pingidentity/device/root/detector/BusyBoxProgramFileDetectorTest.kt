/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test class for [BusyBoxProgramFileDetector].
 *
 * This class tests the functionality of the BusyBoxProgramFileDetector which checks
 * for the presence of the BusyBox binary on the device filesystem as an indicator
 * of potential rooting or tampering. The tests verify that the detector correctly
 * identifies the expected filename used to detect BusyBox installations.
 *
 * The test ensures that the detector's filename list matches the known BusyBox
 * binary name, which is crucial for accurate detection during analysis.
 *
 * @see BusyBoxProgramFileDetector
 */
class BusyBoxProgramFileDetectorTest {
    /**
     * Tests that BusyBoxProgramFileDetector returns the expected filename list.
     *
     * This test verifies that the detector correctly provides the list of filenames
     * it checks for when analyzing the device filesystem. The expected filename is
     * "busybox", which is the standard name of the BusyBox binary commonly found
     * on rooted devices.
     */
    @Test
    fun `BusyBoxProgramReturns expected file names`() {
        val detector = BusyBoxProgramFileDetector
        val expectedFileNames = BusyBoxProgramFileDetector.BUSYBOX_PROGRAM_FILE_DETECTOR_NAME

        assertEquals(1, detector.getFilenames().size)
        assert(detector.getFilenames() == listOf(expectedFileNames))
    }
}