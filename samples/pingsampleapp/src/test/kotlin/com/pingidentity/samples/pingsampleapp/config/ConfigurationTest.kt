/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigurationTest {

    @Test
    fun `ConfigType JOURNEY serializes as Journey`() {
        val encoded = Json.encodeToString(ConfigType.JOURNEY)
        assertEquals("\"Journey\"", encoded)
    }

    @Test
    fun `ConfigType OIDC_WEB serializes as OIDC (Web)`() {
        val encoded = Json.encodeToString(ConfigType.OIDC_WEB)
        assertEquals("\"OIDC (Web)\"", encoded)
    }

    @Test
    fun `ConfigType DAVINCI serializes as DaVinci`() {
        val encoded = Json.encodeToString(ConfigType.DAVINCI)
        assertEquals("\"DaVinci\"", encoded)
    }

    @Test
    fun `ConfigType DEVICE serializes as Device Flow`() {
        val encoded = Json.encodeToString(ConfigType.DEVICE)
        assertEquals("\"Device Flow\"", encoded)
    }
}
