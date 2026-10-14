/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software is provided 'as-is', without any express or implied
 * warranty. In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package com.pingidentity.recognize

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Transcription guard for [SharedErrorCode] and [nativeToShared], mirroring the
 * `SharedErrorCodeTests` suite on iOS: an independently written expected table of all 29
 * native → shared mappings (shared error model v6.0.0) checked entry by entry, so a
 * transcription error in the Kotlin fails the normal test run.
 */
class SharedErrorCodeTest {

    /** Independent transcription of the shared Android error map (v6.0.0) — 29 entries. */
    private val expectedMapping = listOf(
        10000 to SharedErrorCode.SDK_ERROR,
        10001 to SharedErrorCode.SDK_ARTIFACT_RETRIEVE_FAILED,
        10003 to SharedErrorCode.SDK_LOGGING_CONFIGURATION_FAILED,
        10004 to SharedErrorCode.CAMERA_ERROR,
        10005 to SharedErrorCode.SDK_STORAGE_FAILED,
        10006 to SharedErrorCode.SDK_INVALID_CUSTOMER_PROPERTIES,
        10100 to SharedErrorCode.BIOM_ERROR,
        10200 to SharedErrorCode.CORE_ERROR,
        20000 to SharedErrorCode.CORE_USER_NOT_ENROLLED,
        20001 to SharedErrorCode.CORE_USER_ALREADY_ENROLLED,
        20002 to SharedErrorCode.SDK_NOT_CONFIGURED,
        20010 to SharedErrorCode.SDK_INVALID_CONFIGURATION,
        20013 to SharedErrorCode.CORE_NOT_ENOUGH_API_KEY_SEATS,
        20021 to SharedErrorCode.BIOM_LIVENESS_ENVIRONMENT_AWARE_NOT_SUPPORTED,
        20022 to SharedErrorCode.BIOM_DEVICE_ENVIRONMENT_AWARE_NOT_SUPPORTED,
        20023 to SharedErrorCode.SDK_INVALID_CLIENT_STATE,
        20150 to SharedErrorCode.SDK_DYNAMIC_LINKING_PAYLOAD_MALFORMED,
        20300 to SharedErrorCode.CORE_SECRET_NOT_FOUND,
        30000 to SharedErrorCode.BIOM_GENUINE_PRESENCE_NOT_ESTABLISHED,
        30001 to SharedErrorCode.SDK_TIMEOUT,
        30003 to SharedErrorCode.SDK_USER_CANCELLED,
        30004 to SharedErrorCode.CORE_FACE_NOT_MATCHING,
        30005 to SharedErrorCode.SDK_NO_NETWORK_CONNECTION,
        30007 to SharedErrorCode.CORE_USER_LOCKED_OUT,
        30008 to SharedErrorCode.BIOM_REJECTED,
        30009 to SharedErrorCode.CAMERA_PERMISSION_DENIED,
        30010 to SharedErrorCode.SDK_OUTDATED_APP,
        40000 to SharedErrorCode.SECURITY_ERROR,
        40002 to SharedErrorCode.SECURITY_DEVICE_NOT_GENUINE,
    )

    @Test
    fun `nativeToShared table matches the expected mapping`() {
        assertEquals(expectedMapping.size, nativeToShared.size)
        assertEquals(expectedMapping.toMap(), nativeToShared)
    }

    @Test
    fun `every native code resolves to its expected shared error`() {
        expectedMapping.forEach { (native, shared) ->
            // Each native code must be an explicit entry, not a fallback coincidence.
            assertTrue(native in nativeToShared, "native $native must be an explicit entry in nativeToShared")
            assertEquals(shared, resolve(native))
            assertEquals(shared.code, resolve(native).code)
            assertEquals(shared.name, resolve(native).name)
        }
    }

    @Test
    fun `resolve falls back to SDK_ERROR for unmapped native codes`() {
        val resolved = resolve(99999)
        assertEquals(SharedErrorCode.SDK_ERROR, resolved)
        assertEquals(1000, resolved.code)
        assertEquals("SDK_ERROR", resolved.name)
    }

    @Test
    fun `every shared name equals its enum constant name`() {
        SharedErrorCode.entries.forEach { shared ->
            assertEquals(shared.name, shared.toString())
        }
    }

    @Test
    fun `shared codes are unique across the enum`() {
        assertEquals(
            SharedErrorCode.entries.size,
            SharedErrorCode.entries.map { it.code }.toSet().size,
        )
    }
}
