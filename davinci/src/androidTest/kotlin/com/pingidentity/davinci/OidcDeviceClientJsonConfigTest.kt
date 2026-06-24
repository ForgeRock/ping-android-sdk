/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import androidx.test.filters.SmallTest
import com.pingidentity.android.ContextProvider
import com.pingidentity.oidc.JsonConfigError
import com.pingidentity.oidc.OidcDeviceClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@SmallTest
class OidcDeviceClientJsonConfigTest {

    @Test
    fun oidcDeviceClientSucceedsWithMinimalJsonFile() {
        val json: JsonObject = ContextProvider.context.assets
            .open("minimal-oidc-deviceclient.json")
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }

        val result = OidcDeviceClient(json)

        assertTrue(result.isSuccess, "OidcDeviceClient(json) should succeed but failed: ${result.exceptionOrNull()?.message}")
    }

    @Test
    fun oidcDeviceClientSucceedsWithFullJsonFile() {
        val json: JsonObject = ContextProvider.context.assets
            .open("full-oidc-deviceclient.json")
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }

        val result = OidcDeviceClient(json)

        assertTrue(result.isSuccess, "OidcDeviceClient(json) should succeed with all optional fields but failed: ${result.exceptionOrNull()?.message}")
    }

    @Test
    fun oidcDeviceClientMissingRequiredFieldReturnsError() {
        val validOidc = buildJsonObject {
            put("clientId", "a6859a12-5e6e-4f64-96bb-cc8577706bee")
            put("discoveryEndpoint", "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration")
            put("scopes", "openid")
        }

        fun withoutOidcField(field: String): JsonObject = buildJsonObject {
            put("oidc", buildJsonObject {
                validOidc.forEach { (k, v) -> if (k != field) put(k, v) }
            })
        }

        // redirectUri is intentionally absent: OidcDeviceClient does not require it
        // (device flow uses a device code exchange, not a redirect callback)

        // Missing oidc block entirely
        OidcDeviceClient(buildJsonObject {}).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("oidc", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc.clientId
        OidcDeviceClient(withoutOidcField("clientId")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("clientId", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc.discoveryEndpoint
        OidcDeviceClient(withoutOidcField("discoveryEndpoint")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("discoveryEndpoint", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }

        // Missing oidc.scopes
        OidcDeviceClient(withoutOidcField("scopes")).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.MissingRequiredField>(result.exceptionOrNull())
            assertEquals("scopes", (result.exceptionOrNull() as JsonConfigError.MissingRequiredField).field)
        }
    }

    @Test
    fun oidcDeviceClientUnknownFieldsAreIgnored() {
        val json = buildJsonObject {
            put("unknownTopLevel", "should-be-ignored")
            put("extraFlag", true)
            put("oidc", buildJsonObject {
                put("clientId", "a6859a12-5e6e-4f64-96bb-cc8577706bee")
                put("discoveryEndpoint", "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration")
                put("scopes", buildJsonArray { add("openid") })
                put("unknownOidcField", "also-ignored")
                put("extraOidcObject", buildJsonObject { put("nested", "value") })
            })
        }

        val result = OidcDeviceClient(json)

        assertTrue(result.isSuccess, "OidcDeviceClient(json) should succeed with unknown fields but failed: ${result.exceptionOrNull()?.message}")
    }

    @Test
    fun oidcDeviceClientWrongFieldTypeReturnsError() {
        fun validOidc(overrides: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
            put("oidc", buildJsonObject {
                put("clientId", "a6859a12-5e6e-4f64-96bb-cc8577706bee")
                put("discoveryEndpoint", "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration")
                put("scopes", buildJsonArray { add("openid") })
                overrides()
            })
        }

        // Note: `timeout` is not tested here. OidcDeviceClient uses OidcClientConfig (not
        // WorkflowConfig) so `timeout` is never read by the factory and is silently ignored.

        // oidc.clientId as an object instead of a string
        OidcDeviceClient(buildJsonObject {
            put("oidc", buildJsonObject {
                put("clientId", buildJsonObject {})
                put("discoveryEndpoint", "https://auth.pingone.ca/300c4f2a-39d4-4ba9-a18a-f6de246006f4/as/.well-known/openid-configuration")
                put("scopes", buildJsonArray { add("openid") })
            })
        }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("clientId", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }

        // oidc.scopes as a number instead of string or array
        OidcDeviceClient(validOidc { put("scopes", 123) }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("scopes", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }

        // oidc.refreshThreshold as a non-numeric string instead of a number
        OidcDeviceClient(validOidc { put("refreshThreshold", "not-a-number") }).let { result ->
            assertTrue(result.isFailure)
            assertIs<JsonConfigError.InvalidType>(result.exceptionOrNull())
            assertEquals("refreshThreshold", (result.exceptionOrNull() as JsonConfigError.InvalidType).field)
        }
    }
}
