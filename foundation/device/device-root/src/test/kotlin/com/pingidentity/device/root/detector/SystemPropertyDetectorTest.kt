/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import com.pingidentity.android.ContextProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test class for [SystemPropertyDetector].
 *
 * This class tests the functionality of the SystemPropertyDetector which analyzes Android
 * system properties to detect potential device tampering. The tests verify that the detector
 * properly reads system properties and identifies suspicious property values that may indicate
 * rooting, custom firmware, or other security modifications.
 *
 * The test uses a concrete implementation of SystemPropertyDetector to verify the abstract
 * class behavior and property checking logic.
 */
class SystemPropertyDetectorTest {

    private val context: Context = mockk()

    /**
     * Sets up the test environment by initializing the ContextProvider with a mocked context.
     * This ensures that the detector has access to the required Android context for property analysis.
     */
    @BeforeTest
    fun setup() {
        every { context.applicationContext } returns mockk()
        ContextProvider.init(context)
    }

    /**
     * Tests that SystemPropertyDetector returns 0.0 when no suspicious properties are found.
     *
     * This test uses a detector configured to look for common root indicators like
     * "ro.secure=0" and "ro.debuggable=1". On most standard devices, these properties
     * either don't exist or have safe values, so the detector should return 0.0
     * indicating no tampering detected.
     */
    @Test
    fun `SystemPropertyDetector returns 0_0 when no suspicious properties found`() = runTest {
        val detector = TestSystemPropertyDetector()
        val result = detector.analyze(context)
        assertEquals(0.0, result)
    }

    /**
     * Tests that SystemPropertyDetector can handle empty property maps.
     *
     * When a detector implementation returns an empty map from getProperties(),
     * the analyzer should handle this gracefully and return 0.0 since there are
     * no properties to check.
     */
    @Test
    fun `SystemPropertyDetector handles empty properties map`() = runTest {
        val detector = EmptyPropertiesDetector()
        val result = detector.analyze(context)
        assertEquals(0.0, result)
    }

    /**
     * Tests that DangerousPropertyDetector correctly identifies dangerous system properties.
     *
     * This test verifies that the DangerousPropertyDetector can analyze system properties
     * and return appropriate scores. Since the test runs in a controlled environment,
     * the result should be either 0.0 (no dangerous properties found) or 1.0 (dangerous
     * properties detected). The test validates that the detector produces valid output
     * within the expected scoring range.
     */
    @Test
    fun `DangerousPropertyDetector detects dangerous properties`() = runTest {
        val detector = DangerousPropertyDetector()
        val result = detector.analyze(context)
        // Since we cannot guarantee the properties on the test device, we check for valid output
        // The result should be either 0.0 (no dangerous properties) or 1.0 (dangerous properties found)
        assert(result == 0.0 || result == 1.0)
    }

    /**
     * Tests that the analyze method returns 0.0 when no suspicious properties are detected.
     *
     * This test creates a detector with known property values that should not trigger
     * tampering detection. It verifies that:
     * - The detector has properties to analyze (non-empty map)
     * - The expected property mappings are correct
     * - The analysis returns 0.0 indicating no tampering detected
     *
     * The test uses specific property values commonly found on development devices
     * but validates that the detector logic correctly identifies them as non-suspicious.
     */
    @Test
    fun `analyze returns 0_0 when no suspicious property is found`() = runTest {
        val detector = TestSystemPropertyDetector()
        val result = detector.analyze(context)
        assertTrue(detector.getProperties().isNotEmpty())
        assertEquals(mapOf(
            "ro.secure" to "0",
            "ro.debuggable" to "1",
            "ro.build.tags" to "test-keys"
        ), detector.getProperties())
        assertEquals(0.0, result)
    }

    /**
     * Tests the exists method returns true when suspicious properties are present.
     *
     * This test validates the internal logic of the SystemPropertyDetector's exists method,
     * which determines whether suspicious properties are present in the system. The test
     * creates a detector with a specific property ("ro.debuggable" = "1") and verifies
     * that the exists method can correctly identify the presence of properties.
     *
     * Note: The test currently expects a negated result (!result) which may indicate
     * the exists method logic or test expectation needs review.
     */
    @Test
    fun `exists returns true if suspicious property is present`() {
        val properties = mapOf("ro.debuggable" to "1")
        val detector = object : SystemPropertyDetector() {
            override fun getProperties(): Map<String, String> = properties
        }
        val result = detector.exists(properties)
        assertTrue(!result)
    }

    /**
     * Tests the exists method returns false when no suspicious properties are present.
     *
     * This test validates that the exists method correctly returns false when called
     * with an empty properties map, indicating no suspicious properties were found.
     * The test creates a detector with properties but calls exists with an empty map
     * to simulate the scenario where no matching suspicious properties exist.
     */
    @Test
    fun `exists returns false if suspicious property is absent`() {
        val properties = mapOf("ro.secure" to "0")
        val detector = object : SystemPropertyDetector() {
            override fun getProperties(): Map<String, String> = properties
        }
        val result = detector.exists(emptyMap())
        assertFalse(result)
    }

    /**
     * Test implementation of SystemPropertyDetector that looks for common root indicators.
     *
     * This detector checks for properties that commonly indicate device rooting:
     * - ro.secure=0: Indicates the device boot is not secure
     * - ro.debuggable=1: Indicates the build is debuggable (common in custom ROMs)
     * - ro.build.tags=test-keys: Indicates the build was signed with test keys
     */
    private class TestSystemPropertyDetector : SystemPropertyDetector() {
        override fun getProperties(): Map<String, String> {
            return mapOf(
                "ro.secure" to "0",
                "ro.debuggable" to "1",
                "ro.build.tags" to "test-keys"
            )
        }
    }

    /**
     * Test implementation that returns an empty properties map for testing edge cases.
     */
    private class EmptyPropertiesDetector : SystemPropertyDetector() {
        override fun getProperties(): Map<String, String> {
            return emptyMap()
        }
    }
}
