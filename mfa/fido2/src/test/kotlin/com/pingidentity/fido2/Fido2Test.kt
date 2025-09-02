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
import com.pingidentity.android.ContextProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class) //CredentialManager uses Android API
class Fido2Test {

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
    fun `register should successfully create credential and return attestation response`() = runTest {
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

        val expectedResponse = """{"id":"test-id","rawId":"test-raw-id","response":{"attestationObject":"test-attestation","clientDataJSON":"test-client-data"}}"""
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
        val result = Fido2.register(creationOptions)

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
        val creationOptions =  buildJsonObject {
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
        val result = Fido2.register(creationOptions)

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
        val result = Fido2.register(creationOptions)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `authenticate should successfully get credential and return assertion response`() = runTest {
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

        val expectedResponse = """{"id":"test-id","rawId":"test-raw-id","response":{"authenticatorData":"test-auth-data","signature":"test-signature","clientDataJSON":"test-client-data"}}"""
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
        val result = Fido2.authenticate(requestOptions)

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
        val result = Fido2.authenticate(requestOptions)

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }

    @Test
    fun `authenticate should throw IllegalStateException for unexpected credential type`() = runTest {
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
        val result = Fido2.authenticate(requestOptions)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}

