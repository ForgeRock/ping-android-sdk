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

class BuildTagsDetectorTest {
    private val context: Context = mockk()
    private val mockAndroidBuildTagProvider = mockk<AndroidBuildTagProvider>()
    private val buildTagsDetector = BuildTagsDetector(mockAndroidBuildTagProvider)

    @BeforeTest
    fun setup() {
        every { context.applicationContext } returns mockk()
        ContextProvider.init(context)
    }

    @Test
    fun `BuildTags detector checks for test keys in build tags`() = runTest {
        every { mockAndroidBuildTagProvider.getBuildTags() } returns TEST_KEYS
        val result = buildTagsDetector.analyze(context)
        assertEquals(1.0,result)
    }
}