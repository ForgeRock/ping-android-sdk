/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.detector

import android.content.Context
import com.pingidentity.android.ContextProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test class for [CommandDetector] abstract class functionality.
 *
 * This test suite validates the command execution detection logic used for identifying
 * suspicious system commands that might indicate device tampering, root access, or
 * security modifications. Uses mocked Runtime to simulate command execution scenarios.
 */
class CommandDetectorTest {
    private val context: Context = mockk()
    private val mockRuntime: Runtime = mockk()

    /**
     * Sets up the test environment by initializing the ContextProvider with a mocked context.
     * This ensures all tests have access to the required Android context for command detection.
     */
    @BeforeTest
    fun setup() {
        every { context.applicationContext } returns mockk()
        ContextProvider.init(context)
    }

    /**
     * Tests that CommandDetector returns a score of 1.0 when suspicious commands are detected.
     *
     * This test verifies the positive detection case where suspicious system commands
     * (like su, busybox, magisk) can be executed, indicating potential device tampering.
     */
    @Test
    fun `CommandDetector detects presence of suspicious commands`() = runTest {
        every { mockRuntime.exec(any<Array<String>>()) } returns mockk()

        val testDetector = object : CommandDetector() {
            override fun getCommands(): Array<String> {
                return arrayOf("su", "busybox", "magisk")
            }
        }
        val result = testDetector.analyze(context)

        assertEquals(1.0, result)
    }

    /**
     * Tests that CommandDetector handles exceptions gracefully when command execution fails.
     *
     * This test verifies that when Runtime.exec() returns null or fails, the detector
     * returns a score of 0.0, indicating no tampering detected due to execution failure.
     */
    @Test
    fun `CommandDetector throws exception`() = runTest {
        every { mockRuntime.exec(any<Array<String>>()) } returns null

        val testDetector = object : CommandDetector() {
            override fun getCommands(): Array<String> {
                return arrayOf("nonexistentcommand1", "nonexistentcommand2")
            }
        }
        val result = testDetector.analyze(context)

        assertEquals(0.0, result)
    }

    /**
     * Tests that CommandDetector returns a score of 0.0 when no suspicious commands are found.
     *
     * This test verifies the negative detection case where nonexistent commands cannot
     * be executed, indicating the device appears clean of tampering indicators.
     */
    @Test
    fun `CommandDetector does not detect suspicious commands`() = runTest {
        every { mockRuntime.exec(any<Array<String>>()) } returns mockk()

        val testDetector = object : CommandDetector() {
            override fun getCommands(): Array<String> {
                return arrayOf("nonexistentcommand1", "nonexistentcommand2")
            }
        }
        val result = testDetector.analyze(context)

        assertEquals(0.0, result)
    }

    /**
     * Tests that SuCommandDetector properly detects the presence of su command.
     *
     * This test specifically validates the SuCommandDetector implementation which checks
     * for the 'su' (switch user) command that is commonly used for gaining root access.
     * When the su command is available and executable, it indicates potential device rooting.
     */
    @Test
    fun `SuCommandDetector detects su command` () = runTest {
        every { mockRuntime.exec(any<Array<String>>()) } returns mockk()

        val suDetector = SuCommandDetector()
        val result = suDetector.analyze(context)

        assertEquals(1.0, result)
    }
}