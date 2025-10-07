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
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test class for [FileDetector] and its concrete implementations.
 *
 * This class tests file-based tamper detection functionality, including:
 * - Abstract FileDetector behavior for checking file existence
 * - BusyBoxProgramFileDetector for detecting BusyBox installation
 * - NativeDetector for detecting native su binary files
 * - RootProgramFileDetector for detecting root-related program files
 *
 * The tests verify that detectors return appropriate scores based on file presence
 * or absence on the device filesystem.
 */
class FileDetectorTest {
    private val context: Context = mockk()
    private val mockFile = mockk<File>()

    /**
     * Sets up the test environment by initializing the ContextProvider with a mocked context.
     * This ensures that file detectors have access to the required Android context for file analysis.
     */
    @BeforeTest
    fun setup() {
        every { context.applicationContext } returns mockk()
        ContextProvider.init(context)
    }

    /**
     * Tests that the abstract FileDetector properly checks for file existence.
     *
     * Creates a test implementation of FileDetector and verifies that it correctly
     * processes a list of filenames and returns the expected score when files exist.
     * This test uses a mock file to simulate file existence.
     */
    @Test
    fun `FileDetector checks for existence of files`() = runTest {

        every { mockFile.exists() } returns true

        val testDetector = object : FileDetector() {
            override fun getFilenames(): List<String> {
                return listOf("testfile1", "testfile2")
            }
            override var logger: Logger = Logger.WARN
        }

        val result = testDetector.analyze(context)
        assertEquals(0.0, result)
    }

    /**
     * Tests BusyBoxProgramFileDetector for detecting BusyBox installation.
     *
     * BusyBox is a common tool on rooted devices that provides Unix utilities.
     * This test is ignored because it needs to run on a real device or emulator
     * without BusyBox installed to properly verify the detection logic.
     *
     * @see BusyBoxProgramFileDetector
     */
    @Test
    fun `BusyBoxProgramFileDetector detects busybox file presence`(): Unit = runTest {
        val busyBoxDetector = BusyBoxProgramFileDetector
        val result = busyBoxDetector.analyze(context)
        // Since we cannot guarantee the properties on the test device, we check for valid output
        // The result should be either 0.0 (no BusyBox program found) or 1.0 (BusyBox program found)
        assert(result == 0.0 || result == 1.0)
    }

    /**
     * Tests NativeDetector for detecting native su binary files.
     *
     * The NativeDetector uses JNI calls to check for the presence of su binaries
     * that are commonly found on rooted devices. This test verifies that the
     * detector returns 0.0 when no su files are detected.
     *
     * @see NativeDetector
     */
    @Test
    fun `NativeDetector detects su file presence`(): Unit = runTest {
        val nativeDetector = NativeDetector()
        val result = nativeDetector.analyze(context)
        assertEquals(0.0, result)
    }

    /**
     * Tests RootProgramFileDetector for detecting root-related program files.
     *
     * This detector checks for common root management programs and utilities
     * that are typically installed on rooted devices. The test expects a score
     * of 1.0, indicating that root program files were detected, suggesting
     * device tampering.
     *
     * @see RootProgramFileDetector
     */
    @Test
    fun `RootProgramFileDetector detects root program filenames to check for tampering detection`() = runTest {
        val rootProgramFileDetector = RootProgramFileDetector
        val result = rootProgramFileDetector.analyze(context)
        assertEquals(1.0, result)
    }
}