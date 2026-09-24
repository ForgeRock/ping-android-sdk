/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreateRestoreCredentialRequest
import androidx.credentials.CreateRestoreCredentialResponse
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetRestoreCredentialOption
import androidx.credentials.RestoreCredential
import androidx.credentials.exceptions.restorecredential.E2eeUnavailableException
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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class) //CredentialManager uses Android API
class RestoreCredentialClientTest {

    private lateinit var mockContext: Context
    private lateinit var mockActivity: Activity
    private lateinit var mockCredentialManager: CredentialManager
    private lateinit var client: RestoreCredentialClient

    @BeforeTest
    fun setUp() {
        mockContext = mockk<Context>(relaxed = true)
        mockActivity = mockk<Activity>(relaxed = true)
        mockCredentialManager = mockk<CredentialManager>(relaxed = true)

        mockkObject(ContextProvider)
        every { ContextProvider.context } returns mockContext
        every { ContextProvider.currentActivity } returns mockActivity

        mockkObject(CredentialManager.Companion)
        every { CredentialManager.create(any()) } returns mockCredentialManager

        client = RestoreCredentialClient()
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `create should successfully create restore credential with cloud backup enabled`() =
        runTest {
            // Given
            val input = buildJsonObject {
                put("challenge", "test-challenge")
                put("user", buildJsonObject { put("id", "user-id") })
            }
            val expectedResponse = """{"id":"restore-id","response":{"attestationObject":"test"}}"""
            val mockResponse = mockk<CreateRestoreCredentialResponse> {
                every { responseJson } returns expectedResponse
            }

            val requestSlot = slot<CreateRestoreCredentialRequest>()
            coEvery {
                mockCredentialManager.createCredential(
                    context = mockActivity,
                    request = capture(requestSlot)
                )
            } returns mockResponse

            // When
            val result = client.create(input)

            // Then
            assertTrue(result.isSuccess)
            assertEquals("restore-id", result.getOrThrow()["id"]?.jsonPrimitive?.content)
            assertTrue(requestSlot.captured.isCloudBackupEnabled)
            assertEquals(input.toString(), requestSlot.captured.requestJson)
        }

    @Test
    fun `create should retry with cloud backup disabled when E2eeUnavailableException is thrown`() =
        runTest {
            // Given
            val input = buildJsonObject {
                put("challenge", "test-challenge")
                put("user", buildJsonObject { put("id", "user-id") })
            }
            val expectedResponse = """{"id":"restore-id-local"}"""
            val mockResponse = mockk<CreateRestoreCredentialResponse> {
                every { responseJson } returns expectedResponse
            }

            coEvery {
                mockCredentialManager.createCredential(context = mockActivity, request = any())
            } answers {
                val request = secondArg<CreateRestoreCredentialRequest>()
                if (request.isCloudBackupEnabled) {
                    throw E2eeUnavailableException("no backup or e2ee configured")
                } else {
                    mockResponse
                }
            }

            // When
            val result = client.create(input)

            // Then
            assertTrue(result.isSuccess)
            assertEquals("restore-id-local", result.getOrThrow()["id"]?.jsonPrimitive?.content)
            coVerify(exactly = 2) {
                mockCredentialManager.createCredential(context = mockActivity, request = any())
            }
        }

    @Test
    fun `create should return failure when the cloud backup fallback also fails`() = runTest {
        // Given
        val input = buildJsonObject {
            put("challenge", "test-challenge")
            put("user", buildJsonObject { put("id", "user-id") })
        }

        coEvery {
            mockCredentialManager.createCredential(any(), any())
        } throws E2eeUnavailableException("no backup or e2ee configured")

        // When
        val result = client.create(input)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is E2eeUnavailableException)
    }

    @Test
    fun `create should return failure when credential creation fails`() = runTest {
        // Given
        val input = buildJsonObject {
            put("challenge", "test-challenge")
            put("user", buildJsonObject { put("id", "user-id") })
        }
        val expectedException = RuntimeException("Credential creation failed")
        coEvery {
            mockCredentialManager.createCredential(any(), any())
        } throws expectedException

        // When
        val result = client.create(input)

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }

    @Test
    fun `create should return failure for unexpected result type`() = runTest {
        // Given
        val input = buildJsonObject {
            put("challenge", "test-challenge")
            put("user", buildJsonObject { put("id", "user-id") })
        }
        coEvery {
            mockCredentialManager.createCredential(any(), any())
        } returns mockk<CreateCredentialResponse>()

        // When
        val result = client.create(input)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `create with DSL customizer should apply onCreateRestoreCredentialRequest`() = runTest {
        // Given
        val input = buildJsonObject {
            put("challenge", "original-challenge")
            put("user", buildJsonObject { put("id", "user-id") })
        }
        val modifiedJson = buildJsonObject {
            put("challenge", "modified-challenge")
            put("user", buildJsonObject { put("id", "user-id") })
        }.toString()

        val expectedResponse = """{"id":"customized-id"}"""
        val mockResponse = mockk<CreateRestoreCredentialResponse> {
            every { responseJson } returns expectedResponse
        }

        val requestSlot = slot<CreateRestoreCredentialRequest>()
        coEvery {
            mockCredentialManager.createCredential(
                context = mockActivity,
                request = capture(requestSlot)
            )
        } returns mockResponse

        // When
        val result = client.create(input) {
            onCreateRestoreCredentialRequest { original ->
                assertTrue(original.requestJson.contains("original-challenge"))
                CreateRestoreCredentialRequest(modifiedJson)
            }
        }

        // Then
        assertTrue(result.isSuccess)
        assertEquals("customized-id", result.getOrThrow()["id"]?.jsonPrimitive?.content)
        assertEquals(modifiedJson, requestSlot.captured.requestJson)
    }

    @Test
    fun `signIn should successfully retrieve restore credential and return assertion`() = runTest {
        // Given
        val input = buildJsonObject { put("challenge", "test-challenge") }
        val expectedResponse = """{"id":"restore-id","response":{"signature":"test-signature"}}"""
        val mockRestoreCredential = mockk<RestoreCredential> {
            every { authenticationResponseJson } returns expectedResponse
        }
        val mockGetResponse = mockk<GetCredentialResponse> {
            every { credential } returns mockRestoreCredential
        }

        val requestSlot = slot<GetCredentialRequest>()
        coEvery {
            mockCredentialManager.getCredential(
                context = mockActivity,
                request = capture(requestSlot)
            )
        } returns mockGetResponse

        // When
        val result = client.signIn(input)

        // Then
        assertTrue(result.isSuccess)
        assertEquals("restore-id", result.getOrThrow()["id"]?.jsonPrimitive?.content)
        assertEquals(1, requestSlot.captured.credentialOptions.size)
        assertTrue(requestSlot.captured.credentialOptions.first() is GetRestoreCredentialOption)
    }

    @Test
    fun `signIn should return failure when credential retrieval fails`() = runTest {
        // Given
        val input = buildJsonObject { put("challenge", "test-challenge") }
        val expectedException = RuntimeException("No restore credential found")
        coEvery {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        } throws expectedException

        // When
        val result = client.signIn(input)

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }

    @Test
    fun `signIn should return failure for unexpected credential type`() = runTest {
        // Given
        val input = buildJsonObject { put("challenge", "test-challenge") }
        val unexpectedCredential = mockk<Credential>()
        val mockGetResponse = mockk<GetCredentialResponse> {
            every { credential } returns unexpectedCredential
        }
        coEvery {
            mockCredentialManager.getCredential(any(), any() as GetCredentialRequest)
        } returns mockGetResponse

        // When
        val result = client.signIn(input)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `signIn with DSL customizer should apply onGetRestoreCredentialOption`() = runTest {
        // Given
        val input = buildJsonObject { put("challenge", "original-challenge") }
        val modifiedJson = buildJsonObject { put("challenge", "modified-challenge") }.toString()

        val expectedResponse = """{"id":"customized-id"}"""
        val mockRestoreCredential = mockk<RestoreCredential> {
            every { authenticationResponseJson } returns expectedResponse
        }
        val mockGetResponse = mockk<GetCredentialResponse> {
            every { credential } returns mockRestoreCredential
        }

        val requestSlot = slot<GetCredentialRequest>()
        coEvery {
            mockCredentialManager.getCredential(
                context = mockActivity,
                request = capture(requestSlot)
            )
        } returns mockGetResponse

        // When
        val result = client.signIn(input) {
            onGetRestoreCredentialOption { original ->
                assertTrue(original.requestJson.contains("original-challenge"))
                GetRestoreCredentialOption(modifiedJson)
            }
        }

        // Then
        assertTrue(result.isSuccess)
        val option = requestSlot.captured.credentialOptions.first() as GetRestoreCredentialOption
        assertEquals(modifiedJson, option.requestJson)
    }

    @Test
    fun `clear should successfully clear restore credential state`() = runTest {
        // Given
        val requestSlot = slot<ClearCredentialStateRequest>()
        coEvery {
            mockCredentialManager.clearCredentialState(capture(requestSlot))
        } returns Unit

        // When
        val result = client.clear()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(
            ClearCredentialStateRequest.TYPE_CLEAR_RESTORE_CREDENTIAL,
            requestSlot.captured.requestType
        )
    }

    @Test
    fun `clear should return failure when clearing credential state fails`() = runTest {
        // Given
        val expectedException = RuntimeException("Failed to clear credential state")
        coEvery {
            mockCredentialManager.clearCredentialState(any())
        } throws expectedException

        // When
        val result = client.clear()

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }
}
