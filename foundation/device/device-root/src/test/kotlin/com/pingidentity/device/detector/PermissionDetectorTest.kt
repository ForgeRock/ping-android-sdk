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
 * Test class for [PermissionDetector].
 *
 * This class tests the functionality of the PermissionDetector which analyzes mount permissions
 * to detect potential device tampering. The tests verify behavior across different Android SDK versions.
 */
class PermissionDetectorTest {
    private val context: Context = mockk()
    private val mockAndroidBuildSdkProvider = mockk<AndroidBuildSdkProvider>()

    /**
     * Sets up the test environment by initializing the ContextProvider with a mocked context.
     * This ensures that the detector has access to the required Android context for permission analysis.
     */
    @BeforeTest
    fun setup() {
        every { context.applicationContext } returns mockk()
        ContextProvider.init(context)
    }

    /**
     * Tests that PermissionDetector properly analyzes mount permissions on Android M (API 23) and above.
     *
     * On newer Android versions, the permission model changed and mount permission checking
     * should return a score of 0.0 indicating no tampering detected through this method.
     */
    @Test
    fun `PermissionDetector analyzes mount permissions when Build version is M or above`() = runTest {
        every { mockAndroidBuildSdkProvider.getSdkVersion() } returns 23 // Build.VERSION_CODES.M
        val permissionDetector = PermissionDetector(mockAndroidBuildSdkProvider)

        val result = permissionDetector.analyze(context)

        assertEquals(0.0, result)
    }

    /**
     * Tests that PermissionDetector properly analyzes mount permissions on Android L (API 22) and below.
     *
     * On older Android versions, mount permission analysis follows a different code path.
     * The test verifies that the detector returns 0.0 when no suspicious permissions are found.
     */
    @Test
    fun `PermissionDetector analyzes mount permissions when Build version is L or below`() = runTest {
        every { mockAndroidBuildSdkProvider.getSdkVersion() } returns 22 // Build.VERSION_CODES.LOLLIPOP_MR1
        val permissionDetector = PermissionDetector(mockAndroidBuildSdkProvider)

        val result = permissionDetector.analyze(context)

        assertEquals(0.0, result)
    }
}