/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.logger

import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LoggerTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @TestRailCase(22066)
    @Test
    fun loggerSameInstanceConsole() {
        assertTrue(Logger.CONSOLE is Console)
        val log1 = Logger.CONSOLE
        val log2 = Logger.CONSOLE
        assertEquals(log1, log2)
    }

    @TestRailCase(22062)
    @Test
    fun loggerSameInstanceStandard() {
        assertTrue(Logger.STANDARD is Standard)
        val log1 = Logger.STANDARD
        val log2 = Logger.STANDARD
        assertEquals(log1, log2)
    }

    @TestRailCase(22063)
    @Test
    fun loggerSameInstanceWarn() {
        assertTrue(Logger.WARN is Standard)
        val log1 = Logger.WARN
        val log2 = Logger.WARN
        assertEquals(log1, log2)
    }

    @TestRailCase(22064)
    @Test
    fun loggerSameInstanceNone() {
        assertTrue(Logger.NONE is None)
        val log1 = Logger.NONE
        val log2 = Logger.NONE
        assertEquals(log1, log2)
    }

    @TestRailCase(22065)
    @Test
    fun multipleLoggers() {
        val standard = Logger.STANDARD
        val console = Logger.CONSOLE
        val warn = Logger.WARN
        val none = Logger.NONE

        assertNotEquals(standard, console)
        assertNotEquals(standard, warn)
        assertNotEquals(standard, none)
        assertNotEquals(warn, none)
    }
}
