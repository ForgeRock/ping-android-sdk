/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

import com.pingidentity.davinci.collector.SubmitCollector
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher

class SubmitCollectorTest {

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @TestRailCase(22560)
    @Test
    fun testInitialization() {
        val submitCollector = SubmitCollector()
        assertNotNull(submitCollector)
    }

    @Test
    fun `close should clear value`() {
        val submitCollector = SubmitCollector()
        submitCollector.value = "submitValue"

        // Verify value is set
        assertEquals("submitValue", submitCollector.value)
        assertEquals("submitValue", submitCollector.payload())

        // Close the collector
        submitCollector.close()

        // Verify value is cleared
        assertEquals("", submitCollector.value)
        assertNull(submitCollector.payload())
    }

    @Test
    fun `close should allow reuse after clearing`() {
        val submitCollector = SubmitCollector()

        // First value
        submitCollector.value = "submit1"
        assertEquals("submit1", submitCollector.payload())

        // Close and set new value
        submitCollector.close()
        assertEquals("", submitCollector.value)
        assertNull(submitCollector.payload())

        // Second value
        submitCollector.value = "submit2"
        assertEquals("submit2", submitCollector.payload())
    }
}