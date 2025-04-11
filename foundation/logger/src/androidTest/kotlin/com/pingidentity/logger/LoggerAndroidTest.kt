/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.logger

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlin.test.Test
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class LoggerAndroidTest {

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @TestRailCase(22067)
    @Test
    fun testConsoleLogger() {
        val log = Logger.CONSOLE

        log.d("Debug message")
        log.i("Info message")
        log.w("Warning message")
        log.e("Error message")
    }

    @TestRailCase(22068)
    @Test
    fun testStandardLogger() {
        val log = Logger.STANDARD

        log.d("Debug message")
        log.i("Info message")
        log.w("Warning message")
        log.e("Error message")
    }

    @TestRailCase(22069)
    @Test
    fun testWarnLogger() {
        val log = Logger.WARN

        log.d("Debug message")
        log.i("Info message")
        log.w("Warning message")
        log.e("Error message")
    }

    @TestRailCase(22070)
    @Test
    fun testNoneLogger() {
        val log = Logger.NONE

        log.d("Debug message")
        log.i("Info message")
        log.w("Warning message")
        log.e("Error message")
    }

    @TestRailCase(22071)
    @Test
    fun testCustomLogger() {
        val customLogger = CustomTestLogger()

        customLogger.d("Debug message")
        customLogger.i("Info message")
        customLogger.w("Warning message")
        customLogger.e("Error message")

        assertTrue(customLogger.messages.contains("DEBUG: Debug message"))
        assertTrue(customLogger.messages.contains("INFO: Info message"))
        assertTrue(customLogger.messages.contains("WARN: Warning message"))
        assertTrue(customLogger.messages.contains("ERROR: Error message"))
    }
}