/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido

import android.app.Activity
import android.content.Context
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.google.android.gms.fido.common.Transport
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAssertionResponse
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialType
import com.pingidentity.android.ContextProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential as GmsPublicKeyCredential

@RunWith(RobolectricTestRunner::class) //CredentialManager uses Android API
class FidoClientTest {

    private lateinit var mockContext: Context
    private lateinit var mockActivity: Activity
    private lateinit var mockCredentialManager: CredentialManager
    private lateinit var fidoClient: FidoClient

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

        // Mock static functions for Google Play Services tests
        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")

        // Remove the mocked Base64 functions - use actual implementations
        // The Base64Ext.kt now contains real implementations
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
                    context = mockActivity,
                    request = capture(requestSlot)
                )
            } returns mockCreateResponse

            // When
            val result =
                FidoClient().register(creationOptions)

            // Then
            assertTrue(result.isSuccess)
            val response = result.getOrThrow()
            assertEquals("test-id", response["id"]?.toString()?.removeSurrounding("\""))

            coVerify {
                mockCredentialManager.createCredential(
                    context = mockActivity,
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
        val result =
            FidoClient().register(creationOptions)

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
        val result =
            FidoClient().register(creationOptions)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `register with DSL customizer should apply onCreatePublicKeyCredentialRequest`() = runTest {
        // Given
        fidoClient = FidoClient()

        val creationOptions = buildJsonObject {
            put("challenge", "original-challenge-base64")
            put("rp", buildJsonObject {
                put("id", "original.example.com")
            })
            put("user", buildJsonObject {
                put("name", "test@example.com")
                put("id", "test-user-id")
            })
        }

        val modifiedJson = buildJsonObject {
            put("challenge", "modified-challenge-base64")
            put("rp", buildJsonObject {
                put("id", "modified.example.com")
            })
            put("user", buildJsonObject {
                put("name", "test@example.com")
                put("id", "test-user-id")
            })
        }.toString()

        val expectedResponse = """{"id":"customized-id","rawId":"customized-raw-id","response":{"attestationObject":"customized-attestation","clientDataJSON":"customized-client-data"}}"""
        val mockCreateResponse = mockk<CreatePublicKeyCredentialResponse> {
            every { registrationResponseJson } returns expectedResponse
        }

        val requestSlot = slot<CreatePublicKeyCredentialRequest>()
        coEvery {
            mockCredentialManager.createCredential(
                context = mockActivity,
                request = capture(requestSlot)
            )
        } returns mockCreateResponse

        // When
        val result = fidoClient.register(creationOptions) {
            onCreatePublicKeyCredentialRequest { originalRequest ->
                // Verify the original request JSON is as expected
                assertTrue(originalRequest.requestJson.contains("original-challenge-base64"))
                assertTrue(originalRequest.requestJson.contains("original.example.com"))

                // Return a new, modified request
                CreatePublicKeyCredentialRequest(modifiedJson)
            }
        }

        // Then
        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("customized-id", response["id"]?.jsonPrimitive?.content)

        // Verify that the customized request was used in the final call
        val capturedRequest = requestSlot.captured
        assertEquals(modifiedJson, capturedRequest.requestJson)

        coVerify {
            mockCredentialManager.createCredential(
                context = mockActivity,
                request = any<CreatePublicKeyCredentialRequest>()
            )
        }
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
                    context = mockActivity,
                    request = capture(requestSlot)
                )
            } returns mockGetResponse

            // When
            val result = FidoClient { useFido2Client = false }.authenticate(requestOptions)

            // Then
            assertTrue(result.isSuccess)
            val response = result.getOrThrow()
            assertEquals("test-id", response["id"]?.toString()?.removeSurrounding("\""))

            coVerify {
                mockCredentialManager.getCredential(
                    context = mockActivity,
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
        val result = FidoClient { useFido2Client = false }.authenticate(requestOptions)

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
            val result = FidoClient { useFido2Client = false }.authenticate(requestOptions)

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
        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
        coEvery { getPublicKeyCredential(any(), any()) } returns mockCredential

        // When
        val result = FidoClient { useFido2Client = true }.authenticate(buildJsonObject { })

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

            mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
            coEvery { getPublicKeyCredential(any(), any()) } returns mockCredential

            // When
            val result = FidoClient { useFido2Client = true }.authenticate(buildJsonObject { })

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

        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
        coEvery { getPublicKeyCredential(any(), any()) } returns mockCredential

        // When
        val result = FidoClient { useFido2Client = true }.authenticate(buildJsonObject { })

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

            mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
            coEvery { getPublicKeyCredential(any(), any()) } throws expectedException

            // When
            val result = FidoClient { useFido2Client = true }.authenticate(buildJsonObject { })

            // Then
            assertTrue(result.isFailure)
            assertEquals(expectedException, result.exceptionOrNull())
        }

    @Test
    fun `authenticate with PublicKeyCredentialRequestOptions should handle cancellation`() =
        runTest {
            // Given
            val mockOptions = mockk<PublicKeyCredentialRequestOptions>()

            mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
            coEvery {
                getPublicKeyCredential(
                    any(),
                    any()
                )
            } throws CancellationException("Cancelled")

            // When
            val result = FidoClient { useFido2Client = true }.authenticate(buildJsonObject { })

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is CancellationException)
        }

    @Test
    fun `authenticate with DSL customizer should apply onPublicKeyCredentialRequestOptions when useFido2Client is true`() = runTest {
        // Given
        fidoClient = FidoClient {
            useFido2Client = true
        }

        val challengeBytes = "test-challenge-for-dsl".toByteArray()
        val challengeBase64 = challengeBytes.toBase64()

        val inputJson = buildJsonObject {
            put(Constants.FIELD_RP_ID, "example.com")
            put(Constants.FIELD_CHALLENGE, challengeBase64)
            put(Constants.FIELD_TIMEOUT, 60000.0)
        }

        val mockGmsCredential = mockk<GmsPublicKeyCredential> {
            every { id } returns "test-credential-id"
            every { rawId } returns byteArrayOf(1, 2, 3, 4)
            every { type } returns "public-key"
            every { authenticatorAttachment } returns "platform"
        }

        val mockAuthenticatorResponse = mockk<AuthenticatorAssertionResponse> {
            every { authenticatorData } returns byteArrayOf(5, 6, 7, 8)
            every { clientDataJSON } returns byteArrayOf(9, 10, 11, 12)
            every { signature } returns byteArrayOf(13, 14, 15, 16)
            every { userHandle } returns null
        }

        every { mockGmsCredential.response } returns mockAuthenticatorResponse

        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockGmsCredential

        // When - using DSL customizer
        val result = fidoClient.authenticate(inputJson) {
            onPublicKeyCredentialRequestOptions { options ->
                // Verify original options are correctly parsed
                assertEquals("example.com", options.rpId)
                assertEquals(60.0, options.timeoutSeconds)

                // Verify challenge was properly base64 decoded
                val expectedChallenge = challengeBase64.urlSafeDecode()
                assertTrue(options.challenge.contentEquals(expectedChallenge))
                assertEquals("test-challenge-for-dsl", String(options.challenge))

                // Return modified options
                PublicKeyCredentialRequestOptions.Builder()
                    .setRpId("modified-${options.rpId}")
                    .setChallenge(options.challenge)
                    .setTimeoutSeconds((options.timeoutSeconds ?: 60.0) + 30) // Add 30 seconds
                    .setAllowList(options.allowList)
                    .build()
            }
        }

        // Then
        assertTrue(result.isSuccess)

        // Verify the modified options were used
        val capturedOptions = optionsSlot.captured
        assertEquals("modified-example.com", capturedOptions.rpId)
        assertEquals(90.0, capturedOptions.timeoutSeconds) // 60 + 30
        assertTrue(capturedOptions.challenge.contentEquals(challengeBytes))
    }

    @Test
    fun `authenticate with DSL customizer should apply onGetPublicKeyCredentialOption when useFido2Client is false`() = runTest {
        // Given
        fidoClient = FidoClient {
            useFido2Client = false
        }

        val inputJson = buildJsonObject {
            put(Constants.FIELD_RP_ID, "test.example.com")
            put(Constants.FIELD_CHALLENGE, "dGVzdC1jaGFsbGVuZ2U") // "test-challenge" base64
            put(Constants.FIELD_TIMEOUT, 45000.0)
        }

        val expectedResponse = """{"id":"modified-id","type":"public-key"}"""
        val mockPublicKeyCredential = mockk<PublicKeyCredential> {
            every { authenticationResponseJson } returns expectedResponse
        }
        val mockGetResponse = mockk<GetCredentialResponse> {
            every { credential } returns mockPublicKeyCredential
        }

        val requestSlot = slot<GetCredentialRequest>()
        coEvery {
            mockCredentialManager.getCredential(
                context = mockActivity,
                request = capture(requestSlot)
            )
        } returns mockGetResponse

        // When - using DSL customizer
        val result = fidoClient.authenticate(inputJson) {
            onGetPublicKeyCredentialOption { option ->
                // Verify the option contains the correct JSON with base64 challenge
                assertTrue(option.requestJson.contains("test.example.com"))
                assertTrue(option.requestJson.contains("dGVzdC1jaGFsbGVuZ2U"))

                // Parse and verify the JSON structure
                val parsedJson = kotlinx.serialization.json.Json.parseToJsonElement(option.requestJson)
                val jsonObj = parsedJson.jsonObject
                assertEquals("test.example.com", jsonObj[Constants.FIELD_RP_ID]?.jsonPrimitive?.content)
                assertEquals("dGVzdC1jaGFsbGVuZ2U", jsonObj[Constants.FIELD_CHALLENGE]?.jsonPrimitive?.content)

                // Return the original option (could be modified if needed)
                option
            }
        }

        // Then
        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("modified-id", response["id"]?.jsonPrimitive?.content)

        // Verify credential manager was called
        coVerify {
            mockCredentialManager.getCredential(
                context = mockActivity,
                request = any()
            )
        }
    }

    @Test
    fun `authenticate with DSL customizer should handle allowCredentials with real base64 decoding`() = runTest {
        // Given
        fidoClient = FidoClient {
            useFido2Client = true
        }

        val credentialId1 = "first-test-credential".toByteArray()
        val credentialId2 = "second-test-credential".toByteArray()
        val challengeBytes = "complex-challenge-data".toByteArray()

        val inputJson = buildJsonObject {
            put(Constants.FIELD_RP_ID, "secure.example.com")
            put(Constants.FIELD_CHALLENGE, challengeBytes.toBase64())
            put(Constants.FIELD_TIMEOUT, 90000.0)
            put(Constants.FIELD_ALLOW_CREDENTIALS, kotlinx.serialization.json.JsonArray(
                listOf(
                    buildJsonObject {
                        put(Constants.FIELD_TYPE, "public-key")
                        put(Constants.FIELD_ID, credentialId1.toBase64())
                    },
                    buildJsonObject {
                        put(Constants.FIELD_TYPE, "public-key")
                        put(Constants.FIELD_ID, credentialId2.toBase64())
                    }
                )
            ))
        }

        val mockGmsCredential = mockk<GmsPublicKeyCredential> {
            every { id } returns "response-credential-id"
            every { rawId } returns "response-raw-id".toByteArray()
            every { type } returns "public-key"
            every { authenticatorAttachment } returns "cross-platform"
        }

        val mockAuthenticatorResponse = mockk<AuthenticatorAssertionResponse> {
            every { authenticatorData } returns "auth-data-test".toByteArray()
            every { clientDataJSON } returns "client-data-test".toByteArray()
            every { signature } returns "signature-test".toByteArray()
            every { userHandle } returns "user-handle-test".toByteArray()
        }

        every { mockGmsCredential.response } returns mockAuthenticatorResponse

        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockGmsCredential

        // When - using DSL customizer with allowCredentials validation
        val result = fidoClient.authenticate(inputJson) {
            onPublicKeyCredentialRequestOptions { options ->
                // Verify JSON parsing and allowCredentials processing
                assertEquals("secure.example.com", options.rpId)
                assertEquals(90.0, options.timeoutSeconds)

                // Verify challenge decoding
                assertTrue(options.challenge.contentEquals(challengeBytes))
                assertEquals("complex-challenge-data", String(options.challenge))

                // Verify allowCredentials were properly processed
                assertEquals(2, options.allowList?.size)

                // Check first credential with real base64 decoding
                val firstCred = options.allowList!![0]
                assertTrue(firstCred.id.contentEquals(credentialId1))
                assertEquals("first-test-credential", String(firstCred.id))
                assertEquals(PublicKeyCredentialType.PUBLIC_KEY, firstCred.type)

                // Check second credential
                val secondCred = options.allowList!![1]
                assertTrue(secondCred.id.contentEquals(credentialId2))
                assertEquals("second-test-credential", String(secondCred.id))

                // Return options with modified timeout
                PublicKeyCredentialRequestOptions.Builder()
                    .setRpId(options.rpId)
                    .setChallenge(options.challenge)
                    .setTimeoutSeconds(120.0) // Override timeout
                    .setAllowList(options.allowList)
                    .build()
            }
        }

        // Then
        assertTrue(result.isSuccess)

        // Verify the modified options were used
        val capturedOptions = optionsSlot.captured
        assertEquals("secure.example.com", capturedOptions.rpId)
        assertEquals(120.0, capturedOptions.timeoutSeconds) // Modified timeout
        assertEquals(2, capturedOptions.allowList?.size)

        // Verify response with real base64 encoding
        val response = result.getOrThrow()
        assertEquals("response-credential-id", response[Constants.FIELD_ID]?.jsonPrimitive?.content)
        assertEquals("response-raw-id".toByteArray().toBase64(), response[Constants.FIELD_RAW_ID]?.jsonPrimitive?.content)
        assertEquals("cross-platform", response[Constants.FIELD_AUTHENTICATOR_ATTACHMENT]?.jsonPrimitive?.content)

        val responseObject = response[Constants.FIELD_RESPONSE]?.jsonObject
        assertNotNull(responseObject)
        assertEquals("auth-data-test".toByteArray().toBase64(),
            responseObject[Constants.FIELD_AUTHENTICATOR_DATA]?.jsonPrimitive?.content)
        assertEquals("client-data-test".toByteArray().toBase64(),
            responseObject[Constants.FIELD_CLIENT_DATA_JSON]?.jsonPrimitive?.content)
        assertEquals("signature-test".toByteArray().toBase64(),
            responseObject[Constants.FIELD_SIGNATURE]?.jsonPrimitive?.content)
        assertEquals("user-handle-test".toByteArray().toBase64(),
            responseObject[Constants.FIELD_USER_HANDLE]?.jsonPrimitive?.content)
    }

    @Test
    fun `authenticate with DSL customizer should handle both customizers when needed`() = runTest {
        // Given
        fidoClient = FidoClient {
            useFido2Client = true
        }

        val inputJson = buildJsonObject {
            put(Constants.FIELD_RP_ID, "multi.example.com")
            put(Constants.FIELD_CHALLENGE, "bXVsdGktY3VzdG9taXplcg") // "multi-customizer" base64
            put(Constants.FIELD_TIMEOUT, 75000.0)
        }

        val mockGmsCredential = mockk<GmsPublicKeyCredential> {
            every { id } returns "multi-test-id"
            every { rawId } returns "multi-raw-id".toByteArray()
            every { type } returns "public-key"
            every { authenticatorAttachment } returns "platform"
        }

        val mockAuthenticatorResponse = mockk<AuthenticatorAssertionResponse> {
            every { authenticatorData } returns "multi-auth".toByteArray()
            every { clientDataJSON } returns "multi-client".toByteArray()
            every { signature } returns "multi-sig".toByteArray()
            every { userHandle } returns null
        }

        every { mockGmsCredential.response } returns mockAuthenticatorResponse

        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockGmsCredential

        var requestOptionsCustomized = false
        var getOptionCustomized = false

        // When - using DSL with both customizers (though only one will be used based on useFido2Client)
        val result = fidoClient.authenticate(inputJson) {
            onPublicKeyCredentialRequestOptions { options ->
                requestOptionsCustomized = true
                // Verify base64 challenge decoding
                val expectedChallenge = "bXVsdGktY3VzdG9taXplcg".urlSafeDecode()
                assertTrue(options.challenge.contentEquals(expectedChallenge))
                assertEquals("multi-customizer", String(options.challenge))
                options
            }

            onGetPublicKeyCredentialOption { option ->
                getOptionCustomized = true
                option
            }
        }

        // Then
        assertTrue(result.isSuccess)
        assertTrue(requestOptionsCustomized) // Should be called since useFido2Client = true
        assertTrue(!getOptionCustomized) // Should NOT be called since useFido2Client = true

        val capturedOptions = optionsSlot.captured
        assertEquals("multi.example.com", capturedOptions.rpId)
        assertEquals(75.0, capturedOptions.timeoutSeconds)
    }

    @Test
    fun `authenticate with DSL customizer should handle exception in customizer gracefully`() = runTest {
        // Given
        fidoClient = FidoClient {
            useFido2Client = true
        }

        val inputJson = buildJsonObject {
            put(Constants.FIELD_RP_ID, "error.example.com")
            put(Constants.FIELD_CHALLENGE, "ZXJyb3ItdGVzdA") // "error-test" base64
        }

        // When - customizer throws exception
        val result = fidoClient.authenticate(inputJson) {
            onPublicKeyCredentialRequestOptions { options ->
                // Verify the options are parsed correctly before throwing
                assertEquals("error.example.com", options.rpId)
                val expectedChallenge = "ZXJyb3ItdGVzdA".urlSafeDecode()
                assertTrue(options.challenge.contentEquals(expectedChallenge))

                throw RuntimeException("Customizer intentionally failed")
            }
        }

        // Then
        assertTrue(result.isFailure)
        assertEquals("Customizer intentionally failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `authenticate with DSL customizer should handle invalid base64 in input JSON`() = runTest {
        // Given
        fidoClient = FidoClient {
            useFido2Client = true
        }

        val inputJson = buildJsonObject {
            put(Constants.FIELD_RP_ID, "invalid.example.com")
            put(Constants.FIELD_CHALLENGE, "invalid-base64-@#$") // Invalid base64
            put(Constants.FIELD_TIMEOUT, 30000.0)
        }

        // When - should fail during JSON parsing before customizer is called
        val result = fidoClient.authenticate(inputJson) {
            onPublicKeyCredentialRequestOptions { options ->
                // This should not be reached due to base64 decoding error
                options
            }
        }

        // Then
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
        // Should be a base64 decoding error
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `authenticate with DSL customizer should work with minimal JSON and default values`() = runTest {
        // Given
        fidoClient = FidoClient {
            useFido2Client = true
        }

        // Minimal JSON with only required fields
        val inputJson = buildJsonObject {
            put(Constants.FIELD_CHALLENGE, "bWluaW1hbA") // "minimal" base64
            // No rpId (should default to "")
            // No timeout (should use default)
            // No allowCredentials (should result in empty list)
        }

        val mockGmsCredential = mockk<GmsPublicKeyCredential> {
            every { id } returns "minimal-test-id"
            every { rawId } returns byteArrayOf(0xFF.toByte(), 0xEE.toByte())
            every { type } returns "public-key"
            every { authenticatorAttachment } returns null
        }

        val mockAuthenticatorResponse = mockk<AuthenticatorAssertionResponse> {
            every { authenticatorData } returns byteArrayOf(0xAA.toByte(), 0xBB.toByte())
            every { clientDataJSON } returns byteArrayOf(0xCC.toByte(), 0xDD.toByte())
            every { signature } returns byteArrayOf(0x11, 0x22)
            every { userHandle } returns null
        }

        every { mockGmsCredential.response } returns mockAuthenticatorResponse

        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockGmsCredential

        // When
        val result = fidoClient.authenticate(inputJson) {
            onPublicKeyCredentialRequestOptions { options ->
                // Verify defaults and minimal parsing
                assertEquals("", options.rpId) // Default for missing rpId
                assertEquals(Constants.DEFAULT_TIMEOUT / 1000, options.timeoutSeconds) // Default timeout
                assertEquals(0, options.allowList?.size) // Empty allowCredentials

                // Verify challenge decoding
                val expectedChallenge = "bWluaW1hbA".urlSafeDecode()
                assertTrue(options.challenge.contentEquals(expectedChallenge))
                assertEquals("minimal", String(options.challenge))

                options
            }
        }

        // Then
        assertTrue(result.isSuccess)

        val response = result.getOrThrow()
        assertEquals("minimal-test-id", response[Constants.FIELD_ID]?.jsonPrimitive?.content)
        assertEquals(byteArrayOf(0xFF.toByte(), 0xEE.toByte()).toBase64(),
            response[Constants.FIELD_RAW_ID]?.jsonPrimitive?.content)
        assertEquals(Constants.AUTHENTICATOR_PLATFORM,
            response[Constants.FIELD_AUTHENTICATOR_ATTACHMENT]?.jsonPrimitive?.content)
    }

    @Test
    fun `authenticate with DSL customizer should properly switch between API paths`() = runTest {
        // Test credential manager path (useFido2Client = false)
        val credentialManagerClient = FidoClient {
            useFido2Client = false
        }

        val inputJson = buildJsonObject {
            put(Constants.FIELD_RP_ID, "switch.example.com")
            put(Constants.FIELD_CHALLENGE, "c3dpdGNoLXRlc3Q") // "switch-test" base64
        }

        val expectedResponse = """{"id":"switch-id","type":"public-key"}"""
        val mockPublicKeyCredential = mockk<PublicKeyCredential> {
            every { authenticationResponseJson } returns expectedResponse
        }
        val mockGetResponse = mockk<GetCredentialResponse> {
            every { credential } returns mockPublicKeyCredential
        }

        coEvery {
            mockCredentialManager.getCredential(any(), any<GetCredentialRequest>())
        } returns mockGetResponse

        var requestOptionsUsed = false
        var getOptionUsed = false

        // When
        val result = credentialManagerClient.authenticate(inputJson) {
            onPublicKeyCredentialRequestOptions { options ->
                requestOptionsUsed = true
                options
            }

            onGetPublicKeyCredentialOption { option ->
                getOptionUsed = true
                // Verify JSON content with real base64
                assertTrue(option.requestJson.contains("switch.example.com"))
                assertTrue(option.requestJson.contains("c3dpdGNoLXRlc3Q"))
                option
            }
        }

        // Then
        assertTrue(result.isSuccess)
        assertTrue(!requestOptionsUsed) // Should NOT be called since useFido2Client = false
        assertTrue(getOptionUsed) // Should be called since useFido2Client = false

        val response = result.getOrThrow()
        assertEquals("switch-id", response["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `authenticate with empty DSL block should use default behavior with real base64`() = runTest {
        // Given
        fidoClient = FidoClient {
            useFido2Client = true
        }

        val testChallenge = "default-behavior-test".toByteArray()
        val inputJson = buildJsonObject {
            put(Constants.FIELD_RP_ID, "default.example.com")
            put(Constants.FIELD_CHALLENGE, testChallenge.toBase64())
            put(Constants.FIELD_TIMEOUT, 50000.0)
        }

        val mockGmsCredential = mockk<GmsPublicKeyCredential> {
            every { id } returns "default-test-id"
            every { rawId } returns "default-raw".toByteArray()
            every { type } returns "public-key"
            every { authenticatorAttachment } returns "platform"
        }

        val mockAuthenticatorResponse = mockk<AuthenticatorAssertionResponse> {
            every { authenticatorData } returns "default-auth".toByteArray()
            every { clientDataJSON } returns "default-client".toByteArray()
            every { signature } returns "default-sig".toByteArray()
            every { userHandle } returns null
        }

        every { mockGmsCredential.response } returns mockAuthenticatorResponse

        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockGmsCredential

        // When - empty DSL block (should use defaults)
        val result = fidoClient.authenticate(inputJson) {
            // Empty block - should not modify anything
        }

        // Then
        assertTrue(result.isSuccess)

        // Verify original values are preserved
        val capturedOptions = optionsSlot.captured
        assertEquals("default.example.com", capturedOptions.rpId)
        assertEquals(50.0, capturedOptions.timeoutSeconds)
        assertTrue(capturedOptions.challenge.contentEquals(testChallenge))

        // Verify response with real base64 encoding
        val response = result.getOrThrow()
        assertEquals("default-test-id", response[Constants.FIELD_ID]?.jsonPrimitive?.content)
        assertEquals("default-raw".toByteArray().toBase64(),
            response[Constants.FIELD_RAW_ID]?.jsonPrimitive?.content)

        val responseObject = response[Constants.FIELD_RESPONSE]?.jsonObject
        assertNotNull(responseObject)
        assertEquals("default-auth".toByteArray().toBase64(),
            responseObject[Constants.FIELD_AUTHENTICATOR_DATA]?.jsonPrimitive?.content)
        assertEquals("default-client".toByteArray().toBase64(),
            responseObject[Constants.FIELD_CLIENT_DATA_JSON]?.jsonPrimitive?.content)
        assertEquals("default-sig".toByteArray().toBase64(),
            responseObject[Constants.FIELD_SIGNATURE]?.jsonPrimitive?.content)
    }

    @Test
    fun `authenticate should map JSON challenge to PublicKeyCredentialRequestOptions correctly`() = runTest {
        // Given
        val challenge = "dGVzdC1jaGFsbGVuZ2U"
        val requestOptions = buildJsonObject {
            put("challenge", challenge)
            put("rpId", "example.com")
        }

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

        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockCredential

        // When
        val result = FidoClient { useFido2Client = true }.authenticate(requestOptions)

        // Then
        assertTrue(result.isSuccess)
        val capturedOptions = optionsSlot.captured
        assertEquals("example.com", capturedOptions.rpId)
        // Challenge should be decoded from base64
        assertTrue(capturedOptions.challenge.contentEquals(challenge.urlSafeDecode()))
    }

    @Test
    fun `authenticate should map JSON timeout to PublicKeyCredentialRequestOptions correctly`() = runTest {
        // Given
        val timeoutMs = 60000.0 // 60 seconds in milliseconds
        val requestOptions = buildJsonObject {
            put("challenge", "dGVzdC1jaGFsbGVuZ2U")
            put("rpId", "example.com")
            put("timeout", timeoutMs)
        }

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

        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockCredential

        // When
        val result = FidoClient { useFido2Client = true }.authenticate(requestOptions)

        // Then
        assertTrue(result.isSuccess)
        val capturedOptions = optionsSlot.captured
        assertEquals(60.0, capturedOptions.timeoutSeconds) // Should be converted to seconds
    }

    @Test
    fun `authenticate should use default timeout when not provided in JSON`() = runTest {
        // Given
        val requestOptions = buildJsonObject {
            put("challenge", "dGVzdC1jaGFsbGVuZ2U")
            put("rpId", "example.com")
        }

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

        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockCredential

        // When
        val result = FidoClient { useFido2Client = true }.authenticate(requestOptions)

        // Then
        assertTrue(result.isSuccess)
        val capturedOptions = optionsSlot.captured
        // Should use default timeout converted to seconds
        assertEquals(Constants.DEFAULT_TIMEOUT / 1000, capturedOptions.timeoutSeconds)
    }

    @Test
    fun `authenticate should map allowCredentials from JSON to PublicKeyCredentialRequestOptions`() = runTest {
        // Given
        val credentialId1 = "Y3JlZGVudGlhbElkMQ" // credentialId1 in base64
        val credentialId2 = "Y3JlZGVudGlhbElkMg" // credentialId2 in base64

        val requestOptions = buildJsonObject {
            put("challenge", "dGVzdC1jaGFsbGVuZ2U")
            put("rpId", "example.com")
            put("allowCredentials", kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject {
                    put("type", "public-key")
                    put("id", credentialId1)
                })
                add(buildJsonObject {
                    put("type", "public-key")
                    put("id", credentialId2)
                })
            })
        }

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

        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockCredential

        // When
        val result = FidoClient { useFido2Client = true }.authenticate(requestOptions)

        // Then
        assertTrue(result.isSuccess)
        val capturedOptions = optionsSlot.captured
        assertEquals(2, capturedOptions.allowList?.size)

        val firstDescriptor = capturedOptions.allowList!![0]
        assertEquals(PublicKeyCredentialType.PUBLIC_KEY, firstDescriptor.type)
        assertTrue(firstDescriptor.id.contentEquals(credentialId1.urlSafeDecode()))
        assertEquals(1, firstDescriptor.transports?.size)
        assertEquals(Transport.INTERNAL, firstDescriptor.transports!![0])

        val secondDescriptor = capturedOptions.allowList!![1]
        assertEquals(PublicKeyCredentialType.PUBLIC_KEY, secondDescriptor.type)
        assertTrue(secondDescriptor.id.contentEquals(credentialId2.urlSafeDecode()))
    }

    @Test
    fun `authenticate should handle empty allowCredentials array`() = runTest {
        // Given
        val requestOptions = buildJsonObject {
            put("challenge", "dGVzdC1jaGFsbGVuZ2U")
            put("rpId", "example.com")
            put("allowCredentials", kotlinx.serialization.json.buildJsonArray { })
        }

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

        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockCredential

        // When
        val result = FidoClient { useFido2Client = true }.authenticate(requestOptions)

        // Then
        assertTrue(result.isSuccess)
        val capturedOptions = optionsSlot.captured
        assertTrue(capturedOptions.allowList!!.isEmpty())
    }

    @Test
    fun `authenticate should handle missing allowCredentials field`() = runTest {
        // Given
        val requestOptions = buildJsonObject {
            put("challenge", "dGVzdC1jaGFsbGVuZ2U")
            put("rpId", "example.com")
            // No allowCredentials field
        }

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

        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockCredential

        // When
        val result = FidoClient { useFido2Client = true }.authenticate(requestOptions)

        // Then
        assertTrue(result.isSuccess)
        val capturedOptions = optionsSlot.captured
        assertTrue(capturedOptions.allowList!!.isEmpty())
    }

    @Test
    fun `authenticate should handle malformed allowCredentials gracefully`() = runTest {
        // Given
        val requestOptions = buildJsonObject {
            put("challenge", "dGVzdC1jaGFsbGVuZ2U")
            put("rpId", "example.com")
            put("allowCredentials", kotlinx.serialization.json.buildJsonArray {
                // Valid credential
                add(buildJsonObject {
                    put("type", "public-key")
                    put("id", "dmFsaWRJZA")
                })
                // Invalid credential - missing type
                add(buildJsonObject {
                    put("id", "aW52YWxpZElk")
                })
                // Invalid credential - missing id
                add(buildJsonObject {
                    put("type", "public-key")
                })
            })
        }

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

        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockCredential

        // When
        val result = FidoClient { useFido2Client = true }.authenticate(requestOptions)

        // Then
        assertTrue(result.isSuccess)
        val capturedOptions = optionsSlot.captured
        // Should only include the valid credential
        assertEquals(1, capturedOptions.allowList?.size)
        assertEquals(PublicKeyCredentialType.PUBLIC_KEY, capturedOptions.allowList!![0].type)
        assertTrue(capturedOptions.allowList!![0].id.contentEquals("dmFsaWRJZA".urlSafeDecode()))
    }

    @Test
    fun `authenticate should use customizer to modify PublicKeyCredentialRequestOptions`() = runTest {
        // Given
        val requestOptions = buildJsonObject {
            put("challenge", "dGVzdC1jaGFsbGVuZ2U")
            put("rpId", "example.com")
        }

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

        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockCredential

        // When
        val result = FidoClient { useFido2Client = true }.authenticate(requestOptions) {
            onPublicKeyCredentialRequestOptions { options ->
                // Customize the options
                PublicKeyCredentialRequestOptions.Builder()
                    .setAllowList(options.allowList)
                    .setRpId("customized.example.com") // Override rpId
                    .setChallenge(options.challenge)
                    .setTimeoutSeconds(120.0) // Override timeout
                    .build()
            }
        }

        // Then
        assertTrue(result.isSuccess)
        val capturedOptions = optionsSlot.captured
        assertEquals("customized.example.com", capturedOptions.rpId)
        assertEquals(120.0, capturedOptions.timeoutSeconds)
    }

    @Test
    fun `authenticate should use customizer to modify GetPublicKeyCredentialOption when useFido2Client is false`() = runTest {
        // Given
        val requestOptions = buildJsonObject {
            put("challenge", "dGVzdC1jaGFsbGVuZ2U")
            put("rpId", "example.com")
        }

        val expectedResponse = """{"id":"test-id","response":{"authenticatorData":"test-auth-data"}}"""
        val mockPublicKeyCredential = mockk<PublicKeyCredential> {
            every { authenticationResponseJson } returns expectedResponse
        }
        val mockGetResponse = mockk<GetCredentialResponse> {
            every { credential } returns mockPublicKeyCredential
        }

        val requestSlot = slot<GetCredentialRequest>()
        coEvery {
            mockCredentialManager.getCredential(
                context = mockActivity,
                request = capture(requestSlot)
            )
        } returns mockGetResponse

        // When
        val result = FidoClient { useFido2Client = false }.authenticate(requestOptions) {
            onGetPublicKeyCredentialOption { option ->
                // Customize the option by creating a new one with modified JSON
                val customJson = buildJsonObject {
                    put("challenge", "customized-challenge")
                    put("rpId", "customized.example.com")
                }
                GetPublicKeyCredentialOption(customJson.toString())
            }
        }

        // Then
        assertTrue(result.isSuccess)
        val capturedRequest = requestSlot.captured
        val credentialOption = capturedRequest.credentialOptions.first() as GetPublicKeyCredentialOption
        assertTrue(credentialOption.requestJson.contains("customized-challenge"))
        assertTrue(credentialOption.requestJson.contains("customized.example.com"))
    }

    @Test
    fun `authenticate should handle missing rpId gracefully`() = runTest {
        // Given
        val requestOptions = buildJsonObject {
            put("challenge", "dGVzdC1jaGFsbGVuZ2U")
            // No rpId field
        }

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

        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockCredential

        // When
        val result = FidoClient { useFido2Client = true }.authenticate(requestOptions)

        // Then
        assertTrue(result.isSuccess)
        val capturedOptions = optionsSlot.captured
        assertEquals("", capturedOptions.rpId) // Should default to empty string
    }

    @Test
    fun `authenticate should handle missing challenge gracefully`() = runTest {
        // Given
        val requestOptions = buildJsonObject {
            put("rpId", "example.com")
            // No challenge field
        }

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

        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockCredential

        // When
        val result = FidoClient { useFido2Client = true }.authenticate(requestOptions)

        // Then
        assertTrue(result.isSuccess)
        val capturedOptions = optionsSlot.captured
        assertTrue(capturedOptions.challenge.contentEquals(byteArrayOf())) // Should default to empty byte array
    }

    @Test
    fun `authenticate should convert complex allowCredentials JSON structure correctly`() = runTest {
        // Given
        val credentialId = "complex-credential-id-base64"
        val requestOptions = buildJsonObject {
            put("challenge", "dGVzdC1jaGFsbGVuZ2U")
            put("rpId", "example.com")
            put("allowCredentials", kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject {
                    put("type", "public-key")
                    put("id", credentialId)
                    // Additional fields that should be ignored
                    put("transports", kotlinx.serialization.json.buildJsonArray {
                        add(JsonPrimitive("usb"))
                        add(JsonPrimitive("nfc"))
                    })
                })
            })
        }

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

        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
        val optionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(optionsSlot)) } returns mockCredential

        // When
        val result = FidoClient { useFido2Client = true }.authenticate(requestOptions)

        // Then
        assertTrue(result.isSuccess)
        val capturedOptions = optionsSlot.captured
        assertEquals(1, capturedOptions.allowList?.size)

        val descriptor = capturedOptions.allowList!![0]
        assertEquals(PublicKeyCredentialType.PUBLIC_KEY, descriptor.type)
        assertTrue(descriptor.id.contentEquals(credentialId.urlSafeDecode()))
        // Should always use INTERNAL transport regardless of JSON transports
        assertEquals(1, descriptor.transports?.size)
        assertEquals(Transport.INTERNAL, descriptor.transports!![0])
    }

    @Test
    fun `authenticate should handle both API paths based on useFido2Client config`() = runTest {
        // Test Credential Manager path (useFido2Client = false)
        val requestOptions = buildJsonObject {
            put("challenge", "dGVzdC1jaGFsbGVuZ2U")
            put("rpId", "example.com")
        }

        val expectedResponse = """{"id":"test-id","response":{"authenticatorData":"test-auth-data"}}"""
        val mockPublicKeyCredential = mockk<PublicKeyCredential> {
            every { authenticationResponseJson } returns expectedResponse
        }
        val mockGetResponse = mockk<GetCredentialResponse> {
            every { credential } returns mockPublicKeyCredential
        }

        val credentialManagerRequestSlot = slot<GetCredentialRequest>()
        coEvery {
            mockCredentialManager.getCredential(
                context = mockActivity,
                request = capture(credentialManagerRequestSlot)
            )
        } returns mockGetResponse

        // When using Credential Manager
        val credentialManagerResult = FidoClient { useFido2Client = false }.authenticate(requestOptions)

        // Then
        assertTrue(credentialManagerResult.isSuccess)
        val capturedRequest = credentialManagerRequestSlot.captured
        assertTrue(capturedRequest.credentialOptions.first() is GetPublicKeyCredentialOption)

        // Test Google Play Services path (useFido2Client = true)
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

        mockkStatic("com.pingidentity.fido.FidoNonDiscoverableKt")
        val gmsOptionsSlot = slot<PublicKeyCredentialRequestOptions>()
        coEvery { getPublicKeyCredential(any(), capture(gmsOptionsSlot)) } returns mockCredential

        // When using Google Play Services
        val gmsResult = FidoClient { useFido2Client = true }.authenticate(requestOptions)

        // Then
        assertTrue(gmsResult.isSuccess)
        val capturedOptions = gmsOptionsSlot.captured
        assertEquals("example.com", capturedOptions.rpId)
        assertTrue(capturedOptions.challenge.contentEquals("dGVzdC1jaGFsbGVuZ2U".urlSafeDecode()))
    }
}
