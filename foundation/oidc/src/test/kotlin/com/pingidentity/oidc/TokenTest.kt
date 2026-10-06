/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.oidc.Token.Companion.now
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @TestRailCase(22112)
    @Test
    fun `isExpired should return true when current time is after expireAt`() {
        val token = Token(expireAt = now() - 1)
        assertTrue(token.isExpired)
    }

    @TestRailCase(22113)
    @Test
    fun `isExpired should return false when current time is before expireAt`() {
        val token = Token(expireAt = now() + 1)
        assertFalse(token.isExpired)
    }

    @TestRailCase(22114)
    @Test
    fun `isExpired with threshold should return true when current time is after expireAt minus threshold`() {
        val token = Token(expireAt = now() + 1)
        assertTrue(token.isExpired(threshold = 2))
    }

    @TestRailCase(22115)
    @Test
    fun `isExpired with threshold should return false when current time is before expireAt minus threshold`() {
        val token = Token(expireAt = now() + 3)
        assertFalse(token.isExpired(threshold = 2))
    }

    @TestRailCase(22116)
    @Test
    fun `Token should deserialize from JSON correctly`() {
        val json = """{"access_token":"accessToken","token_type":"Bearer","scope":"openid","expires_in":3600,"refresh_token":"refreshToken","id_token":"idToken","expireAt":${now() + 3600}}"""
        val token = Json.decodeFromString<Token>(json)
        assertEquals("accessToken", token.accessToken)
        assertEquals("Bearer", token.tokenType)
        assertEquals("openid", token.scope)
        assertEquals(3600, token.expiresIn)
        assertEquals("refreshToken", token.refreshToken)
        assertEquals("idToken", token.idToken)
        assertEquals(now() + 3600, token.expireAt)
    }

    @TestRailCase(22117)
    @Test
    fun `Token should handle missing optional fields during deserialization`() {
        val json = """{"access_token":"accessToken","expires_in":3600,"expireAt":${now() + 3600}}"""
        val token = Json.decodeFromString<Token>(json)
        assertEquals("accessToken", token.accessToken)
        assertEquals(null, token.tokenType)
        assertEquals(null, token.scope)
        assertEquals(3600, token.expiresIn)
        assertEquals(null, token.refreshToken)
        assertEquals(null, token.idToken)
        assertEquals(now() + 3600, token.expireAt)
    }

    @Test
    fun `Token should deserialize authorization_details array with two entries`() {
        val json = """
            {
              "access_token":"accessToken",
              "expires_in":3600,
              "expireAt":${now() + 3600},
              "authorization_details":[
                {
                  "type":"account_information",
                  "actions":["list_accounts","read_balances","read_transactions"],
                  "locations":["https://example.com/accounts"]
                },
                {
                  "type":"payment_initiation",
                  "actions":["initiate","status","cancel"],
                  "locations":["https://example.com/payments"],
                  "datatypes":["PaymentInitiation"],
                  "privileges":["initiate-payment"]
                }
              ]
            }
        """.trimIndent()
        val token = Json.decodeFromString<Token>(json)
        val details = assertNotNull(token.authorizationDetails)
        assertEquals(2, details.size)

        val accountInformation = details[0]
        assertEquals("account_information", accountInformation.type)
        assertEquals(
            listOf("list_accounts", "read_balances", "read_transactions"),
            accountInformation.actions,
        )
        assertEquals(listOf("https://example.com/accounts"), accountInformation.locations)
        assertEquals(null, accountInformation.datatypes)
        assertEquals(null, accountInformation.privileges)

        val paymentInitiation = details[1]
        assertEquals("payment_initiation", paymentInitiation.type)
        assertEquals(listOf("initiate", "status", "cancel"), paymentInitiation.actions)
        assertEquals(listOf("https://example.com/payments"), paymentInitiation.locations)
        assertEquals(listOf("PaymentInitiation"), paymentInitiation.datatypes)
        assertEquals(listOf("initiate-payment"), paymentInitiation.privileges)
    }

    @Test
    fun `Token should decode authorization_details entry with unmodeled key into additionalFields`() {
        val json = """
            {
              "access_token":"accessToken",
              "expires_in":3600,
              "expireAt":${now() + 3600},
              "authorization_details":[
                {
                  "type":"account_information",
                  "identifier":"tx-123"
                }
              ]
            }
        """.trimIndent()
        val token = Json.decodeFromString<Token>(json)
        val details = assertNotNull(token.authorizationDetails)
        assertEquals(1, details.size)
        val entry = details[0]
        assertEquals("account_information", entry.type)
        assertEquals(null, entry.locations)
        assertEquals(null, entry.actions)
        assertEquals(null, entry.datatypes)
        assertEquals(null, entry.privileges)
        assertEquals(
            mapOf("identifier" to JsonPrimitive("tx-123")),
            entry.additionalFields,
        )
    }

    @Test
    fun `Token should deserialize ground truth authorization_details from live AIC response`() {
        val json = """
            {
              "access_token":"accessToken",
              "expires_in":3600,
              "expireAt":${now() + 3600},
              "authorization_details":[
                {
                  "type":"account_information",
                  "actions":["list_accounts","read_balances","read_transactions"],
                  "locations":["https://example.com/accounts"]
                }
              ]
            }
        """.trimIndent()
        val token = Json.decodeFromString<Token>(json)
        assertEquals(
            listOf(
                AuthorizationDetail(
                    type = "account_information",
                    actions = listOf("list_accounts", "read_balances", "read_transactions"),
                    locations = listOf("https://example.com/accounts"),
                ),
            ),
            token.authorizationDetails,
        )
    }

    @Test
    fun `Token should deserialize empty authorization_details array as empty list`() {
        val json = """
            {
              "access_token":"accessToken",
              "expires_in":3600,
              "expireAt":${now() + 3600},
              "authorization_details":[]
            }
        """.trimIndent()
        val token = Json.decodeFromString<Token>(json)
        assertEquals(emptyList<AuthorizationDetail>(), token.authorizationDetails)
    }

    @Test
    fun `Token should have null authorizationDetails when token response has no authorization_details`() {
        val json = """{"access_token":"accessToken","expires_in":3600,"expireAt":${now() + 3600}}"""
        val token = Json.decodeFromString<Token>(json)
        assertNull(token.authorizationDetails)
    }

    @Test
    fun `Token should deserialize authorization_details entry with only type and null optionals`() {
        val json = """
            {
              "access_token":"accessToken",
              "expires_in":3600,
              "expireAt":${now() + 3600},
              "authorization_details":[
                {"type":"account_information"}
              ]
            }
        """.trimIndent()
        val token = Json.decodeFromString<Token>(json)
        assertEquals(
            listOf(AuthorizationDetail(type = "account_information")),
            token.authorizationDetails,
        )
    }

    @Test
    fun `Token should capture vendor extension member of authorization_details entry in additionalFields`() {
        val json = """
            {
              "access_token":"accessToken",
              "expires_in":3600,
              "expireAt":${now() + 3600},
              "authorization_details":[
                {
                  "type":"payment_initiation",
                  "actions":["initiate","status"],
                  "locations":["https://example.com/payments"],
                  "datatypes":["PaymentInitiation"],
                  "privileges":["initiate-payment"],
                  "instructedAmount":{"currency":"EUR","amount":559}
                }
              ]
            }
        """.trimIndent()
        val token = Json.decodeFromString<Token>(json)
        val details = assertNotNull(token.authorizationDetails)
        assertEquals(1, details.size)
        val entry = details[0]
        assertEquals("payment_initiation", entry.type)
        assertEquals(listOf("initiate", "status"), entry.actions)
        assertEquals(listOf("https://example.com/payments"), entry.locations)
        assertEquals(listOf("PaymentInitiation"), entry.datatypes)
        assertEquals(listOf("initiate-payment"), entry.privileges)
        // Only the unmodeled member is captured; known keys stay on their typed properties.
        assertEquals(setOf("instructedAmount"), entry.additionalFields.keys)
        assertEquals(
            buildJsonObject {
                put("currency", "EUR")
                put("amount", 559)
            },
            entry.additionalFields["instructedAmount"],
        )
    }

    @Test
    fun `AuthorizationDetail should round trip through decode and re-encode preserving extension members`() {
        val json = """
            {
              "access_token":"accessToken",
              "expires_in":3600,
              "expireAt":${now() + 3600},
              "authorization_details":[
                {
                  "type":"payment_initiation",
                  "actions":["initiate","status"],
                  "instructedAmount":{"currency":"EUR","amount":559}
                }
              ]
            }
        """.trimIndent()
        val token = Json.decodeFromString<Token>(json)
        assertNotNull(token.authorizationDetails)

        val reEncoded = Json.encodeToString(token)
        val roundTripped = Json.decodeFromString<Token>(reEncoded)

        assertEquals(token.authorizationDetails, roundTripped.authorizationDetails)
        val reEncodedDetails = assertNotNull(roundTripped.authorizationDetails)
        assertEquals(1, reEncodedDetails.size)
        assertEquals(
            buildJsonObject {
                put("currency", "EUR")
                put("amount", 559)
            },
            reEncodedDetails[0].additionalFields["instructedAmount"],
        )
        val reEncodedArray = assertNotNull(
            Json.parseToJsonElement(reEncoded).jsonObject["authorization_details"],
        ).jsonArray
        assertTrue(
            reEncodedArray[0].jsonObject.containsKey("instructedAmount"),
            "Re-encoded JSON should contain the extension key",
        )
    }

    @Test
    fun `AuthorizationDetail re-encode should not duplicate known keys already modeled as properties`() {
        val detail = AuthorizationDetail(
            type = "account_information",
            actions = listOf("list_accounts", "read_balances"),
            additionalFields = mapOf(
                "type" to JsonPrimitive("account_information"),
                "actions" to JsonPrimitive("list_accounts"),
            ),
        )
        val encoded = Json.encodeToString(detail)
        val entryObject = Json.parseToJsonElement(encoded).jsonObject
        // Shadowed additionalFields keys are dropped (the typed property wins), so each modeled
        // member is emitted exactly once and strict parsers accept the object.
        assertEquals(1, entryObject.keys.count { it == "type" })
        assertEquals(1, entryObject.keys.count { it == "actions" })
        assertEquals("account_information", entryObject["type"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("list_accounts", "read_balances"),
            entryObject["actions"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `Token should preserve catch-all field fidelity across decode and re-encode`() {
        // Mirrors the iOS SDK's testCatchAllFieldFidelity: every JSON value kind landing in the
        // catch-all must keep its exact kind and survive a decode/re-encode round trip.
        val json = """
            {
              "access_token":"accessToken",
              "expires_in":3600,
              "expireAt":${now() + 3600},
              "authorization_details":[
                {
                  "type":"account_information",
                  "actions":["list_accounts","read_balances"],
                  "datatypes":["balances"],
                  "isReadOnly":true,
                  "maxAmount":42.5,
                  "note":"some text",
                  "extra":null,
                  "nested":{"a":"b"},
                  "list":[1,2,3]
                }
              ]
            }
        """.trimIndent()
        val token = Json.decodeFromString<Token>(json)
        val details = assertNotNull(token.authorizationDetails)
        assertEquals(1, details.size)
        val entry = details[0]
        assertEquals("account_information", entry.type)
        assertEquals(listOf("list_accounts", "read_balances"), entry.actions)
        assertEquals(listOf("balances"), entry.datatypes)
        assertEquals(
            mapOf(
                "isReadOnly" to JsonPrimitive(true),
                "maxAmount" to JsonPrimitive(42.5),
                "note" to JsonPrimitive("some text"),
                "extra" to JsonNull,
                "nested" to buildJsonObject { put("a", "b") },
                "list" to buildJsonArray { add(1); add(2); add(3) },
            ),
            entry.additionalFields,
        )
        // Each catch-all member keeps its exact JSON kind
        val isReadOnly = entry.additionalFields.getValue("isReadOnly")
        assertTrue(isReadOnly is JsonPrimitive && !isReadOnly.isString)
        assertEquals(true, isReadOnly.jsonPrimitive.boolean)
        assertEquals(42.5, entry.additionalFields.getValue("maxAmount").jsonPrimitive.double)
        assertEquals(JsonNull, entry.additionalFields.getValue("extra"))
        assertTrue(entry.additionalFields.getValue("nested") is JsonObject)
        assertTrue(entry.additionalFields.getValue("list") is JsonArray)

        // Re-encode and decode again: catch-all fields survive a full round trip.
        val reEncoded = Json.encodeToString(token)
        val roundTripped = Json.decodeFromString<Token>(reEncoded)
        assertEquals(token.authorizationDetails, roundTripped.authorizationDetails)
        val reEncodedArray = assertNotNull(
            Json.parseToJsonElement(reEncoded).jsonObject["authorization_details"],
        ).jsonArray
        val reEncodedEntry = reEncodedArray[0].jsonObject
        assertEquals(true, reEncodedEntry["isReadOnly"]?.jsonPrimitive?.boolean)
        assertEquals("42.5", reEncodedEntry["maxAmount"]?.jsonPrimitive?.content)
        assertEquals("some text", reEncodedEntry["note"]?.jsonPrimitive?.content)
        assertTrue(reEncodedEntry.containsKey("extra"))
        assertEquals(JsonNull, reEncodedEntry["extra"])
        assertEquals(buildJsonObject { put("a", "b") }, reEncodedEntry["nested"])
        assertEquals(buildJsonArray { add(1); add(2); add(3) }, reEncodedEntry["list"])
    }

    @Test
    fun `Token should reject scalar authorization_details and scalar actions loudly`() {
        // decisions.md (Error model): decode failures fail loudly — no lenient scalar-or-array
        // coercion. A future regression toward leniency must be caught here.
        val scalarDetails = """
            {"access_token":"accessToken","expires_in":3600,"authorization_details":{"type":"account_information"}}
        """.trimIndent()
        assertFailsWith<SerializationException> {
            Json.decodeFromString<Token>(scalarDetails)
        }

        val scalarActions = """
            {"access_token":"accessToken","expires_in":3600,
             "authorization_details":[{"type":"account_information","actions":"read_balances"}]}
        """.trimIndent()
        assertFailsWith<SerializationException> {
            Json.decodeFromString<Token>(scalarActions)
        }
    }

}
