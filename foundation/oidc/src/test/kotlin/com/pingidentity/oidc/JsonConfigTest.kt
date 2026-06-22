/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.logger.Logger
import com.pingidentity.logger.NONE
import com.pingidentity.logger.STANDARD
import com.pingidentity.logger.WARN
import com.pingidentity.logger.CONSOLE
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JsonConfigTest {

    // -------------------------------------------------------------------------
    // JsonConfigParser.required
    // -------------------------------------------------------------------------

    @Test
    fun `required returns value when key is present and type matches`() {
        val parser = JsonConfigParser(buildJsonObject { put("clientId", "my-client") })
        assertEquals("my-client", parser.required<String>("clientId"))
    }

    @Test
    fun `required throws MissingRequiredField when key is absent`() {
        val parser = JsonConfigParser(buildJsonObject {})
        val ex = assertFailsWith<JsonConfigError.MissingRequiredField> {
            parser.required<String>("clientId")
        }
        assertEquals("clientId", ex.field)
    }

    @Test
    fun `required throws InvalidType when value has wrong type`() {
        val parser = JsonConfigParser(buildJsonObject { put("timeout", 5000) })
        val ex = assertFailsWith<JsonConfigError.InvalidType> {
            parser.required<String>("timeout")
        }
        assertEquals("timeout", ex.field)
    }

    // -------------------------------------------------------------------------
    // JsonConfigParser.optional
    // -------------------------------------------------------------------------

    @Test
    fun `optional returns value when key is present and type matches`() {
        val parser = JsonConfigParser(buildJsonObject { put("realm", "alpha") })
        assertEquals("alpha", parser.optional("realm", "root"))
    }

    @Test
    fun `optional returns default when key is absent`() {
        val parser = JsonConfigParser(buildJsonObject {})
        assertEquals("root", parser.optional("realm", "root"))
    }

    @Test
    fun `optional throws InvalidType when value has wrong type`() {
        val parser = JsonConfigParser(buildJsonObject { put("realm", 42) })
        val ex = assertFailsWith<JsonConfigError.InvalidType> {
            parser.optional("realm", "root")
        }
        assertEquals("realm", ex.field)
    }

    // -------------------------------------------------------------------------
    // JsonConfigParser.timeoutMillis
    // -------------------------------------------------------------------------

    @Test
    fun `timeoutMillis returns parsed value in millis`() {
        val parser = JsonConfigParser(buildJsonObject { put("timeout", 30000) })
        assertEquals(30_000L, parser.timeoutMillis())
    }

    @Test
    fun `timeoutMillis returns default when key is absent`() {
        val parser = JsonConfigParser(buildJsonObject {})
        assertEquals(15_000L, parser.timeoutMillis())
    }

    @Test
    fun `timeoutMillis returns custom default when key is absent`() {
        val parser = JsonConfigParser(buildJsonObject {})
        assertEquals(60_000L, parser.timeoutMillis(default = 60_000L))
    }

    @Test
    fun `timeoutMillis throws InvalidType when value is not a number`() {
        val parser = JsonConfigParser(buildJsonObject { put("timeout", "fast") })
        assertFailsWith<JsonConfigError.InvalidType> {
            parser.timeoutMillis()
        }
    }

    // -------------------------------------------------------------------------
    // JsonConfigParser.logLevel
    // -------------------------------------------------------------------------

    @Test
    fun `logLevel returns STANDARD for standard string`() {
        val parser = JsonConfigParser(buildJsonObject { put("log", "STANDARD") })
        assertEquals(Logger.STANDARD, parser.logLevel())
    }

    @Test
    fun `logLevel returns STANDARD for debug string`() {
        val parser = JsonConfigParser(buildJsonObject { put("log", "DEBUG") })
        assertEquals(Logger.STANDARD, parser.logLevel())
    }

    @Test
    fun `logLevel returns STANDARD for info string`() {
        val parser = JsonConfigParser(buildJsonObject { put("log", "INFO") })
        assertEquals(Logger.STANDARD, parser.logLevel())
    }

    @Test
    fun `logLevel returns WARN for warn string`() {
        val parser = JsonConfigParser(buildJsonObject { put("log", "WARN") })
        assertEquals(Logger.WARN, parser.logLevel())
    }

    @Test
    fun `logLevel returns WARN for error string`() {
        val parser = JsonConfigParser(buildJsonObject { put("log", "ERROR") })
        assertEquals(Logger.WARN, parser.logLevel())
    }

    @Test
    fun `logLevel returns CONSOLE for console string`() {
        val parser = JsonConfigParser(buildJsonObject { put("log", "CONSOLE") })
        assertEquals(Logger.CONSOLE, parser.logLevel())
    }

    @Test
    fun `logLevel returns NONE for none string`() {
        val parser = JsonConfigParser(buildJsonObject { put("log", "NONE") })
        assertEquals(Logger.NONE, parser.logLevel())
    }

    @Test
    fun `logLevel is case insensitive`() {
        val parser = JsonConfigParser(buildJsonObject { put("log", "standard") })
        assertEquals(Logger.STANDARD, parser.logLevel())
    }

    @Test
    fun `logLevel returns default when key is absent`() {
        val parser = JsonConfigParser(buildJsonObject {})
        assertEquals(Logger.NONE, parser.logLevel())
    }

    @Test
    fun `logLevel throws InvalidType for unknown level string`() {
        val parser = JsonConfigParser(buildJsonObject { put("log", "verbose") })
        assertFailsWith<JsonConfigError.InvalidType> {
            parser.logLevel()
        }
    }

    @Test
    fun `logLevel throws InvalidType when value is not a string`() {
        val parser = JsonConfigParser(buildJsonObject { put("log", 1) })
        assertFailsWith<JsonConfigError.InvalidType> {
            parser.logLevel()
        }
    }

    // -------------------------------------------------------------------------
    // String.toScopeSet
    // -------------------------------------------------------------------------

    @Test
    fun `toScopeSet splits comma-separated scopes`() {
        assertEquals(setOf("openid", "email", "profile"), "openid,email,profile".toScopeSet())
    }

    @Test
    fun `toScopeSet trims whitespace around each scope`() {
        assertEquals(setOf("openid", "email"), " openid , email ".toScopeSet())
    }

    @Test
    fun `toScopeSet filters out blank entries`() {
        assertEquals(setOf("openid"), "openid,,".toScopeSet())
    }

    @Test
    fun `toScopeSet returns empty set for blank string`() {
        assertEquals(emptySet(), "".toScopeSet())
    }

    // -------------------------------------------------------------------------
    // JsonConfigParser.additionalParameters
    // -------------------------------------------------------------------------

    @Test
    fun `additionalParameters returns null when key is absent`() {
        val parser = JsonConfigParser(buildJsonObject {})
        assertNull(parser.additionalParameters("additionalParameters"))
    }

    @Test
    fun `additionalParameters parses string values correctly`() {
        val parser = JsonConfigParser(buildJsonObject {
            put("additionalParameters", buildJsonObject {
                put("max_age", "3600")
                put("custom_param", "custom_value")
            })
        })
        val result = parser.additionalParameters("additionalParameters")
        assertEquals(mapOf("max_age" to "3600", "custom_param" to "custom_value"), result)
    }

    @Test
    fun `additionalParameters throws InvalidType when a value is not a string`() {
        val parser = JsonConfigParser(buildJsonObject {
            put("additionalParameters", buildJsonObject {
                put("max_age", 3600)
            })
        })
        val ex = assertFailsWith<JsonConfigError.InvalidType> {
            parser.additionalParameters("additionalParameters")
        }
        assertEquals("additionalParameters.max_age", ex.field)
        assertEquals("string", ex.expected)
    }

    @Test
    fun `additionalParameters throws InvalidType when field is not a JsonObject`() {
        val parser = JsonConfigParser(buildJsonObject {
            put("additionalParameters", "not-an-object")
        })
        assertFailsWith<JsonConfigError.InvalidType> {
            parser.additionalParameters("additionalParameters")
        }
    }

    // -------------------------------------------------------------------------
    // JsonConfigError messages
    // -------------------------------------------------------------------------

    @Test
    fun `MissingRequiredField message contains field name`() {
        val ex = JsonConfigError.MissingRequiredField("clientId")
        assertEquals("Missing required configuration field: 'clientId'", ex.message)
    }

    @Test
    fun `InvalidType message contains field and expected type`() {
        val ex = JsonConfigError.InvalidType(field = "timeout", expected = "Int")
        assertEquals("Invalid type for configuration field 'timeout': expected Int", ex.message)
    }
}
