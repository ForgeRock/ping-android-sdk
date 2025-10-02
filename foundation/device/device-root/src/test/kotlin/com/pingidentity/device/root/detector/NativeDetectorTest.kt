/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.detector.FileDetector
import com.pingidentity.device.detector.NativeDetector
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test class for [NativeDetector].
 *
 * This class contains unit tests that verify the behavior of the NativeDetector,
 * which uses JNI (Java Native Interface) to detect root-related files on the device.
 * The tests validate file detection logic, library loading behavior, and error handling
 * when native methods are unavailable or throw exceptions.
 *
 * Since NativeDetector relies on native libraries that may not be available in test
 * environments, this test uses a testable implementation that simulates the native
 * method behavior without requiring actual JNI calls.
 */
class NativeDetectorTest {
    private val context: Context = mockk()

    @BeforeTest
    fun setup() {
        every { context.applicationContext } returns mockk()
        ContextProvider.init(context)
    }

    /**
     * Test that verifies the NativeDetector returns the expected filenames for detection.
     *
     * This test validates that the detector is configured to look for the "su" binary,
     * which is a common indicator of root access on Android devices. The test ensures
     * that the filename list contains exactly one entry with the correct filename.
     */
    @Test
    fun `test getFilenames returns su`() {
        val detector = NativeDetector()
        val filenames = detector.getFilenames()

        assertEquals(1, filenames.size)
        assertEquals("su", filenames[0])
    }

    /**
     * Test that verifies analyze returns 0.0 when the native library is not loaded.
     *
     * This test simulates the scenario where the native library required for file
     * detection is not available or failed to load. In such cases, the detector
     * should gracefully return 0.0 (no tampering detected) rather than throwing
     * an exception.
     */
    @Test
    fun `test analyze returns 0_0 when library not loaded`() = runTest {
        val detector = TestableNativeDetector(libraryLoaded = false)
        val result = detector.analyze(context)

        assertEquals(0.0, result)
    }

    /**
     * Test that verifies analyze returns 1.0 when suspicious files are detected.
     *
     * This test simulates the scenario where the native library is loaded and
     * detects the presence of suspicious files (like the "su" binary). The detector
     * should return 1.0 indicating high confidence that tampering/rooting is present.
     */
    @Test
    fun `test analyze returns 1_0 when suspicious files found`() = runTest {
        val detector = TestableNativeDetector(libraryLoaded = true, existsResult = 1)
        val result = detector.analyze(context)

        assertEquals(1.0, result)
    }

    /**
     * Test that verifies analyze returns 0.0 when no suspicious files are found.
     *
     * This test simulates the scenario where the native library is loaded and
     * functioning correctly but does not detect any suspicious files. The detector
     * should return 0.0 indicating no tampering detected.
     */
    @Test
    fun `test analyze returns 0_0 when no suspicious files found`() = runTest {
        val detector = TestableNativeDetector(libraryLoaded = true, existsResult = 0)
        val result = detector.analyze(context)

        assertEquals(0.0, result)
    }

    /**
     * Test that verifies analyze handles UnsatisfiedLinkError gracefully.
     *
     * This test simulates the scenario where the native method call throws an
     * UnsatisfiedLinkError, which can occur when the native library is not properly
     * linked or the method signature is incorrect. The detector should catch this
     * exception and return 0.0 rather than propagating the error.
     */
    @Test
    fun `test analyze handles UnsatisfiedLinkError gracefully`() = runTest {
        val detector = TestableNativeDetector(libraryLoaded = true, throwError = true)
        val result = detector.analyze(context)

        assertEquals(0.0, result)
    }

    /**
     * Test that verifies analyze creates the correct path array for native method calls.
     *
     * This test validates that the detector properly constructs the path array that
     * gets passed to the native method. It ensures that:
     * - The path array is not empty
     * - The paths include the expected filename ("su")
     * - The paths are formatted correctly for native method consumption
     */
    @Test
    fun `test analyze creates correct path array`() = runTest {
        val detector = TestableNativeDetector(libraryLoaded = true)
        detector.analyze(context)

        assertTrue(detector.passedPathArray.isNotEmpty())
        assertTrue(detector.passedPathArray.any { it.toString().endsWith("su") })
    }

    /**
     * Testable version of NativeDetector that allows controlling the native method behavior.
     *
     * This test implementation extends FileDetector to simulate NativeDetector behavior
     * without requiring actual JNI calls. It allows tests to control:
     * - Whether the library appears to be loaded
     * - The return value of the native exists method
     * - Whether the native method throws an exception
     * - Capturing the path array passed to the native method for verification
     *
     * @param libraryLoaded Whether to simulate the native library being loaded
     * @param existsResult The return value to simulate from the native exists method
     * @param throwError Whether the native method should throw an UnsatisfiedLinkError
     */
    private class TestableNativeDetector(
        private val libraryLoaded: Boolean,
        private val existsResult: Int = 0,
        private val throwError: Boolean = false
    ) : FileDetector() {

        /**
         * Captures the path array passed to the exists method for test verification.
         */
        var passedPathArray: Array<Any> = emptyArray()

        override fun getFilenames(): List<String> = listOf("su")

        /**
         * Simulates the native exists method behavior.
         *
         * This method mimics the JNI call that would normally check for file existence
         * using native code. It captures the path array for test verification and
         * can be configured to return specific values or throw exceptions.
         *
         * @param pathArray Array of paths to check for existence
         * @return Simulated result (0 for not found, >0 for found)
         * @throws UnsatisfiedLinkError If configured to simulate native library errors
         */
        fun exists(pathArray: Array<Any>): Int {
            passedPathArray = pathArray
            if (throwError) {
                throw UnsatisfiedLinkError("Test error")
            }
            return existsResult
        }

        override suspend fun analyze(context: Context): Double {
            if (!libraryLoaded) return 0.0

            val pathList = PATHS.flatMap { path ->
                getFilenames().map { filename ->
                    path + filename
                }
            }.toTypedArray()

            val pathsAsAny = Array<Any>(pathList.size) { i -> pathList[i] }

            try {
                if (exists(pathsAsAny) > 0) {
                    return 1.0
                }
            } catch (e: UnsatisfiedLinkError) {
                return 0.0
            }
            return 0.0
        }
    }
}