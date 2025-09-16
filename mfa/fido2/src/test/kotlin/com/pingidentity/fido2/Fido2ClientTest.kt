/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2

import android.content.Context
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAssertionResponse
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import com.pingidentity.android.ContextProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential as GmsPublicKeyCredential

@RunWith(RobolectricTestRunner::class) //CredentialManager uses Android API
class Fido2ClientTest {

    private lateinit var mockContext: Context
    private lateinit var mockCredentialManager: CredentialManager

    @BeforeTest
    fun setUp() {
        mockContext = mockk<Context>()
        mockCredentialManager = mockk<CredentialManager>()

        mockkObject(ContextProvider)
        every { ContextProvider.context } returns mockContext

        mockkObject(CredentialManager.Companion)
        every { CredentialManager.create(any()) } returns mockCredentialManager
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `register should successfully create credential and return attestation response`() =
        runTest {
            // Given
            val creationOptions = buildJsonObject {
                put("challenge", "test-challenge")
                put("rp", buildJsonObject {
                    put("name", "Test RP")
                    put("id", "example.com")
                })
                put("user", buildJsonObject {
                    put("name", "user@example.com")
                    put("id", "user")
                })
            }

            val expectedResponse =
                """{"id":"test-id","rawId":"test-raw-id","response":{"attestationObject":"test-attestation","clientDataJSON":"test-client-data"}}"""
            val mockCreateResponse = mockk<CreatePublicKeyCredentialResponse> {
                every { registrationResponseJson } returns expectedResponse
            }

            val requestSlot = slot<CreatePublicKeyCredentialRequest>()
            coEvery {
                mockCredentialManager.createCredential(
                    context = mockContext,
                    request = capture(requestSlot)
                )
            } returns mockCreateResponse

            // When
            val result =
                Fido2Client.register(CreatePublicKeyCredentialRequest(creationOptions.toString()))

            // Then
            assertTrue(result.isSuccess)
            val response = result.getOrThrow()
            assertEquals("test-id", response["id"]?.toString()?.removeSurrounding("\""))

            coVerify {
                mockCredentialManager.createCredential(
                    context = mockContext,
                    request = any<CreatePublicKeyCredentialRequest>()
                )
            }
            assertEquals(creationOptions.toString(), requestSlot.captured.requestJson)
        }

    @Test
    fun `register should return failure when credential creation fails`() = runTest {
        // Given
        val creationOptions = buildJsonObject {
            put("challenge", "test-challenge")
            put("rp", buildJsonObject {
                put("name", "Test RP")
                put("id", "example.com")
            })
            put("user", buildJsonObject {
                put("name", "user@example.com")
                put("id", "user")
            })
        }

        val expectedException = RuntimeException("Credential creation failed")
        coEvery {
            mockCredentialManager.createCredential(any(), any())
        } throws expectedException

        // When
        val result = Fido2Client.register(CreatePublicKeyCredentialRequest(creationOptions.toString()))

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }

    @Test
    fun `register should throw IllegalStateException for unexpected result type`() = runTest {
        // Given
        val creationOptions = buildJsonObject {
            put("challenge", "test-challenge")
            put("rp", buildJsonObject {
                put("name", "Test RP")
                put("id", "example.com")
            })
            put("user", buildJsonObject {
                put("name", "user@example.com")
                put("id", "user")
            })
        }

        val unexpectedResponse = mockk<CreateCredentialResponse>()
        coEvery {
            mockCredentialManager.createCredential(any(), any())
        } returns unexpectedResponse

        // When
        val result = Fido2Client.register(CreatePublicKeyCredentialRequest(creationOptions.toString()))

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `authenticate should successfully get credential and return assertion response`() =
        runTest {
            // Given
            val requestOptions = buildJsonObject {
                put("challenge", "test-challenge")
                put("rp", buildJsonObject {
                    put("name", "Test RP")
                    put("id", "example.com")
                })
                put("user", buildJsonObject {
                    put("name", "user@example.com")
                    put("id", "user")
                })
            }

            val expectedResponse =
                """{"id":"test-id","rawId":"test-raw-id","response":{"authenticatorData":"test-auth-data","signature":"test-signature","clientDataJSON":"test-client-data"}}"""
            val mockPublicKeyCredential = mockk<PublicKeyCredential> {
                every { authenticationResponseJson } returns expectedResponse
            }
            val mockGetResponse = mockk<GetCredentialResponse> {
                every { credential } returns mockPublicKeyCredential
            }

            val requestSlot = slot<GetCredentialRequest>()
            coEvery {
                mockCredentialManager.getCredential(
                    context = mockContext,
                    request = capture(requestSlot)
                )
            } returns mockGetResponse

            // When
            val result = Fido2Client.authenticate(GetPublicKeyCredentialOption(requestOptions.toString()))

            // Then
            assertTrue(result.isSuccess)
            val response = result.getOrThrow()
            assertEquals("test-id", response["id"]?.toString()?.removeSurrounding("\""))

            coVerify {
                mockCredentialManager.getCredential(
                    context = mockContext,
                    request = any<GetCredentialRequest>()
                )
            }

            val capturedRequest = requestSlot.captured
            assertEquals(1, capturedRequest.credentialOptions.size)
            assertTrue(capturedRequest.credentialOptions.first() is GetPublicKeyCredentialOption)
        }

    @Test
    fun `authenticate should return failure when credential retrieval fails`() = runTest {
        // Given
        val requestOptions = buildJsonObject {
            put("challenge", "test-challenge")
        }

        val expectedException = RuntimeException("Credential retrieval failed")
        coEvery {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        } throws expectedException

        // When
        val result = Fido2Client.authenticate(GetPublicKeyCredentialOption(requestOptions.toString()))

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }

    @Test
    fun `authenticate should throw IllegalStateException for unexpected credential type`() =
        runTest {
            // Given
            val requestOptions = buildJsonObject {
                put("challenge", "test-challenge")
            }

            val unexpectedCredential = mockk<androidx.credentials.Credential>()
            val mockGetResponse = mockk<GetCredentialResponse> {
                every { credential } returns unexpectedCredential
            }
            coEvery {
                mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
            } returns mockGetResponse

            // When
            val result = Fido2Client.authenticate(GetPublicKeyCredentialOption(requestOptions.toString()))

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }

    @Test
    fun `authenticate with PublicKeyCredentialRequestOptions should return success`() = runTest {
        // Given
        val mockOptions = mockk<PublicKeyCredentialRequestOptions>()
        val mockCredential = mockk<GmsPublicKeyCredential>()
        val mockResponse = mockk<AuthenticatorAssertionResponse>()
        val mockAuthenticatorData = "authenticatorData".toByteArray()
        val mockClientDataJSON = "clientDataJSON".toByteArray()
        val mockSignature = "signature".toByteArray()
        val mockUserHandle = "userHandle".toByteArray()
        val mockRawId = "rawId".toByteArray()

        every { mockCredential.id } returns "credentialId"
        every { mockCredential.rawId } returns mockRawId
        every { mockCredential.type } returns "public-key"
        every { mockCredential.authenticatorAttachment } returns "platform"
        every { mockCredential.response } returns mockResponse
        every { mockResponse.authenticatorData } returns mockAuthenticatorData
        every { mockResponse.clientDataJSON } returns mockClientDataJSON
        every { mockResponse.signature } returns mockSignature
        every { mockResponse.userHandle } returns mockUserHandle

        // Mock the getPublicKeyCredential function
        mockkStatic("com.pingidentity.fido2.Fido2NonDiscoverableKt")
        coEvery { getPublicKeyCredential(any(), mockOptions) } returns mockCredential

        // When
        val result = Fido2Client.authenticate(mockOptions)

        // Then
        assertTrue(result.isSuccess)
        val jsonResult = result.getOrThrow()
        assertEquals("credentialId", jsonResult["id"]?.jsonPrimitive?.content)
        assertEquals("public-key", jsonResult["type"]?.jsonPrimitive?.content)

        val responseObject = jsonResult["response"]?.jsonObject
        assertNotNull(responseObject)
        assertEquals(
            mockAuthenticatorData.toBase64(),
            responseObject["authenticatorData"]?.jsonPrimitive?.content
        )
        assertEquals(
            mockClientDataJSON.toBase64(),
            responseObject["clientDataJSON"]?.jsonPrimitive?.content
        )
        assertEquals(
            mockSignature.toBase64(),
            responseObject["signature"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `authenticate with PublicKeyCredentialRequestOptions should handle null userHandle`() =
        runTest {
            // Given
            val mockOptions = mockk<PublicKeyCredentialRequestOptions>()
            val mockCredential = mockk<GmsPublicKeyCredential>()
            val mockResponse = mockk<AuthenticatorAssertionResponse>()

            every { mockCredential.id } returns "credentialId"
            every { mockCredential.rawId } returns "rawId".toByteArray()
            every { mockCredential.type } returns "public-key"
            every { mockCredential.authenticatorAttachment } returns "platform"
            every { mockCredential.response } returns mockResponse
            every { mockResponse.authenticatorData } returns "authenticatorData".toByteArray()
            every { mockResponse.clientDataJSON } returns "clientDataJSON".toByteArray()
            every { mockResponse.signature } returns "signature".toByteArray()
            every { mockResponse.userHandle } returns null

            mockkStatic("com.pingidentity.fido2.Fido2NonDiscoverableKt")
            coEvery { getPublicKeyCredential(any(), mockOptions) } returns mockCredential

            // When
            val result = Fido2Client.authenticate(mockOptions)

            // Then
            assertTrue(result.isSuccess)
            val jsonResult = result.getOrThrow()
            val responseObject = jsonResult["response"]?.jsonObject
            assertNotNull(responseObject)
            assertNull(responseObject["userHandle"])
        }

    @Test
    fun `authenticate with PublicKeyCredentialRequestOptions should handle null rawId`() = runTest {
        // Given
        val mockOptions = mockk<PublicKeyCredentialRequestOptions>()
        val mockCredential = mockk<GmsPublicKeyCredential>()
        val mockResponse = mockk<AuthenticatorAssertionResponse>()

        every { mockCredential.id } returns "credentialId"
        every { mockCredential.rawId } returns null
        every { mockCredential.type } returns "public-key"
        every { mockCredential.authenticatorAttachment } returns "platform"
        every { mockCredential.response } returns mockResponse
        every { mockResponse.authenticatorData } returns "authenticatorData".toByteArray()
        every { mockResponse.clientDataJSON } returns "clientDataJSON".toByteArray()
        every { mockResponse.signature } returns "signature".toByteArray()
        every { mockResponse.userHandle } returns null

        mockkStatic("com.pingidentity.fido2.Fido2NonDiscoverableKt")
        coEvery { getPublicKeyCredential(any(), mockOptions) } returns mockCredential

        // When
        val result = Fido2Client.authenticate(mockOptions)

        // Then
        assertTrue(result.isSuccess)
        val jsonResult = result.getOrThrow()
        assertNull(jsonResult["rawId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `authenticate with PublicKeyCredentialRequestOptions should return failure on exception`() =
        runTest {
            // Given
            val mockOptions = mockk<PublicKeyCredentialRequestOptions>()
            val expectedException = RuntimeException("Authentication failed")

            mockkStatic("com.pingidentity.fido2.Fido2NonDiscoverableKt")
            coEvery { getPublicKeyCredential(any(), mockOptions) } throws expectedException

            // When
            val result = Fido2Client.authenticate(mockOptions)

            // Then
            assertTrue(result.isFailure)
            assertEquals(expectedException, result.exceptionOrNull())
        }

    @Test
    fun `authenticate with PublicKeyCredentialRequestOptions should handle cancellation`() =
        runTest {
            // Given
            val mockOptions = mockk<PublicKeyCredentialRequestOptions>()

            mockkStatic("com.pingidentity.fido2.Fido2NonDiscoverableKt")
            coEvery {
                getPublicKeyCredential(
                    any(),
                    mockOptions
                )
            } throws CancellationException("Cancelled")

            // When
            val result = Fido2Client.authenticate(mockOptions)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is CancellationException)
        }
}

