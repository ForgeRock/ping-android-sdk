/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.commons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorTest {

    @Test
    fun `error stores code and message`() {
        val error = Error(code = 10001, message = "Authentication failed")

        assertEquals(10001, error.code)
        assertEquals("Authentication failed", error.message)
    }

    @Test
    fun `userInfo defaults to empty map when not provided`() {
        val error = Error(code = 10002, message = "Device not paired")

        assertTrue(error.userInfo.isEmpty())
    }

    @Test
    fun `userInfo stores server diagnostic key value pairs`() {
        val userInfo = mapOf(
            "requestId" to "abc-123",
            "region" to "NA",
            "detail" to "push token expired"
        )
        val error = Error(code = 10003, message = "Push token invalid", userInfo = userInfo)

        assertEquals(3, error.userInfo.size)
        assertEquals("abc-123", error.userInfo["requestId"])
        assertEquals("NA", error.userInfo["region"])
        assertEquals("push token expired", error.userInfo["detail"])
    }

    @Test
    fun `two errors with same fields are equal`() {
        val a = Error(code = 10004, message = "Timeout", userInfo = mapOf("key" to "value"))
        val b = Error(code = 10004, message = "Timeout", userInfo = mapOf("key" to "value"))

        assertEquals(a, b)
    }

    @Test
    fun `two errors with different codes are not equal`() {
        val a = Error(code = 10001, message = "Same message")
        val b = Error(code = 10002, message = "Same message")

        assertTrue(a != b)
    }

    @Test
    fun `two errors with different userInfo are not equal`() {
        val a = Error(code = 10001, message = "msg", userInfo = mapOf("k" to "v1"))
        val b = Error(code = 10001, message = "msg", userInfo = mapOf("k" to "v2"))

        assertTrue(a != b)
    }

    @Test
    fun `copy preserves all fields`() {
        val original = Error(
            code = 10005,
            message = "Original",
            userInfo = mapOf("trace" to "xyz")
        )
        val copy = original.copy(message = "Updated")

        assertEquals(10005, copy.code)
        assertEquals("Updated", copy.message)
        assertEquals(mapOf("trace" to "xyz"), copy.userInfo)
    }
}
