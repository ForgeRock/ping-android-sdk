/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.PasswordCollector
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.Test
import kotlin.test.assertEquals

class PasswordCollectorTest {

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @TestRailCase(21259)
    @Test
    fun `close should clear password when clearPassword is true`() {
        val passwordCollector = PasswordCollector()
        passwordCollector.value = "password"
        passwordCollector.clearPassword = true

        passwordCollector.close()

        assertEquals("", passwordCollector.value)
    }

    @TestRailCase(22559)
    @Test
    fun `close should not clear password when clearPassword is false`() {
        val passwordCollector = PasswordCollector()
        passwordCollector.value = "password"
        passwordCollector.clearPassword = false

        passwordCollector.close()

        assertEquals("password", passwordCollector.value)
    }
}