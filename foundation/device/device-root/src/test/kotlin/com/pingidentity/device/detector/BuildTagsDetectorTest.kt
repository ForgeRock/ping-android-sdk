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
 * Test class for [BuildTagsDetector].
 *
 * This class tests the functionality of the BuildTagsDetector which analyzes Android build tags
 * to detect potential device tampering. Build tags like "test-keys" indicate that the device
 * firmware was signed with test keys instead of release keys, which is a common indicator
 * of custom ROMs or rooted devices.
 */
class BuildTagsDetectorTest {
    private val context: Context = mockk()
    private val mockAndroidBuildTagProvider = mockk<AndroidBuildTagProvider>()
    private val buildTagsDetector = BuildTagsDetector(mockAndroidBuildTagProvider)

    /**
     * Sets up the test environment by initializing the ContextProvider with a mocked context.
     * This ensures that the detector has access to the required Android context for build tag analysis.
     */
    @BeforeTest
    fun setup() {
        every { context.applicationContext } returns mockk()
        ContextProvider.init(context)
    }

    /**
     * Tests that BuildTagsDetector properly detects test keys in build tags.
     *
     * When build tags contain "test-keys", it indicates the device firmware was signed
     * with test keys instead of release keys. This is a strong indicator of device
     * tampering or custom ROM installation. The test expects a score of 1.0 when
     * test keys are detected.
     */
    @Test
    fun `BuildTags detector checks for test keys in build tags`() = runTest {
        every { mockAndroidBuildTagProvider.getBuildTags() } returns TEST_KEYS
        val result = buildTagsDetector.analyze(context)
        assertEquals(1.0,result)
    }
}