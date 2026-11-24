/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.oidc.Token.Companion.now
import com.pingidentity.storage.EncryptedDataToJsonSerializer
import com.pingidentity.storage.encrypt.Encryptor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenStorageTest {

    private val mockEncryptor = mockk<Encryptor>()

    @Test
    fun `EncryptedDataToJsonSerializer should round trip Token with authorization_details`() = runTest {
        val instructedAmount = buildJsonObject {
            put("currency", "EUR")
            put("amount", 559)
        }
        val token = Token(
            accessToken = "test-access-token",
            tokenType = "Bearer",
            scope = "openid",
            expiresIn = 3600,
            refreshToken = "test-refresh-token",
            idToken = "test-id-token",
            expireAt = now() + 3600,
            authorizationDetails = listOf(
                AuthorizationDetail(
                    type = "payment_initiation",
                    locations = listOf("https://example.com/payments"),
                    actions = listOf("initiate", "status", "cancel"),
                    datatypes = listOf("PaymentInitiation"),
                    privileges = listOf("initiate-payment"),
                    additionalFields = mapOf("instructedAmount" to instructedAmount),
                ),
            ),
        )

        // Pass-through Encryptor: the JSON codec of EncryptedDataToJsonSerializer is under test,
        // not the cipher (same mocked-Encryptor pattern as EncryptedDataToJsonSerializerTest).
        coEvery { mockEncryptor.encrypt(any()) } answers { firstArg<ByteArray>() }
        coEvery { mockEncryptor.decrypt(any()) } answers { firstArg<ByteArray>() }

        val serializer = EncryptedDataToJsonSerializer<Token>(mockEncryptor)

        val outputStream = ByteArrayOutputStream()
        serializer.writeTo(token, outputStream)
        val stored = outputStream.toByteArray()

        val result = assertNotNull(serializer.readFrom(ByteArrayInputStream(stored)))

        assertEquals(token.accessToken, result.accessToken)
        assertEquals(token.tokenType, result.tokenType)
        assertEquals(token.scope, result.scope)
        assertEquals(token.expiresIn, result.expiresIn)
        assertEquals(token.refreshToken, result.refreshToken)
        assertEquals(token.idToken, result.idToken)
        assertEquals(token.expireAt, result.expireAt)

        // The granted details survive the storage write/read round trip (AC 2).
        assertEquals(token.authorizationDetails, result.authorizationDetails)
        val entry = assertNotNull(result.authorizationDetails).single()
        assertEquals("payment_initiation", entry.type)
        assertEquals(listOf("https://example.com/payments"), entry.locations)
        assertEquals(listOf("initiate", "status", "cancel"), entry.actions)
        assertEquals(listOf("PaymentInitiation"), entry.datatypes)
        assertEquals(listOf("initiate-payment"), entry.privileges)
        assertEquals(mapOf("instructedAmount" to instructedAmount), entry.additionalFields)

        // The persisted JSON itself carries the extension member, proving the encode direction
        // (the additionalFields catch-all survives writeTo, not just the decode).
        val storedArray = assertNotNull(
            Json.parseToJsonElement(String(stored)).jsonObject["authorization_details"],
        ).jsonArray
        assertTrue(
            storedArray[0].jsonObject.containsKey("instructedAmount"),
            "Stored token JSON should contain the extension key",
        )

        coVerify { mockEncryptor.encrypt(any()) }
        coVerify { mockEncryptor.decrypt(any()) }
    }

    @Test
    fun `EncryptedDataToJsonSerializer should decode legacy stored Token without authorization_details as null`() = runTest {
        val legacyJson = """{"access_token":"legacy-access-token","token_type":"Bearer","expires_in":3600,"expireAt":${now() + 3600}}"""
            .toByteArray()
        val encryptedData = byteArrayOf(1, 2, 3, 4, 5)

        coEvery { mockEncryptor.decrypt(encryptedData) } returns legacyJson

        val serializer = EncryptedDataToJsonSerializer<Token>(mockEncryptor)

        val result = assertNotNull(serializer.readFrom(ByteArrayInputStream(encryptedData)))

        assertNull(result.authorizationDetails)
        assertEquals("legacy-access-token", result.accessToken)
        assertEquals(3600, result.expiresIn)
        coVerify { mockEncryptor.decrypt(encryptedData) }
    }
}
