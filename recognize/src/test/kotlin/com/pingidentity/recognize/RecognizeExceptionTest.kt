/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize

import io.keyless.sdk.errorshandling.KeylessSdkError
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class RecognizeExceptionTest {

    private fun keylessSdkError(code: Int, message: String?, debuggingInfo: Map<String, String> = emptyMap()): KeylessSdkError =
        mockk {
            every { this@mockk.code } returns code
            every { this@mockk.message } returns message
            every { this@mockk.debuggingInfo } returns debuggingInfo
        }

    @Test
    fun `from maps native 20000 to shared CORE_USER_NOT_ENROLLED preserving native as sdkCode`() {
        val info = mapOf("flowId" to "flow-1", "sessionId" to "session-2")
        val sdkError = keylessSdkError(code = 20000, message = "user is not enrolled", debuggingInfo = info)

        val ex = RecognizeException.from(sdkError)

        assertEquals(3003, ex.code)
        assertEquals("CORE_USER_NOT_ENROLLED", ex.name)
        assertEquals("user is not enrolled", ex.message)
        assertEquals(20000, ex.sdkCode)
        assertEquals(info + ("sdkCode" to "20000"), ex.debuggingInfo)
        assertSame(sdkError, ex.cause)
    }

    @Test
    fun `from maps an unmapped native code to SDK_ERROR preserving the native value`() {
        // 20024 (userProfileError) has no official shared mapping.
        val sdkError = keylessSdkError(code = 20024, message = "user profile failure", debuggingInfo = emptyMap())

        val ex = RecognizeException.from(sdkError)

        assertEquals(1000, ex.code)
        assertEquals("SDK_ERROR", ex.name)
        assertEquals("user profile failure", ex.message)
        assertEquals(20024, ex.sdkCode)
        assertEquals("20024", ex.debuggingInfo["sdkCode"])
    }

    @Test
    fun `from maps null message to UNKNOWN_ERROR`() {
        val sdkError = keylessSdkError(code = 30003, message = null)

        val ex = RecognizeException.from(sdkError)

        assertEquals("UNKNOWN_ERROR", ex.message)
        assertEquals(1005, ex.code)
        assertEquals("SDK_USER_CANCELLED", ex.name)
        assertEquals(30003, ex.sdkCode)
    }

    @Test
    fun `from copies native code into debuggingInfo sdkCode key`() {
        val sdkError = keylessSdkError(code = 30001, message = "timed out", debuggingInfo = mapOf("flowId" to "f"))

        val ex = RecognizeException.from(sdkError)

        assertEquals("30001", ex.debuggingInfo["sdkCode"])
        assertEquals("f", ex.debuggingInfo["flowId"])
    }

    @Test
    fun `from does not mutate the passed debuggingInfo map`() {
        val info = mutableMapOf("flowId" to "flow-1")
        val sdkError = keylessSdkError(code = 20000, message = "user is not enrolled", debuggingInfo = info)

        RecognizeException.from(sdkError)

        assertNull(info["sdkCode"])
        assertEquals("flow-1", info["flowId"])
    }

    @Test
    fun `asRecognizeException returns SDK_ERROR for wrapped non-Recognize failures`() {
        val ioException = IOException("network dropped")

        val ex = ioException.asRecognizeException()

        assertEquals(1000, ex.code)
        assertEquals("SDK_ERROR", ex.name)
        assertEquals("network dropped", ex.message)
        assertNull(ex.sdkCode)
        assertSame(ioException, ex.cause)
    }

    @Test
    fun `asRecognizeException casts RecognizeException through unchanged`() {
        val original = RecognizeException(
            code = 3003,
            name = "CORE_USER_NOT_ENROLLED",
            message = "not enrolled",
            sdkCode = 20000,
            debuggingInfo = emptyMap(),
        )
        val ex = original.asRecognizeException()

        assertSame(original, ex)
    }

    @Test
    fun `clientErrorReport falls back name and code to SDK_ERROR when nameless`() {
        // Mirrors iOS report(): a nameless RecognizeError loses its code too.
        val ex = RecognizeException(code = 3003, message = "not enrolled", debuggingInfo = emptyMap())

        assertEquals("SDK_ERROR" to "1000", ex.clientErrorReport())
    }

    @Test
    fun `clientErrorReport submits shared name and code when named`() {
        val ex = RecognizeException.from(keylessSdkError(code = 20000, message = "user is not enrolled"))

        assertEquals("CORE_USER_NOT_ENROLLED" to "3003", ex.clientErrorReport())
    }
}
