/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import androidx.test.filters.SmallTest
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.logger.None
import com.pingidentity.logger.WARN
import com.pingidentity.oidc.JsonConfigError
import com.pingidentity.oidc.OidcClientConfig
import com.pingidentity.oidc.OpenIdConfiguration
import com.pingidentity.oidc.module.Oidc
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
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
    fun loadMinimalDaVinciConfigFromJsonFile() {
        val json: JsonObject = ContextProvider.context.assets
            .open("minimal-davinci.json")
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }

        val result = DaVinci(json)

        assertTrue(result.isSuccess, "DaVinci(json) should succeed but failed: ${result.exceptionOrNull()?.message}")

        val daVinci = result.getOrThrow()
        val oidcConfig = daVinci.config.modules.first { it.module == Oidc }.config as OidcClientConfig

        assertEquals("a6859a12-5e6e-4f64-96bb-cc8577706bee", oidcConfig.clientId)
        assertEquals(
            "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration",
            oidcConfig.discoveryEndpoint,
        )
        assertEquals("org.forgerock.demo://oauth2redirect", oidcConfig.redirectUri)
        assertEquals(setOf("openid", "profile", "email"), oidcConfig.scopes)
    }

    @Test
    fun daVinciDefaultValues() {
        val json: JsonObject = ContextProvider.context.assets
            .open("minimal-davinci.json")
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }

        val daVinci = DaVinci(json).getOrThrow()
        val oidcConfig = daVinci.config.modules.first { it.module == Oidc }.config as OidcClientConfig

        // WorkflowConfig defaults
        assertEquals(15_000L, daVinci.config.timeout)
        assertIs<None>(daVinci.config.logger)

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
    fun daVinciFullConfig() {
        val json: JsonObject = ContextProvider.context.assets
            .open("full-davinci-config.json")
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }

        val daVinci = DaVinci(json).getOrThrow()
        val oidcConfig = daVinci.config.modules.first { it.module == Oidc }.config as OidcClientConfig

        // WorkflowConfig
        assertEquals(30_000L, daVinci.config.timeout)
        assertSame(Logger.WARN, daVinci.config.logger)

        // Required OIDC fields
        assertEquals("a6859a12-5e6e-4f64-96bb-cc8577706bee", oidcConfig.clientId)
        assertEquals(
            "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration",
            oidcConfig.discoveryEndpoint,
        )
        assertEquals("org.forgerock.demo://oauth2redirect", oidcConfig.redirectUri)
        assertEquals(setOf("openid", "profile", "email"), oidcConfig.scopes)

        // Optional OIDC fields
        assertEquals("org.forgerock.demo://signout", oidcConfig.signOutRedirectUri)
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
    fun daVinciMissingRequiredFieldReturnsError() {
        val base = buildJsonObject {
            put("oidc", buildJsonObject {
                put("clientId", "a6859a12-5e6e-4f64-96bb-cc8577706bee")
                put("discoveryEndpoint", "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration")
                put("scopes", "openid")
                put("redirectUri", "org.forgerock.demo://oauth2redirect")
            })
        }

        fun withoutOidcField(field: String): JsonObject = buildJsonObject {
            put("oidc", buildJsonObject {
                base["oidc"]!!.jsonObject.forEach { (k, v) -> if (k != field) put(k, v) }
            })
        }

        // Missing oidc.clientId
        DaVinci(withoutOidcField("clientId")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("clientId", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc.discoveryEndpoint
        DaVinci(withoutOidcField("discoveryEndpoint")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("discoveryEndpoint", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc.scopes
        DaVinci(withoutOidcField("scopes")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("scopes", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc.redirectUri
        DaVinci(withoutOidcField("redirectUri")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("redirectUri", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc block entirely
        DaVinci(buildJsonObject {}).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("oidc", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }
    }

    @Test
    fun daVinciOpenIdEndpointsAreLoaded() {
        val json: JsonObject = ContextProvider.context.assets
            .open("full-davinci-config.json")
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }

        val daVinci = DaVinci(json).getOrThrow()
        val oidcConfig = daVinci.config.modules.first { it.module == Oidc }.config as OidcClientConfig

        val override = oidcConfig.openIdOverride
        assertNotNull(override, "openIdOverride should be set when openId block is present")

        val openId = OpenIdConfiguration().apply { override() }

        assertEquals(
            "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/authorize",
            openId.authorizationEndpoint,
        )
        assertEquals(
            "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/token",
            openId.tokenEndpoint,
        )
        assertEquals(
            "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/userinfo",
            openId.userinfoEndpoint,
        )
        assertEquals(
            "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/signoff",
            openId.endSessionEndpoint,
        )
        assertEquals(
            "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/revoke",
            openId.revocationEndpoint,
        )
        assertEquals(
            "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/device_authorization",
            openId.deviceAuthorizationEndpoint,
        )
    }

    @Test
    fun daVinciUnknownFieldsAreIgnored() {
        val json = buildJsonObject {
            put("unknownTopLevel", "should-be-ignored")
            put("extraFlag", true)
            put("oidc", buildJsonObject {
                put("clientId", "a6859a12-5e6e-4f64-96bb-cc8577706bee")
                put("discoveryEndpoint", "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration")
                put("scopes", buildJsonArray { add("openid") })
                put("redirectUri", "org.forgerock.demo://oauth2redirect")
                put("unknownOidcField", "also-ignored")
                put("extraOidcObject", buildJsonObject { put("nested", "value") })
            })
        }

        val result = DaVinci(json)

        assertTrue(result.isSuccess, "DaVinci(json) should succeed with unknown fields but failed: ${result.exceptionOrNull()?.message}")

        val daVinci = result.getOrThrow()
        val oidcConfig = daVinci.config.modules.first { it.module == Oidc }.config as OidcClientConfig

        assertEquals("a6859a12-5e6e-4f64-96bb-cc8577706bee", oidcConfig.clientId)
        assertEquals(
            "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration",
            oidcConfig.discoveryEndpoint,
        )
        assertEquals("org.forgerock.demo://oauth2redirect", oidcConfig.redirectUri)
        assertEquals(setOf("openid"), oidcConfig.scopes)
    }

    @Test
    fun daVinciWrongFieldTypeReturnsError() {
        fun validOidc(overrides: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = buildJsonObject {
            put("oidc", buildJsonObject {
                put("clientId", "a6859a12-5e6e-4f64-96bb-cc8577706bee")
                put("discoveryEndpoint", "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration")
                put("scopes", buildJsonArray { add("openid") })
                put("redirectUri", "org.forgerock.demo://oauth2redirect")
                overrides()
            })
        }

        // timeout as a non-numeric string instead of a number
        DaVinci(buildJsonObject {
            put("timeout", "not-a-number")
            put("oidc", validOidc {}["oidc"]!!)
        }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("timeout", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }

        // oidc.clientId as an object instead of a string
        DaVinci(buildJsonObject {
            put("oidc", buildJsonObject {
                put("clientId", buildJsonObject {})
                put("discoveryEndpoint", "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration")
                put("scopes", buildJsonArray { add("openid") })
                put("redirectUri", "org.forgerock.demo://oauth2redirect")
            })
        }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("clientId", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }

        // oidc.scopes as a number instead of string or array
        DaVinci(validOidc { put("scopes", 123) }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("scopes", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }

        // oidc.refreshThreshold as a non-numeric string instead of a number
        DaVinci(validOidc { put("refreshThreshold", "not-a-number") }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("refreshThreshold", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }
    }
}
