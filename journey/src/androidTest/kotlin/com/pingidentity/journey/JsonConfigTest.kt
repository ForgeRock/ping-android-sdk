/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import androidx.test.filters.SmallTest
import com.pingidentity.android.ContextProvider
import com.pingidentity.journey.module.Oidc
import com.pingidentity.logger.Logger
import com.pingidentity.logger.None
import com.pingidentity.logger.WARN
import com.pingidentity.oidc.JsonConfigError
import com.pingidentity.oidc.OidcClientConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@SmallTest
class JsonConfigTest {

    @Test
    fun loadMinimalJourneyConfigFromJsonFile() {
        val json: JsonObject = ContextProvider.context.assets
            .open("minimal-journey.json")
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }

        val result = Journey(json)

        assertTrue(result.isSuccess, "Journey(json) should succeed but failed: ${result.exceptionOrNull()?.message}")

        val journey = result.getOrThrow()
        val journeyConfig = journey.config as JourneyConfig
        val oidcConfig = journey.config.modules.first { it.module == Oidc }.config as OidcClientConfig

        assertEquals("https://openam-sdks.forgeblocks.com/am", journeyConfig.serverUrl)
        assertEquals("AndroidTest", oidcConfig.clientId)
        assertEquals(
            "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration",
            oidcConfig.discoveryEndpoint,
        )
        assertEquals("frauth://oauth2redirect", oidcConfig.redirectUri)
        assertEquals(setOf("openid", "profile", "email"), oidcConfig.scopes)
    }

    @Test
    fun journeyDefaultValues() {
        val json: JsonObject = ContextProvider.context.assets
            .open("minimal-journey.json")
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }

        val journey = Journey(json).getOrThrow()
        val journeyConfig = journey.config as JourneyConfig
        val oidcConfig = journey.config.modules.first { it.module == Oidc }.config as OidcClientConfig

        // WorkflowConfig defaults
        assertEquals(15_000L, journey.config.timeout)
        assertIs<None>(journey.config.logger)

        // Journey-specific defaults (omitted from minimal JSON)
        assertEquals("root", journeyConfig.realm)
        assertEquals("iPlanetDirectoryPro", journeyConfig.cookie)

        // OidcClientConfig optional field defaults
        assertNull(oidcConfig.signOutRedirectUri)
        assertEquals(0L, oidcConfig.refreshThreshold)
        assertNull(oidcConfig.loginHint)
        assertNull(oidcConfig.state)
        assertNull(oidcConfig.nonce)
        assertNull(oidcConfig.display)
        assertNull(oidcConfig.prompt)
        assertNull(oidcConfig.uiLocales)
        assertNull(oidcConfig.acrValues)
        assertFalse(oidcConfig.par)
        assertEquals(emptyMap<String, String>(), oidcConfig.additionalParameters)
        assertNull(oidcConfig.openIdOverride)
    }

    @Test
    fun journeyFullConfig() {
        val json: JsonObject = ContextProvider.context.assets
            .open("full-journey-config.json")
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }

        val journey = Journey(json).getOrThrow()
        val journeyConfig = journey.config as JourneyConfig
        val oidcConfig = journey.config.modules.first { it.module == Oidc }.config as OidcClientConfig

        // WorkflowConfig
        assertEquals(30_000L, journey.config.timeout)
        assertSame(Logger.WARN, journey.config.logger)

        // Journey-specific fields
        assertEquals("https://openam-sdks.forgeblocks.com/am", journeyConfig.serverUrl)
        assertEquals("alpha", journeyConfig.realm)
        assertEquals("iPlanetDirectoryPro", journeyConfig.cookie)

        // Required OIDC fields
        assertEquals("AndroidTest", oidcConfig.clientId)
        assertEquals(
            "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration",
            oidcConfig.discoveryEndpoint,
        )
        assertEquals("frauth://oauth2redirect", oidcConfig.redirectUri)
        assertEquals(setOf("openid", "profile", "email"), oidcConfig.scopes)

        // Optional OIDC fields
        assertEquals("frauth://signout", oidcConfig.signOutRedirectUri)
        assertEquals(60L, oidcConfig.refreshThreshold)
        assertEquals("user@example.com", oidcConfig.loginHint)
        assertEquals("test-state", oidcConfig.state)
        assertEquals("test-nonce", oidcConfig.nonce)
        assertEquals("page", oidcConfig.display)
        assertEquals("login", oidcConfig.prompt)
        assertEquals("en-US", oidcConfig.uiLocales)
        assertEquals("Level2", oidcConfig.acrValues)
        assertEquals(true, oidcConfig.par)
        assertEquals(mapOf("max_age" to "3600", "custom_param" to "custom_value"), oidcConfig.additionalParameters)
        assertNotNull(oidcConfig.openIdOverride)
    }

    @Test
    fun journeyMissingRequiredFieldReturnsError() {
        val base = buildJsonObject {
            put("journey", buildJsonObject {
                put("serverUrl", "https://openam-sdks.forgeblocks.com/am")
            })
            put("oidc", buildJsonObject {
                put("clientId", "AndroidTest")
                put("discoveryEndpoint", "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration")
                put("scopes", "openid")
                put("redirectUri", "frauth://oauth2redirect")
            })
        }

        fun withoutOidcField(field: String): JsonObject = buildJsonObject {
            put("journey", base["journey"]!!)
            put("oidc", buildJsonObject {
                base["oidc"]!!.jsonObject.forEach { (k, v) -> if (k != field) put(k, v) }
            })
        }

        // Missing journey.serverUrl
        Journey(buildJsonObject {
            put("journey", buildJsonObject {})
            put("oidc", base["oidc"]!!)
        }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("serverUrl", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing journey block entirely
        Journey(buildJsonObject { put("oidc", base["oidc"]!!) }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("journey", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc.clientId
        Journey(withoutOidcField("clientId")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("clientId", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc.discoveryEndpoint
        Journey(withoutOidcField("discoveryEndpoint")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("discoveryEndpoint", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc.scopes
        Journey(withoutOidcField("scopes")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("scopes", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc.redirectUri
        Journey(withoutOidcField("redirectUri")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("redirectUri", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc block entirely
        Journey(buildJsonObject { put("journey", base["journey"]!!) }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("oidc", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }
    }

    @Test
    fun journeyUnknownFieldsAreIgnored() {
        val json = buildJsonObject {
            put("unknownTopLevel", "should-be-ignored")
            put("extraFlag", true)
            put("journey", buildJsonObject {
                put("serverUrl", "https://openam-sdks.forgeblocks.com/am")
                put("unknownJourneyField", "also-ignored")
            })
            put("oidc", buildJsonObject {
                put("clientId", "AndroidTest")
                put("discoveryEndpoint", "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration")
                put("scopes", buildJsonArray { add("openid") })
                put("redirectUri", "frauth://oauth2redirect")
                put("unknownOidcField", "also-ignored")
                put("extraOidcObject", buildJsonObject { put("nested", "value") })
            })
        }

        val result = Journey(json)

        assertTrue(result.isSuccess, "Journey(json) should succeed with unknown fields but failed: ${result.exceptionOrNull()?.message}")

        val journey = result.getOrThrow()
        val journeyConfig = journey.config as JourneyConfig
        val oidcConfig = journey.config.modules.first { it.module == Oidc }.config as OidcClientConfig

        assertEquals("https://openam-sdks.forgeblocks.com/am", journeyConfig.serverUrl)
        assertEquals("AndroidTest", oidcConfig.clientId)
        assertEquals(
            "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration",
            oidcConfig.discoveryEndpoint,
        )
        assertEquals("frauth://oauth2redirect", oidcConfig.redirectUri)
        assertEquals(setOf("openid"), oidcConfig.scopes)
    }

    @Test
    fun journeyWrongFieldTypeReturnsError() {
        val validJourney = buildJsonObject { put("serverUrl", "https://openam-sdks.forgeblocks.com/am") }

        fun validOidc(overrides: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = buildJsonObject {
            put("journey", validJourney)
            put("oidc", buildJsonObject {
                put("clientId", "AndroidTest")
                put("discoveryEndpoint", "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration")
                put("scopes", buildJsonArray { add("openid") })
                put("redirectUri", "frauth://oauth2redirect")
                overrides()
            })
        }

        // timeout as a non-numeric string instead of a number
        Journey(buildJsonObject {
            put("timeout", "not-a-number")
            put("journey", validJourney)
            put("oidc", validOidc {}["oidc"]!!)
        }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("timeout", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }

        // oidc.clientId as an object instead of a string
        Journey(buildJsonObject {
            put("journey", validJourney)
            put("oidc", buildJsonObject {
                put("clientId", buildJsonObject {})
                put("discoveryEndpoint", "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration")
                put("scopes", buildJsonArray { add("openid") })
                put("redirectUri", "frauth://oauth2redirect")
            })
        }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("clientId", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }

        // oidc.scopes as a number instead of string or array
        Journey(validOidc { put("scopes", 123) }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("scopes", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }

        // oidc.refreshThreshold as a non-numeric string instead of a number
        Journey(validOidc { put("refreshThreshold", "not-a-number") }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("refreshThreshold", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }

        // journey.cookieName as a number instead of a string (gap #8 from spec review)
        Journey(buildJsonObject {
            put("journey", buildJsonObject {
                put("serverUrl", "https://openam-sdks.forgeblocks.com/am")
                put("cookieName", 42)
            })
            put("oidc", validOidc {}["oidc"]!!)
        }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("cookieName", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }
    }

    // Non-default cookieName supplied in the JSON is propagated to JourneyConfig
    @Test
    fun journeyCustomCookieName() {
        val json = buildJsonObject {
            put("journey", buildJsonObject {
                put("serverUrl", "https://openam-sdks.forgeblocks.com/am")
                put("cookieName", "MyCustomCookie")
            })
            put("oidc", buildJsonObject {
                put("clientId", "AndroidTest")
                put("discoveryEndpoint", "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration")
                put("scopes", buildJsonArray { add("openid") })
                put("redirectUri", "frauth://oauth2redirect")
            })
        }

        val journey = Journey(json).getOrThrow()
        val journeyConfig = journey.config as JourneyConfig

        assertEquals("MyCustomCookie", journeyConfig.cookie)
    }
}
