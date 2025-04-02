/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.divinci

import com.pingidentity.orchestrate.EmptySession
import com.pingidentity.orchestrate.Session
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @TestRailCase(22123)
    @Test
    fun `EmptySession value should return empty string`() {
        assertEquals("", EmptySession.value)
    }

    @TestRailCase(22124)
    @Test
    fun `Session value should return correct session value`() {
        val session = object : Session {
            override val value: String
                get() = "session_value"
        }
        assertEquals("session_value", session.value)
    }
}