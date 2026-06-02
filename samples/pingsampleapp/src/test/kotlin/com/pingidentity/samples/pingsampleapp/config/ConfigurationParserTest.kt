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
import kotlin.test.assertFailsWith

private val lenientJson = Json { ignoreUnknownKeys = true }

class ConfigurationParserTest {

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun davinciJson(
        name: String = "DaVinci Config",
        clientId: String = "client-id-1",
        scopes: String = """["openid","profile"]""",
        redirectUri: String = "org.example://callback",
        discoveryEndpoint: String = "https://auth.example.com/.well-known/openid-configuration",
        environment: String = "PingOne",
        signOutUri: String? = null,
        acrValues: String? = null,
        par: Boolean? = null,
    ): String {
        val optional = buildString {
            if (signOutUri != null) append(""","signOutUri":"$signOutUri"""")
            if (acrValues != null) append(""","acrValues":"$acrValues"""")
            if (par != null) append(""","par":$par""")
        }
        return """[{
            "name":"$name",
            "type":"DaVinci",
            "clientId":"$clientId",
            "scopes":$scopes,
            "redirectUri":"$redirectUri",
            "discoveryEndpoint":"$discoveryEndpoint",
            "environment":"$environment"
            $optional
        }]"""
    }

    private fun journeyJson(
        name: String = "Journey Config",
        clientId: String = "ios-client",
        scopes: String = """["openid","email"]""",
        redirectUri: String = "frauth://com.example.app",
        discoveryEndpoint: String = "https://openam.example.com/am/oauth2/.well-known/openid-configuration",
        environment: String = "AIC",
        cookieName: String = "session-cookie",
        serverUrl: String = "https://openam.example.com/am",
        realm: String? = "alpha",
    ): String {
        val realmField = if (realm != null) ""","realm":"$realm"""" else ""
        return """[{
            "name":"$name",
            "type":"Journey",
            "clientId":"$clientId",
            "scopes":$scopes,
            "redirectUri":"$redirectUri",
            "discoveryEndpoint":"$discoveryEndpoint",
            "environment":"$environment",
            "cookieName":"$cookieName",
            "serverUrl":"$serverUrl"
            $realmField
        }]"""
    }

    private fun oidcWebJson(
        name: String = "OIDC Web Config",
        clientId: String = "web-client",
        scopes: String = """["openid","address"]""",
        redirectUri: String = "frauth://com.example.ios",
        discoveryEndpoint: String = "https://openam.example.com/am/oauth2/.well-known/openid-configuration",
        environment: String = "AIC",
    ): String = """[{
        "name":"$name",
        "type":"OIDC (Web)",
        "clientId":"$clientId",
        "scopes":$scopes,
        "redirectUri":"$redirectUri",
        "discoveryEndpoint":"$discoveryEndpoint",
        "environment":"$environment"
    }]"""

    // ---------------------------------------------------------------------------
    // Parsing — happy paths
    // ---------------------------------------------------------------------------

    @Test
    fun `parses a fully-specified DaVinci config`() {
        val configs = ConfigurationParser.parse(
            davinciJson(
                name = "DaVinci Test",
                clientId = "abc-123",
                scopes = """["openid","email","profile"]""",
                redirectUri = "org.forgerock.demo://oauth2redirect",
                discoveryEndpoint = "https://auth.pingone.ca/as/.well-known/openid-configuration",
                environment = "PingOne",
                signOutUri = "org.forgerock.demo://oauth2redirect",
                acrValues = "acr-value-1",
                par = false,
            )
        )
        assertEquals(1, configs.size)
        val c = configs[0]
        assertEquals("DaVinci Test", c.name)
        assertEquals(ConfigType.DAVINCI, c.type)
        assertEquals("abc-123", c.clientId)
        assertEquals(listOf("openid", "email", "profile"), c.scopes)
        assertEquals("org.forgerock.demo://oauth2redirect", c.redirectUri)
        assertEquals("org.forgerock.demo://oauth2redirect", c.signOutUri)
        assertEquals("https://auth.pingone.ca/as/.well-known/openid-configuration", c.discoveryEndpoint)
        assertEquals("PingOne", c.environment)
        assertEquals("acr-value-1", c.acrValues)
        assertEquals(false, c.par)
    }

    @Test
    fun `parses a fully-specified Journey config`() {
        val configs = ConfigurationParser.parse(
            journeyJson(
                name = "Journey Full",
                clientId = "sdkPublicClient",
                cookieName = "5421aeddf91aa20",
                serverUrl = "https://openam-sdks.forgeblocks.com/am",
                realm = "alpha",
            )
        )
        assertEquals(1, configs.size)
        val c = configs[0]
        assertEquals("Journey Full", c.name)
        assertEquals(ConfigType.JOURNEY, c.type)
        assertEquals("sdkPublicClient", c.clientId)
        assertEquals("5421aeddf91aa20", c.cookieName)
        assertEquals("https://openam-sdks.forgeblocks.com/am", c.serverUrl)
        assertEquals("alpha", c.realm)
    }

    @Test
    fun `parses a fully-specified OIDC Web config`() {
        val configs = ConfigurationParser.parse(oidcWebJson())
        assertEquals(1, configs.size)
        assertEquals(ConfigType.OIDC_WEB, configs[0].type)
    }

    @Test
    fun `parses a list with mixed types`() {
        val json = """[
            {
                "name":"DaVinci One",
                "type":"DaVinci",
                "clientId":"c1",
                "scopes":["openid"],
                "redirectUri":"org.example://cb",
                "discoveryEndpoint":"https://auth.example.com/.well-known/openid-configuration",
                "environment":"PingOne"
            },
            {
                "name":"Journey One",
                "type":"Journey",
                "clientId":"c2",
                "scopes":["openid"],
                "redirectUri":"frauth://example",
                "discoveryEndpoint":"https://openam.example.com/.well-known/openid-configuration",
                "environment":"AIC",
                "cookieName":"cookie1",
                "serverUrl":"https://openam.example.com/am"
            },
            {
                "name":"OIDC One",
                "type":"OIDC (Web)",
                "clientId":"c3",
                "scopes":["openid"],
                "redirectUri":"frauth://example",
                "discoveryEndpoint":"https://openam.example.com/.well-known/openid-configuration",
                "environment":"AIC"
            }
        ]"""
        val configs = ConfigurationParser.parse(json)
        assertEquals(3, configs.size)
        assertEquals(ConfigType.DAVINCI, configs[0].type)
        assertEquals(ConfigType.JOURNEY, configs[1].type)
        assertEquals(ConfigType.OIDC_WEB, configs[2].type)
    }

    @Test
    fun `accepts unknown JSON fields without failing`() {
        val json = """[{
            "name":"DaVinci X",
            "type":"DaVinci",
            "clientId":"c1",
            "scopes":["openid"],
            "redirectUri":"org.example://cb",
            "discoveryEndpoint":"https://auth.example.com/.well-known/openid-configuration",
            "environment":"PingOne",
            "unknownField":"should be ignored",
            "anotherUnknown":42
        }]"""
        val configs = ConfigurationParser.parse(json)
        assertEquals(1, configs.size)
        assertEquals("DaVinci X", configs[0].name)
    }

    // ---------------------------------------------------------------------------
    // Parsing — error cases
    // ---------------------------------------------------------------------------

    @Test
    fun `throws Malformed on syntactically broken JSON`() {
        val ex = assertFailsWith<ConfigurationException.Malformed> {
            ConfigurationParser.parse("{ not valid json [[[")
        }
        assertTrue(ex.cause != null)
    }

    @Test
    fun `throws MissingField when clientId omitted`() {
        val json = """[{
            "name":"No Client",
            "type":"DaVinci",
            "scopes":["openid"],
            "redirectUri":"org.example://cb",
            "discoveryEndpoint":"https://auth.example.com/.well-known/openid-configuration",
            "environment":"PingOne"
        }]"""
        val ex = assertFailsWith<ConfigurationException.MissingField> {
            ConfigurationParser.parse(json)
        }
        assertEquals("clientId", ex.fieldName)
    }

    @Test
    fun `throws MissingField when discoveryEndpoint omitted`() {
        val json = """[{
            "name":"No Discovery",
            "type":"DaVinci",
            "clientId":"c1",
            "scopes":["openid"],
            "redirectUri":"org.example://cb",
            "environment":"PingOne"
        }]"""
        val ex = assertFailsWith<ConfigurationException.MissingField> {
            ConfigurationParser.parse(json)
        }
        assertEquals("discoveryEndpoint", ex.fieldName)
    }

    @Test
    fun `throws InvalidValue when discoveryEndpoint is not a valid URL`() {
        val json = """[{
            "name":"Bad Discovery",
            "type":"DaVinci",
            "clientId":"c1",
            "scopes":["openid"],
            "redirectUri":"org.example://cb",
            "discoveryEndpoint":"not-a-url",
            "environment":"PingOne"
        }]"""
        val ex = assertFailsWith<ConfigurationException.InvalidValue> {
            ConfigurationParser.parse(json)
        }
        assertEquals("discoveryEndpoint", ex.fieldName)
        assertEquals("not-a-url", ex.value)
    }

    @Test
    fun `throws InvalidValue when scopes is empty`() {
        val json = """[{
            "name":"No Scopes",
            "type":"DaVinci",
            "clientId":"c1",
            "scopes":[],
            "redirectUri":"org.example://cb",
            "discoveryEndpoint":"https://auth.example.com/.well-known/openid-configuration",
            "environment":"PingOne"
        }]"""
        val ex = assertFailsWith<ConfigurationException.InvalidValue> {
            ConfigurationParser.parse(json)
        }
        assertEquals("scopes", ex.fieldName)
    }

    @Test
    fun `throws InvalidValue when type=JOURNEY and serverUrl is blank`() {
        val json = """[{
            "name":"Journey No Server",
            "type":"Journey",
            "clientId":"c1",
            "scopes":["openid"],
            "redirectUri":"frauth://example",
            "discoveryEndpoint":"https://openam.example.com/.well-known/openid-configuration",
            "environment":"AIC",
            "cookieName":"cookie1",
            "serverUrl":"   "
        }]"""
        val ex = assertFailsWith<ConfigurationException.InvalidValue> {
            ConfigurationParser.parse(json)
        }
        assertEquals("serverUrl", ex.fieldName)
        assertEquals("Journey No Server", ex.configName)
    }

    @Test
    fun `throws InvalidValue when type=JOURNEY and cookieName is blank`() {
        val json = """[{
            "name":"Journey No Cookie",
            "type":"Journey",
            "clientId":"c1",
            "scopes":["openid"],
            "redirectUri":"frauth://example",
            "discoveryEndpoint":"https://openam.example.com/.well-known/openid-configuration",
            "environment":"AIC",
            "cookieName":"",
            "serverUrl":"https://openam.example.com/am"
        }]"""
        val ex = assertFailsWith<ConfigurationException.InvalidValue> {
            ConfigurationParser.parse(json)
        }
        assertEquals("cookieName", ex.fieldName)
        assertEquals("Journey No Cookie", ex.configName)
    }

    @Test
    fun `throws DuplicateName when two entries share a name`() {
        val json = """[
            {
                "name":"Same Name",
                "type":"DaVinci",
                "clientId":"c1",
                "scopes":["openid"],
                "redirectUri":"org.example://cb",
                "discoveryEndpoint":"https://auth.example.com/.well-known/openid-configuration",
                "environment":"PingOne"
            },
            {
                "name":"Same Name",
                "type":"OIDC (Web)",
                "clientId":"c2",
                "scopes":["openid"],
                "redirectUri":"frauth://example",
                "discoveryEndpoint":"https://openam.example.com/.well-known/openid-configuration",
                "environment":"AIC"
            }
        ]"""
        val ex = assertFailsWith<ConfigurationException.DuplicateName> {
            ConfigurationParser.parse(json)
        }
        assertEquals("Same Name", ex.name)
    }

    @Test
    fun `round-trips Configuration via Json encodeToString and decodeFromString`() {
        val original = Configuration(
            name = "Round-trip",
            type = ConfigType.DAVINCI,
            clientId = "rt-client",
            scopes = listOf("openid", "profile"),
            redirectUri = "org.example://cb",
            signOutUri = "org.example://cb",
            discoveryEndpoint = "https://auth.example.com/.well-known/openid-configuration",
            environment = "PingOne",
            acrValues = "acr-abc",
            par = true,
        )
        val roundTripJson = Json.encodeToString(original)
        val decoded = lenientJson.decodeFromString<Configuration>(roundTripJson)
        assertEquals(original, decoded)
    }
}
