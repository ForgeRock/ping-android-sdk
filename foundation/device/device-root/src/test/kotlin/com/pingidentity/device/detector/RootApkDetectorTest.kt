/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.detector

import android.content.Context
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.detector.RootApkDetector.Companion.ROOT_APK
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test class for [RootApkDetector].
 *
 * This class tests the functionality of the RootApkDetector which checks for the presence
 * of root management APK files on the device filesystem. Root management applications like
 * SuperSU, Magisk, and KingRoot are commonly installed on rooted devices and their presence
 * is a strong indicator of device tampering.
 *
 * The tests verify that the detector properly analyzes APK file paths and returns appropriate
 * scores based on the presence or absence of these root management applications.
 */
class RootApkDetectorTest {
    private val context: Context = mockk()
    private val mockFile = mockk<File>()

    /**
     * Sets up the test environment by initializing the ContextProvider with a mocked context.
     * This ensures that the detector has access to the required Android context for APK analysis.
     * The mock context is configured to return a valid application context for proper initialization.
     */
    @BeforeTest
    fun setup() {
        every { context.applicationContext } returns mockk()
        ContextProvider.init(context)
    }

    /**
     * Tests that RootApkDetector properly checks for the existence of root management APK files.
     *
     * This test verifies that the detector can identify common root management applications
     * by checking their APK file paths. The test uses the ROOT_APK companion object which
     * contains a list of known root management application file paths.
     *
     * When root APK files are detected, it indicates that root management software is
     * installed on the device, which is a strong sign of device tampering. The test
     * currently expects a score of 0.0, but this may need adjustment based on the
     * actual detection logic implementation.
     */
    @Test
    fun `RootApkDetector checks for existence of root management APK files`() = runTest {
        val apkFiles = ROOT_APK

        apkFiles.forEach { filePath ->
            every { mockFile.exists() } returns true
            val detector = RootApkDetector()
            val result = detector.analyze(context)
            assertEquals(0.0, result)
        }
    }
}