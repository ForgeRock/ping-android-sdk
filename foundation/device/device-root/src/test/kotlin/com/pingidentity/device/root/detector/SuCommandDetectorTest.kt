/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test class for [SuCommandDetector].
 *
 * This class tests the functionality of the SuCommandDetector which checks for the presence
 * of the `su` command to detect potential device rooting. The test verifies that the detector
 * correctly identifies and returns the expected command used for root access detection.
 */
class SuCommandDetectorTest {
    /**
     * Tests that SuCommandDetector returns the correct command.
     *
     * This test verifies that the detector correctly identifies and returns an array
     * containing only the "su" command, which is the primary indicator of root access
     * on Android devices.
     */
    @Test
    fun `SuCommandDetector returns correct command`() {
        val detector = SuCommandDetector
        val command = detector.getCommands()

        assert(command.isNotEmpty())
        assertEquals(SuCommandDetector.SU_COMMAND, command[0])
    }
}