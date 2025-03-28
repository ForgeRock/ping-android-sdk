/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenIdConfigurationTest {

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @TestRailCase(22106)
    @Test
    fun `OpenIdConfiguration default constructor should set all fields to empty strings`() {
        val config = OpenIdConfiguration()
        assertEquals("", config.authorizationEndpoint)
        assertEquals("", config.tokenEndpoint)
        assertEquals("", config.userinfoEndpoint)
        assertEquals("", config.endSessionEndpoint)
        assertEquals("", config.pingEndIdpSessionEndpoint)
        assertEquals("", config.revocationEndpoint)
    }

    @TestRailCase(22107)
    @Test
    fun `OpenIdConfiguration should serialize to JSON correctly`() {
        val config = OpenIdConfiguration(
            authorizationEndpoint = "https://auth.example.com",
            tokenEndpoint = "https://token.example.com",
            userinfoEndpoint = "https://userinfo.example.com",
            endSessionEndpoint = "https://endsession.example.com",
            pingEndIdpSessionEndpoint = "https://pingend.example.com",
            revocationEndpoint = "https://revoke.example.com"
        )
        val json = Json.encodeToString(config)
        assertEquals(
            """{"authorization_endpoint":"https://auth.example.com","token_endpoint":"https://token.example.com","userinfo_endpoint":"https://userinfo.example.com","end_session_endpoint":"https://endsession.example.com","ping_end_idp_session_endpoint":"https://pingend.example.com","revocation_endpoint":"https://revoke.example.com"}""",
            json
        )
    }

    @TestRailCase(22108)
    @Test
    fun `OpenIdConfiguration should deserialize from JSON correctly`() {
        val json = """{"authorization_endpoint":"https://auth.example.com","token_endpoint":"https://token.example.com","userinfo_endpoint":"https://userinfo.example.com","end_session_endpoint":"https://endsession.example.com","ping_end_idp_session_endpoint":"https://pingend.example.com","revocation_endpoint":"https://revoke.example.com"}"""
        val config = Json.decodeFromString<OpenIdConfiguration>(json)
        assertEquals("https://auth.example.com", config.authorizationEndpoint)
        assertEquals("https://token.example.com", config.tokenEndpoint)
        assertEquals("https://userinfo.example.com", config.userinfoEndpoint)
        assertEquals("https://endsession.example.com", config.endSessionEndpoint)
        assertEquals("https://pingend.example.com", config.pingEndIdpSessionEndpoint)
        assertEquals("https://revoke.example.com", config.revocationEndpoint)
    }

    @TestRailCase(22109)
    @Test
    fun `OpenIdConfiguration should handle missing fields during deserialization`() {
        val json = """{"authorization_endpoint":"https://auth.example.com"}"""
        val config = Json.decodeFromString<OpenIdConfiguration>(json)
        assertEquals("https://auth.example.com", config.authorizationEndpoint)
        assertEquals("", config.tokenEndpoint)
        assertEquals("", config.userinfoEndpoint)
        assertEquals("", config.endSessionEndpoint)
        assertEquals("", config.pingEndIdpSessionEndpoint)
        assertEquals("", config.revocationEndpoint)
    }
}