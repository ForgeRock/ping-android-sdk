/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.commons

import com.pingidentity.pingidsdkv2.PingOneSDKError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PingOneMFAExceptionTest {

    // ── public constructor(message) ────────────────────────────────────────────

    @Test
    fun `constructor with message stores message`() {
        val e = PingOneMFAException("Something went wrong")
        assertEquals("Something went wrong", e.message)
    }

    @Test
    fun `constructor with null message defaults to Unknown error`() {
        val e = PingOneMFAException(null as String?)
        assertEquals("Unknown error", e.message)
    }

    @Test
    fun `constructor with message has null cause`() {
        val e = PingOneMFAException("msg")
        assertNull(e.cause)
    }

    @Test
    fun `constructor with message has null internalErrorsList`() {
        val e = PingOneMFAException("msg")
        assertNull(e.internalErrorsList)
    }

    // ── internal constructor(cause: Exception) ─────────────────────────────────

    @Test
    fun `constructor with cause preserves cause in chain`() {
        val cause = RuntimeException("original")
        val e = PingOneMFAException(cause)
        assertEquals(cause, e.cause)
    }

    @Test
    fun `constructor with cause uses cause message`() {
        val cause = RuntimeException("network timeout")
        val e = PingOneMFAException(cause)
        assertEquals("network timeout", e.message)
    }

    @Test
    fun `constructor with cause defaults message when cause message is null`() {
        val cause = RuntimeException(null as String?)
        val e = PingOneMFAException(cause)
        assertEquals("Unknown error", e.message)
    }

    @Test
    fun `constructor with cause has null internalErrorsList`() {
        val e = PingOneMFAException(RuntimeException("cause"))
        assertNull(e.internalErrorsList)
    }

    // ── internal constructor(error: PingOneSDKError) ───────────────────────────

    @Test
    fun `constructor with PingOneSDKError uses error message`() {
        val sdkError = PingOneSDKError(10001, "Auth failed")
        val e = PingOneMFAException(sdkError)
        assertEquals("Auth failed", e.message)
    }

    @Test
    fun `constructor with PingOneSDKError populates internalErrorsList`() {
        val sdkError = PingOneSDKError(10001, "Auth failed")
        val e = PingOneMFAException(sdkError)
        assertNotNull(e.internalErrorsList)
        assertEquals(1, e.internalErrorsList.size)
    }

    @Test
    fun `constructor with PingOneSDKError maps code correctly`() {
        val sdkError = PingOneSDKError(10001, "Auth failed")
        val e = PingOneMFAException(sdkError)
        assertEquals(10001, e.internalErrorsList!!.first().code)
    }

    @Test
    fun `constructor with PingOneSDKError maps userInfo correctly`() {
        val sdkError = PingOneSDKError(10002, "Token expired")
        sdkError.userInfo["requestId"] = "req-999"
        val e = PingOneMFAException(sdkError)
        assertEquals("req-999", e.internalErrorsList!!.first().userInfo["requestId"])
    }

    @Test
    fun `constructor with PingOneSDKError has null cause`() {
        val e = PingOneMFAException(PingOneSDKError(10001, "err"))
        assertNull(e.cause)
    }

    // ── internal constructor(errors: Array<PingOneSDKError>) ───────────────────

    @Test
    fun `constructor with error array uses first error message`() {
        val errors = arrayOf(
            PingOneSDKError(10001, "First"),
            PingOneSDKError(10002, "Second"),
        )
        val e = PingOneMFAException(errors)
        assertEquals("First", e.message)
    }

    @Test
    fun `constructor with error array populates internalErrorsList for all errors`() {
        val errors = arrayOf(
            PingOneSDKError(10001, "First"),
            PingOneSDKError(10002, "Second"),
        )
        val e = PingOneMFAException(errors)
        assertNotNull(e.internalErrorsList)
        assertEquals(2, e.internalErrorsList.size)
    }

    @Test
    fun `constructor with error array maps each error code`() {
        val errors = arrayOf(
            PingOneSDKError(10001, "First"),
            PingOneSDKError(10002, "Second"),
        )
        val e = PingOneMFAException(errors)
        assertEquals(10001, e.internalErrorsList!![0].code)
        assertEquals(10002, e.internalErrorsList!![1].code)
    }

    @Test
    fun `constructor with error array defaults message when first error message is null`() {
        val errors = arrayOf(PingOneSDKError(10003, null))
        println("SDK error message: ${errors[0].message}")
        val e = PingOneMFAException(errors)
        assertEquals("Unknown error", e.message)
    }

    // ── PingOneMFAException is-a Exception ─────────────────────────────────────

    @Test
    fun `PingOneMFAException is subtype of Exception`() {
        val e: Exception = PingOneMFAException("test")
        assertTrue(e is PingOneMFAException)
    }
}
