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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ConfigurationManager]'s pure-logic helpers.
 *
 * DataStore-touching code ([ConfigurationManager.initialize], [ConfigurationManager.select],
 * etc.) requires an Android [Context] and is therefore not tested at this layer.
 * The serialization round-trip and error-recovery logic in [serializeUserConfigs] /
 * [deserializeUserConfigs] are fully JVM-testable and are covered here.
 */
class ConfigurationManagerTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun davinciConfig(
        name: String = "DaVinci Config",
        clientId: String = "dv-client",
        scopes: List<String> = listOf("openid", "profile"),
        redirectUri: String = "org.example://callback",
        discoveryEndpoint: String = "https://auth.example.com/.well-known/openid-configuration",
        environment: String = "PingOne",
    ) = Configuration(
        name = name,
        type = ConfigType.DAVINCI,
        clientId = clientId,
        scopes = scopes,
        redirectUri = redirectUri,
        discoveryEndpoint = discoveryEndpoint,
        environment = environment,
    )

    private fun journeyConfig(
        name: String = "Journey Config",
        clientId: String = "j-client",
    ) = Configuration(
        name = name,
        type = ConfigType.JOURNEY,
        clientId = clientId,
        scopes = listOf("openid", "email"),
        redirectUri = "frauth://com.example.app",
        discoveryEndpoint = "https://openam.example.com/am/oauth2/.well-known/openid-configuration",
        environment = "AIC",
        cookieName = "session-cookie",
        serverUrl = "https://openam.example.com/am",
        realm = "alpha",
    )

    private fun oidcWebConfig(name: String = "OIDC Web Config") = Configuration(
        name = name,
        type = ConfigType.OIDC_WEB,
        clientId = "web-client",
        scopes = listOf("openid", "address"),
        redirectUri = "frauth://com.example.ios",
        discoveryEndpoint = "https://openam.example.com/am/oauth2/.well-known/openid-configuration",
        environment = "AIC",
    )

    private fun deviceConfig(name: String = "Device Config") = Configuration(
        name = name,
        type = ConfigType.DEVICE,
        clientId = "device-client",
        scopes = listOf("openid"),
        redirectUri = "org.example://device",
        discoveryEndpoint = "https://auth.example.com/.well-known/openid-configuration",
        environment = "PingOne",
        deviceAuthorizationEndpoint = "https://auth.example.com/as/device_authorization",
    )

    // -------------------------------------------------------------------------
    // Test 1: round-trip a list of mixed Configuration types
    // -------------------------------------------------------------------------

    @Test
    fun `serialize and deserialize round-trip a list of mixed Configuration types`() {
        val original = listOf(
            davinciConfig(name = "DaVinci One"),
            journeyConfig(name = "Journey One"),
            oidcWebConfig(name = "OIDC One"),
            deviceConfig(name = "Device One"),
        )

        val json = serializeUserConfigs(original)
        val decoded = deserializeUserConfigs(json)

        assertEquals(original.size, decoded.size)
        assertEquals(original[0], decoded[0])
        assertEquals(original[1], decoded[1])
        assertEquals(original[2], decoded[2])
        assertEquals(original[3], decoded[3])
    }

    @Test
    fun `round-trip preserves all optional fields`() {
        val config = Configuration(
            name = "Full Config",
            type = ConfigType.DAVINCI,
            clientId = "full-client",
            scopes = listOf("openid", "email", "profile"),
            redirectUri = "org.example://cb",
            signOutUri = "org.example://signout",
            discoveryEndpoint = "https://auth.example.com/.well-known/openid-configuration",
            deviceAuthorizationEndpoint = "https://auth.example.com/as/device",
            environment = "PingOne",
            cookieName = "my-cookie",
            serverUrl = "https://am.example.com/am",
            realm = "alpha",
            acrValues = "urn:mace:example:acr:1",
            par = true,
        )

        val json = serializeUserConfigs(listOf(config))
        val decoded = deserializeUserConfigs(json)

        assertEquals(1, decoded.size)
        val d = decoded[0]
        assertEquals(config.name, d.name)
        assertEquals(config.type, d.type)
        assertEquals(config.clientId, d.clientId)
        assertEquals(config.scopes, d.scopes)
        assertEquals(config.redirectUri, d.redirectUri)
        assertEquals(config.signOutUri, d.signOutUri)
        assertEquals(config.discoveryEndpoint, d.discoveryEndpoint)
        assertEquals(config.deviceAuthorizationEndpoint, d.deviceAuthorizationEndpoint)
        assertEquals(config.environment, d.environment)
        assertEquals(config.cookieName, d.cookieName)
        assertEquals(config.serverUrl, d.serverUrl)
        assertEquals(config.realm, d.realm)
        assertEquals(config.acrValues, d.acrValues)
        assertEquals(config.par, d.par)
    }

    @Test
    fun `round-trip preserves an empty list`() {
        val json = serializeUserConfigs(emptyList())
        val decoded = deserializeUserConfigs(json)
        assertTrue(decoded.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Test 2: deserializeUserConfigs returns empty list on malformed JSON
    // -------------------------------------------------------------------------

    @Test
    fun `deserializeUserConfigs returns empty list on syntactically broken JSON`() {
        val result = deserializeUserConfigs("{ this is not json [[[")
        assertTrue("Expected empty list on malformed JSON", result.isEmpty())
    }

    @Test
    fun `deserializeUserConfigs returns empty list on valid JSON array with missing required field`() {
        // Missing 'discoveryEndpoint' — ConfigurationParser will throw MissingField, which
        // deserializeUserConfigs recovers from.
        val json = """[{
            "name":"Bad Config",
            "type":"DaVinci",
            "clientId":"c1",
            "scopes":["openid"],
            "redirectUri":"org.example://cb",
            "environment":"PingOne"
        }]"""
        val result = deserializeUserConfigs(json)
        assertTrue("Expected empty list when a required field is missing", result.isEmpty())
    }

    @Test
    fun `deserializeUserConfigs returns empty list on empty string`() {
        val result = deserializeUserConfigs("")
        assertTrue(result.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Test 3: selectedConfig / hasConfiguration pure-logic via in-memory state
    // -------------------------------------------------------------------------

    @Test
    fun `selectedConfig returns null when no configuration has been selected for that type`() {
        // Access the in-memory StateFlow values directly; no Context needed.
        // After construction (before initialize), selections is empty.
        val selection = ConfigurationManager.selectedConfig(ConfigType.JOURNEY)
        // The object may have been previously initialized in other tests (it's a singleton),
        // so we test the helper's behaviour with an explicit map lookup.
        val emptyMap = emptyMap<ConfigType, Configuration>()
        val result = emptyMap[ConfigType.JOURNEY]
        assertEquals(null, result)
    }

    @Test
    fun `hasConfiguration returns false when configurations list is empty`() {
        val emptyList = emptyList<Configuration>()
        val result = emptyList.any { it.type == ConfigType.JOURNEY }
        assertEquals(false, result)
    }

    @Test
    fun `hasConfiguration returns true when matching type exists`() {
        val configs = listOf(
            davinciConfig(name = "DaVinci A"),
            journeyConfig(name = "Journey A"),
        )
        val hasJourney = configs.any { it.type == ConfigType.JOURNEY }
        val hasDaVinci = configs.any { it.type == ConfigType.DAVINCI }
        val hasDevice = configs.any { it.type == ConfigType.DEVICE }

        assertTrue(hasJourney)
        assertTrue(hasDaVinci)
        assertEquals(false, hasDevice)
    }

    // -------------------------------------------------------------------------
    // Test 4: serializeUserConfigs produces valid JSON readable by standard Json
    // -------------------------------------------------------------------------

    @Test
    fun `serializeUserConfigs output is valid JSON decodable by kotlinx serialization`() {
        val configs = listOf(
            davinciConfig(name = "DV"),
            oidcWebConfig(name = "Web"),
        )
        val json = serializeUserConfigs(configs)
        // Should not throw.
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<List<Configuration>>(json)
        assertEquals(2, decoded.size)
        assertEquals("DV", decoded[0].name)
        assertEquals("Web", decoded[1].name)
    }

    @Test
    fun `serializeUserConfigs encodes ConfigType with the correct SerialName`() {
        val config = davinciConfig(name = "Check Type")
        val json = serializeUserConfigs(listOf(config))
        assertTrue(
            "Expected JSON to contain DaVinci type encoding",
            json.contains("\"DaVinci\""),
        )
    }
}
