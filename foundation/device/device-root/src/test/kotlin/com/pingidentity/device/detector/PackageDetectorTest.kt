/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.detector

import android.content.Context
import android.content.pm.PackageManager
import com.pingidentity.android.ContextProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test class for [PackageDetector] abstract class functionality.
 *
 * This test suite validates the package detection logic used for identifying
 * suspicious applications that might indicate device tampering or security risks.
 * Uses mocked Android components to simulate various package presence scenarios.
 */
class PackageDetectorTest {
    private val context: Context = mockk()

    /**
     * Sets up the test environment by initializing the ContextProvider with a mocked context.
     * This ensures all tests have access to the required Android context for package detection.
     */
    @BeforeTest
    fun setup() {
        every { context.applicationContext } returns mockk()
        ContextProvider.init(context)
    }

    /**
     * Tests that PackageDetector returns a score of 1.0 when suspicious packages are detected.
     *
     * This test verifies the positive detection case where the device has packages
     * that are considered indicators of potential tampering or security risks.
     */
    @Test
    fun `PackageDetector detects presence of suspicious packages`() = runTest {
        mockPackageManager(returnPackage = true)

        val testDetector = object : PackageDetector() {
            override fun getPackages(): List<String> {
                return listOf("com.example.suspicious", "com.example.another")
            }
        }

        val result = testDetector.analyze(context)
        assertEquals(1.0, result)
    }

    /**
     * Tests that PackageDetector returns a score of 0.0 when no suspicious packages are found.
     *
     * This test verifies the negative detection case where the device appears clean
     * with no packages that indicate potential tampering or security risks.
     */
    @Test
    fun `Test PackageDetector when no suspicious packages are found`() = runTest {
        mockPackageManager(returnPackage = false)
        val testDetector = object : PackageDetector() {
            override fun getPackages(): List<String> {
                return listOf("com.example.suspicious", "com.example.another")
            }
        }
        val result = testDetector.analyze(context)
        assertEquals(0.0, result)
    }


    /**
     * Helper method to mock the Android PackageManager for testing purposes.
     *
     * @param returnPackage If true, mocks successful package detection; if false,
     *                     throws NameNotFoundException to simulate package absence
     */
    private fun mockPackageManager(
        returnPackage: Boolean = false,
    ) {
        val packageManager = mockk<PackageManager>()
        every { context.packageManager } returns packageManager
        every { context.applicationContext.packageManager } returns packageManager
        every { ContextProvider.context.packageManager } returns packageManager
        if (returnPackage) {
            every {
                packageManager.getPackageInfo(any<String>(), any<Int>())
            } returns mockk()
            return
        } else {
            every {
                packageManager.getPackageInfo(any<String>(), any<Int>())
            } throws PackageManager.NameNotFoundException()
        }
    }
}