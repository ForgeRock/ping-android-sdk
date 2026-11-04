/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.module

import com.pingidentity.logger.Logger
import com.pingidentity.oidc.OidcClientConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OidcRequestTest {

    private class CapturingLogger : Logger {
        val warnings = mutableListOf<String>()
        override fun d(message: String) {}
        override fun i(message: String) {}
        override fun w(
            message: String,
            throwable: Throwable?,
        ) {
            warnings.add(message)
        }

        override fun e(
            message: String,
            throwable: Throwable?,
        ) {}
    }

    @Test
    fun `resolveState prefers the per-call parameter`() {
        val config =
            OidcClientConfig().apply {
                state = "configured"
                additionalParameters = mapOf("state" to "additional")
            }
        assertEquals("per-call", config.resolveState(mapOf("state" to "per-call")))
    }

    @Test
    fun `resolveState falls back to additionalParameters then the configured state`() {
        val both =
            OidcClientConfig().apply {
                state = "configured"
                additionalParameters = mapOf("state" to "additional")
            }
        assertEquals("additional", both.resolveState(emptyMap()))

        val configuredOnly =
            OidcClientConfig().apply {
                state = "configured"
            }
        assertEquals("configured", configuredOnly.resolveState(mapOf("other" to "value")))
    }

    @Test
    fun `resolveState returns null when no source provides a state`() {
        assertNull(OidcClientConfig().resolveState(emptyMap()))
        assertNull(OidcClientConfig().resolveState(mapOf("other" to "value")))
    }

    @Test
    fun `resolveState warns when more than one source provides a state`() {
        val logger = CapturingLogger()
        val config =
            OidcClientConfig().apply {
                this.logger = logger
                state = "configured"
                additionalParameters = mapOf("state" to "additional")
            }
        config.resolveState(emptyMap())
        assertTrue(logger.warnings.single().contains("Multiple state sources"))

        // A single source does not warn.
        val quiet = CapturingLogger()
        OidcClientConfig().apply { this.logger = quiet }.resolveState(emptyMap())
        assertTrue(quiet.warnings.isEmpty())
    }

    @Test
    fun `resolveOrGenerateState returns the resolved state when a source provides one`() {
        val config =
            OidcClientConfig().apply {
                state = "configured"
            }
        assertEquals("configured", config.resolveOrGenerateState())
        assertEquals("per-call", config.resolveOrGenerateState(mapOf("state" to "per-call")))
    }

    @Test
    fun `resolveOrGenerateState generates a state when no source provides one`() {
        val generated = OidcClientConfig().resolveOrGenerateState()
        assertEquals(43, generated.length)
        val again = OidcClientConfig().resolveOrGenerateState()
        assertNotEquals(generated, again)
    }
}
