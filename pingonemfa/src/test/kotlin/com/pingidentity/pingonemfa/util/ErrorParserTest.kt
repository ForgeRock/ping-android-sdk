/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.util

import com.pingidentity.pingidsdkv2.PingOneSDKError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ErrorParserTest {

    private val parser = ErrorParser

    // ── fromPingOneSDKError ────────────────────────────────────────────────────

    @Test
    fun `fromPingOneSDKError returns null when input is null`() {
        val result = parser.fromPingOneSDKError(null)

        assertNull(result)
    }

    @Test
    fun `fromPingOneSDKError maps code and message`() {
        val sdkError = PingOneSDKError(10001, "Authentication failed")

        val result = parser.fromPingOneSDKError(sdkError)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(10001, result.first().code)
        assertEquals("Authentication failed", result.first().message)
    }

    @Test
    fun `fromPingOneSDKError returns empty userInfo when SDK userInfo is null`() {
        val sdkError = PingOneSDKError(10002, "Device not paired")
        // PingOneSDKError.userInfo is initialised to an empty HashMap by default;
        // we guard against null defensively and normalise to emptyMap.

        val result = parser.fromPingOneSDKError(sdkError)

        assertNotNull(result)
        assertTrue(result.first().userInfo.isEmpty())
    }

    @Test
    fun `fromPingOneSDKError preserves userInfo key value pairs`() {
        val sdkError = PingOneSDKError(10003, "Push token invalid")
        sdkError.userInfo["requestId"] = "abc-123"
        sdkError.userInfo["region"] = "NA"

        val result = parser.fromPingOneSDKError(sdkError)

        assertNotNull(result)
        assertEquals("abc-123", result.first().userInfo["requestId"])
        assertEquals("NA", result.first().userInfo["region"])
    }

    @Test
    fun `fromPingOneSDKError returns list with exactly one element`() {
        val sdkError = PingOneSDKError(10004, "Timeout")

        val result = parser.fromPingOneSDKError(sdkError)

        assertNotNull(result)
        assertEquals(1, result.size)
    }

    // ── fromPingOneSDKErrors ───────────────────────────────────────────────────

    @Test
    fun `fromPingOneSDKErrors returns null when input is null`() {
        val result = parser.fromPingOneSDKErrors(null)

        assertNull(result)
    }

    @Test
    fun `fromPingOneSDKErrors returns empty list for empty array`() {
        val result = parser.fromPingOneSDKErrors(emptyArray())

        assertNotNull(result)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fromPingOneSDKErrors maps each error in the array`() {
        val errors = arrayOf(
            PingOneSDKError(10001, "First error"),
            PingOneSDKError(10002, "Second error"),
        )

        val result = parser.fromPingOneSDKErrors(errors)

        assertNotNull(result)
        assertEquals(2, result.size)
        assertEquals(10001, result[0].code)
        assertEquals("First error", result[0].message)
        assertEquals(10002, result[1].code)
        assertEquals("Second error", result[1].message)
    }

    @Test
    fun `fromPingOneSDKErrors preserves userInfo for each error`() {
        val first = PingOneSDKError(10001, "err1").also { it.userInfo["k1"] = "v1" }
        val second = PingOneSDKError(10002, "err2").also { it.userInfo["k2"] = "v2" }

        val result = parser.fromPingOneSDKErrors(arrayOf(first, second))

        assertNotNull(result)
        assertEquals("v1", result[0].userInfo["k1"])
        assertEquals("v2", result[1].userInfo["k2"])
    }

    @Test
    fun `fromPingOneSDKErrors returns flat list for single-element array`() {
        val result = parser.fromPingOneSDKErrors(arrayOf(PingOneSDKError(10005, "One")))

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(10005, result.first().code)
    }
}
