/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import androidx.test.filters.SmallTest
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.logger.None
import com.pingidentity.logger.WARN
import com.pingidentity.oidc.JsonConfigError
import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.oidc.OidcWebClient
import com.pingidentity.oidc.OpenIdConfiguration
import com.pingidentity.oidc.module.Oidc
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@SmallTest
class OidcWebClientJsonConfigTest {

    private fun OidcWebClient.oidcConfig(): OidcClientConfig =
        config.modules.first { it.module == Oidc }.config as OidcClientConfig

    @Test
    fun loadMinimalOidcWebClientConfigFromJsonFile() {
        val json: JsonObject = ContextProvider.context.assets
            .open("minimal-oidc-webclient.json")
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }

        val result = OidcWebClient(json)

        assertTrue(result.isSuccess, "OidcWebClient(json) should succeed but failed: ${result.exceptionOrNull()?.message}")

        val client = result.getOrThrow()
        val oidcConfig = client.oidcConfig()

        assertEquals("AndroidTest", oidcConfig.clientId)
        assertEquals(
            "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration",
            oidcConfig.discoveryEndpoint,
        )
        assertEquals("frauth://oauth2redirect", oidcConfig.redirectUri)
        assertEquals(setOf("openid", "profile", "email"), oidcConfig.scopes)
    }

    @Test
    fun oidcWebClientDefaultValues() {
        val json: JsonObject = ContextProvider.context.assets
            .open("minimal-oidc-webclient.json")
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }

        val client = OidcWebClient(json).getOrThrow()
        val oidcConfig = client.oidcConfig()

        assertEquals(15_000L, client.config.timeout)
        assertIs<None>(client.config.logger)

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
    fun oidcWebClientFullConfig() {
        val json: JsonObject = ContextProvider.context.assets
            .open("full-oidc-webclient.json")
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }

        val client = OidcWebClient(json).getOrThrow()
        val oidcConfig = client.oidcConfig()

        assertEquals(30_000L, client.config.timeout)
        assertSame(Logger.WARN, client.config.logger)

        assertEquals("AndroidTest", oidcConfig.clientId)
        assertEquals(
            "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration",
            oidcConfig.discoveryEndpoint,
        )
        assertEquals("frauth://oauth2redirect", oidcConfig.redirectUri)
        assertEquals(setOf("openid", "profile", "email"), oidcConfig.scopes)
        assertEquals("frauth://signout", oidcConfig.signOutRedirectUri)
        assertEquals(60L, oidcConfig.refreshThreshold)
        assertEquals("user@example.com", oidcConfig.loginHint)
        assertEquals("test-state", oidcConfig.state)
        assertEquals("test-nonce", oidcConfig.nonce)
        assertEquals("page", oidcConfig.display)
        assertEquals("login", oidcConfig.prompt)
        assertEquals("en-US", oidcConfig.uiLocales)
        assertEquals("Level2", oidcConfig.acrValues)
        assertTrue(oidcConfig.par)
        assertEquals(mapOf("max_age" to "3600", "custom_param" to "custom_value"), oidcConfig.additionalParameters)
        assertNotNull(oidcConfig.openIdOverride)
    }

    @Test
    fun oidcWebClientOpenIdEndpointsAreLoaded() {
        val json: JsonObject = ContextProvider.context.assets
            .open("full-oidc-webclient.json")
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }

        val client = OidcWebClient(json).getOrThrow()
        val oidcConfig = client.oidcConfig()

        val override = oidcConfig.openIdOverride
        assertNotNull(override, "openIdOverride should be set when openId block is present")

        val openId = OpenIdConfiguration().apply { override() }

        assertEquals(
            "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/authorize",
            openId.authorizationEndpoint,
        )
        assertEquals(
            "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/access_token",
            openId.tokenEndpoint,
        )
        assertEquals(
            "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/userinfo",
            openId.userinfoEndpoint,
        )
        assertEquals(
            "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/connect/endSession",
            openId.endSessionEndpoint,
        )
        assertEquals(
            "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/token/revoke",
            openId.revocationEndpoint,
        )
    }

    @Test
    fun oidcWebClientMissingRequiredFieldReturnsError() {
        val validOidc = buildJsonObject {
            put("clientId", "AndroidTest")
            put("discoveryEndpoint", "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration")
            put("scopes", "openid")
            put("redirectUri", "frauth://oauth2redirect")
        }

        fun withoutOidcField(field: String): JsonObject = buildJsonObject {
            put("oidc", buildJsonObject {
                validOidc.forEach { (k, v) -> if (k != field) put(k, v) }
            })
        }

        // Missing oidc block entirely
        OidcWebClient(buildJsonObject {}).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("oidc", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc.clientId
        OidcWebClient(withoutOidcField("clientId")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("clientId", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc.discoveryEndpoint
        OidcWebClient(withoutOidcField("discoveryEndpoint")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("discoveryEndpoint", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc.scopes
        OidcWebClient(withoutOidcField("scopes")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("scopes", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc.redirectUri
        OidcWebClient(withoutOidcField("redirectUri")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("redirectUri", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }
    }

    @Test
    fun oidcWebClientUnknownFieldsAreIgnored() {
        val json = buildJsonObject {
            put("unknownTopLevel", "should-be-ignored")
            put("extraFlag", true)
            put("oidc", buildJsonObject {
                put("clientId", "AndroidTest")
                put("discoveryEndpoint", "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration")
                put("scopes", buildJsonArray { add("openid") })
                put("redirectUri", "frauth://oauth2redirect")
                put("unknownOidcField", "also-ignored")
                put("extraOidcObject", buildJsonObject { put("nested", "value") })
            })
        }

        val result = OidcWebClient(json)

        assertTrue(result.isSuccess, "OidcWebClient(json) should succeed with unknown fields but failed: ${result.exceptionOrNull()?.message}")

        val client = result.getOrThrow()
        val oidcConfig = client.oidcConfig()

        assertEquals("AndroidTest", oidcConfig.clientId)
        assertEquals("frauth://oauth2redirect", oidcConfig.redirectUri)
        assertEquals(setOf("openid"), oidcConfig.scopes)
    }

    @Test
    fun oidcWebClientWrongFieldTypeReturnsError() {
        // Note: `par` wrong-type is not tested. kotlinx.serialization coerces JSON string "true"
        // to Boolean without error, so the parser cannot distinguish the type mismatch.
        fun validOidc(overrides: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
            put("oidc", buildJsonObject {
                put("clientId", "AndroidTest")
                put("discoveryEndpoint", "https://openam-sdks.forgeblocks.com/am/oauth2/alpha/.well-known/openid-configuration")
                put("scopes", buildJsonArray { add("openid") })
                put("redirectUri", "frauth://oauth2redirect")
                overrides()
            })
        }

        // timeout as a non-numeric string instead of a number
        OidcWebClient(buildJsonObject {
            put("timeout", "not-a-number")
            put("oidc", validOidc {}["oidc"]!!)
        }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("timeout", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }

        // oidc.clientId as an object instead of a string
        OidcWebClient(buildJsonObject {
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
        OidcWebClient(validOidc { put("scopes", 123) }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("scopes", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }

        // oidc.refreshThreshold as a non-numeric string instead of a number
        OidcWebClient(validOidc { put("refreshThreshold", "not-a-number") }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("refreshThreshold", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }
    }
}
