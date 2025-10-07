/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test class for [RootProgramFileDetector].
 *
 * This class tests the functionality of the RootProgramFileDetector which checks for the presence
 * of known root-related program files on the device. The tests verify that the detector correctly
 * identifies the list of filenames it checks against.
 *
 * The test ensures that the detector's list of known root program files is accurate and up-to-date.
 */
class RootProgramFileDetectorTest {
    /**
     * Tests that RootProgramFileDetector returns the correct list of filenames.
     *
     * This test verifies that the detector's getFilenames method returns a non-empty list
     * and that it matches the current known set of root program files defined in the detector.
     * This ensures that the detector is configured to check for all relevant root binaries.
     */
    @Test
    fun `Test RootProgramFileDetector returns correct files`() {
        val detector = RootProgramFileDetector
        val files = detector.getFilenames()

        assert(files.isNotEmpty())
        assertEquals(RootProgramFileDetector.CURRENT_KNOWN_ROOT_PROGRAM_FILES, files)
    }
}