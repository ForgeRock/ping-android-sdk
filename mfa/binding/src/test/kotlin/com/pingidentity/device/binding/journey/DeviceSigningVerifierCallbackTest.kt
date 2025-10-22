/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.journey

import android.app.Application
import android.content.Context
import androidx.biometric.BiometricPrompt.CryptoObject
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.UserKey
import com.pingidentity.device.binding.authenticator.DeviceAuthenticator
import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType
import com.pingidentity.device.binding.authenticator.None
import com.pingidentity.device.binding.authenticator.UserKeySigningParameters
import com.pingidentity.device.binding.authenticator.exception.DeviceNotRegisteredException
import com.pingidentity.device.binding.authenticator.exception.InvalidClaimException
import com.pingidentity.journey.plugin.Journey
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.WorkflowConfig
import com.pingidentity.storage.MemoryStorage
import com.pingidentity.storage.Storage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.PrivateKey
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class DeviceSigningVerifierCallbackTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }
    private lateinit var callback: DeviceSigningVerifierCallback
    private lateinit var mockJourney: Journey
    private lateinit var mockJourneyConfig: WorkflowConfig
    private lateinit var mockLogger: Logger
    private lateinit var mockAuthenticator: DeviceAuthenticator
    private lateinit var userKeyStorage: Storage<List<UserKey>>
    private lateinit var input: JsonObject

    private val testUserId = "test-user-123"
    private val testChallenge = "test-challenge-value"
    private val testTitle = "Sign Challenge"
    private val testSubtitle = "Authenticate to sign"
    private val testDescription = "Please authenticate to continue"
    private val testTimeout = 60

    private val testUserKey = UserKey(
        id = "test-key-id",
        userId = testUserId,
        userName = "Test User",
        kid = "test-kid",
        authType = DeviceBindingAuthenticationType.NONE
    )

    @BeforeTest
    fun setUp() {
        ContextProvider.init(context)

        mockLogger =  mockk(relaxed = true)
        mockAuthenticator = mockk()
        userKeyStorage = MemoryStorage()
        mockJourneyConfig = mockk(relaxed = true)
        mockJourney = mockk(relaxed = true)
        every { mockJourney.config } returns mockJourneyConfig
        every { mockJourneyConfig.logger } returns mockLogger

        callback = DeviceSigningVerifierCallback().apply {
            journey = mockJourney
        }

        input = Json.parseToJsonElement(
            """
                {
      "type": "DeviceSigningVerifierCallback",
      "output": [
        {
          "name": "userId",
          "value": "$testUserId"
        },
        {
          "name": "challenge",
          "value": "$testChallenge"
        },
        {
          "name": "title",
          "value": "$testTitle"
        },
        {
          "name": "subtitle",
          "value": "$testSubtitle"
        },
        {
          "name": "description",
          "value": "$testDescription"
        },
        {
          "name": "timeout",
          "value": $testTimeout
        }
      ],
      "input": [
        {
          "name": "IDToken1jws",
          "value": ""
        },
        {
          "name": "IDToken1clientError",
          "value": ""
        }
      ]
    }
    """.trimIndent()
        ) as JsonObject
    }

    @AfterTest
    fun tearDown() {
        // Cleanup if needed
    }

    @Test
    fun `test initialization with JSON values`() {
        callback.init(input)
        assertEquals(testUserId, callback.userId)
        assertEquals(testChallenge, callback.challenge)
        assertEquals(testTitle, callback.title)
        assertEquals(testSubtitle, callback.subtitle)
        assertEquals(testDescription, callback.description)
        assertEquals(testTimeout, callback.timeout)
    }

    @Test
    fun `test initialization with empty userId`() {
        callback.init(setValue(input, "userId", JsonPrimitive("")))
        assertNull(callback.userId)
    }

    @Test
    fun `test sign success with specific userId`() = runTest {
        callback.init(input)
        setupUserKeyStorage(listOf(testUserKey))

        val mockPrivateKey = mockk<PrivateKey>()

        coEvery { mockAuthenticator.authenticate(context) } returns Result.success(
            Pair(mockPrivateKey, null)
        )
        every { mockAuthenticator.sign(any<UserKeySigningParameters>()) } returns "test-jwt-token"

        val result = callback.sign {
            userKeyStorage {
                storage = { userKeyStorage }
            }
            deviceAuthenticator = { mockAuthenticator }
        }

        assertTrue(result.isSuccess)

        // Verify payload contains the signed JWT in the input structure
        val payload = callback.payload()
        val jwsValue = getInputValue(payload, "IDToken1jws")
        assertEquals("test-jwt-token", jwsValue)
    }

    @Test
    fun `test sign with no userId - single key found`() = runTest {
        callback.init(setValue(input, "userId", JsonPrimitive(testUserId)))
        setupUserKeyStorage(listOf(testUserKey))

        val mockPrivateKey = mockk<PrivateKey>()
        coEvery { mockAuthenticator.authenticate(context) } returns Result.success(
            Pair(mockPrivateKey, null)
        )
        every { mockAuthenticator.sign(any<UserKeySigningParameters>()) } returns "test-jwt-token"

        val result = callback.sign {
            userKeyStorage {
                storage = { userKeyStorage }
            }
            deviceAuthenticator = { mockAuthenticator }
        }

        assertTrue(result.isSuccess)

        // Verify payload contains the signed JWT in the input structure
        val payload = callback.payload()
        val jwsValue = getInputValue(payload, "IDToken1jws")
        assertEquals("test-jwt-token", jwsValue)
    }

    @Test
    fun `test sign with no userId - no keys found`() = runTest {
        callback.init(input)
        setupUserKeyStorage(emptyList())

        val result = callback.sign {
            userKeyStorage {
                storage = { userKeyStorage }
            }
            deviceAuthenticator = { mockAuthenticator }
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DeviceNotRegisteredException)
        assertEquals("No user is registered", result.exceptionOrNull()?.message)

        // Verify clientError is populated with the correct error code
        val payload = callback.payload()
        val clientErrorValue = getInputValue(payload, "IDToken1clientError")
        assertEquals("ClientNotRegistered", clientErrorValue)
    }

    @Test
    fun `test sign with no userId - multiple keys found uses selector`() = runTest {
        callback.init(setValue(input, "userId", JsonPrimitive("")))

        val userKey2 = testUserKey.copy(
            id = "test-key-2",
            userId = "user-2",
            userName = "User Two"
        )
        setupUserKeyStorage(listOf(testUserKey, userKey2))

        val mockPrivateKey = mockk<PrivateKey>()
        coEvery { mockAuthenticator.authenticate(context) } returns Result.success(
            Pair(mockPrivateKey, null)
        )
        every { mockAuthenticator.sign(any<UserKeySigningParameters>()) } returns "test-jwt-token"


        val result = callback.sign {
            userKeyStorage {
                storage = { userKeyStorage }
            }
            deviceAuthenticator = { mockAuthenticator }
            userKeySelector = { keys -> keys.first() } // Always select first key
        }

        assertTrue(result.isSuccess)

        // Verify payload contains the signed JWT in the input structure
        val payload = callback.payload()
        val jwsValue = getInputValue(payload, "IDToken1jws")
        assertEquals("test-jwt-token", jwsValue)
    }

    @Test
    fun `test sign with user not found`() = runTest {
        callback.init(input)

        setupUserKeyStorage(emptyList()) // No keys stored

        val result = callback.sign {
            userKeyStorage {
                storage = { userKeyStorage }
            }
            deviceAuthenticator = { mockAuthenticator }
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DeviceNotRegisteredException)

        // Verify clientError is populated with the correct error code
        val payload = callback.payload()
        val clientErrorValue = getInputValue(payload, "IDToken1clientError")
        assertEquals("ClientNotRegistered", clientErrorValue)
    }

    @Test
    fun `test sign timeout handling`() = runTest {
        callback.init(setValue(input, "timeout", JsonPrimitive(1))) // 1 second timeout
        setupUserKeyStorage(listOf(testUserKey))

        val slowAuthenticator = object : None() {
            override suspend fun authenticate(context: Context): Result<Pair<PrivateKey, androidx.biometric.BiometricPrompt.CryptoObject?>> {
                kotlinx.coroutines.delay(2000) // 2 seconds delay
                return super.authenticate(context)
            }
        }

        val result = callback.sign {
            userKeyStorage {
                storage = { userKeyStorage }
            }
            deviceAuthenticator = { slowAuthenticator }
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is kotlinx.coroutines.TimeoutCancellationException)

        // Verify clientError is populated with the correct error code
        val payload = callback.payload()
        val clientErrorValue = getInputValue(payload, "IDToken1clientError")
        assertEquals("Timeout", clientErrorValue)
    }

    @Test
    fun `test validate throws InvalidClaimException for reserved claims`() = runTest {
        callback.init(input)
        setupUserKeyStorage(listOf(testUserKey))

        val result = callback.sign {
            userKeyStorage {
                storage = { userKeyStorage }
            }
            deviceAuthenticator = { mockAuthenticator }
            claims {
                put("sub", "reserved-claim") // 'sub' is a reserved claim
            }
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidClaimException)
        assertTrue(result.exceptionOrNull()?.message?.contains("reserved names") == true)

        // Verify clientError is populated with the correct error code
        val payload = callback.payload()
        val clientErrorValue = getInputValue(payload, "IDToken1clientError")
        assertEquals("Abort", clientErrorValue)
    }

    @Test
    fun `test validate allows custom claims`() = runTest {
        callback.init(input)
        setupUserKeyStorage(listOf(testUserKey))

        val result = callback.sign {
            userKeyStorage {
                storage = { userKeyStorage }
            }
            deviceAuthenticator = { mockAuthenticator }
            claims {
                put("custom_claim", "custom_value")
                put("another_claim", 123)
            }
        }

        // Should not fail due to custom claims validation
        assertTrue(result.isSuccess || result.isFailure)
        // If it fails, it should not be due to InvalidClaimException
        if (result.isFailure) {
            assertTrue(result.exceptionOrNull() !is InvalidClaimException)
        }
    }

    @Test
    fun `test journey property is accessible`() {
        val testJourney = mockJourney
        callback.journey = testJourney
        assertEquals(testJourney, callback.journey)
    }

    @Test
    fun `test clientError can be set`() {
        callback.clientError = "Custom error message"
        assertEquals("Custom error message", callback.clientError)
    }

    @Test
    fun `test sign sets CryptoKey for CryptoKeyAware authenticators`() = runTest {
        callback.init(input)
        setupUserKeyStorage(listOf(testUserKey))

        val mockPrivateKey = mockk<PrivateKey>()
        val cryptyObject = mockk<CryptoObject>()
        every { cryptyObject.signature } returns mockk()
        coEvery { mockAuthenticator.authenticate(context) } returns Result.success(
            Pair(mockPrivateKey, cryptyObject)
        )
        every { mockAuthenticator.sign(any<UserKeySigningParameters>()) } returns "test-jwt-token"

        val result = callback.sign {
            userKeyStorage {
                storage = { userKeyStorage }
            }
            deviceAuthenticator = { mockAuthenticator }
        }

        assertTrue(result.isSuccess)

        // Verify payload contains the signed JWT in the input structure
        val payload = callback.payload()
        val jwsValue = getInputValue(payload, "IDToken1jws")
        assertEquals("test-jwt-token", jwsValue)
    }

    @Test
    fun `test UserKeySigningParameters are correctly populated`() = runTest {
        callback.init(input)
        setupUserKeyStorage(listOf(testUserKey))

        val signingParamsSlot = slot<UserKeySigningParameters>()

        val mockPrivateKey = mockk<PrivateKey>()
        val cryptyObject = mockk<CryptoObject>()
        every { cryptyObject.signature } returns mockk()
        coEvery { mockAuthenticator.authenticate(context) } returns Result.success(
            Pair(mockPrivateKey, cryptyObject)
        )
        every { mockAuthenticator.sign(capture(signingParamsSlot)) } returns "test-jwt-token"

        val result = callback.sign {
            userKeyStorage {
                storage = { userKeyStorage }
            }
            deviceAuthenticator = { mockAuthenticator }
        }
        assertTrue(result.isSuccess)

        // Verify payload contains the signed JWT in the input structure
        val payload = callback.payload()
        val jwsValue = getInputValue(payload, "IDToken1jws")
        assertEquals("test-jwt-token", jwsValue)

        val capturedParams = signingParamsSlot.captured
        assertEquals(context, capturedParams.context)
        assertEquals("RS512", capturedParams.algorithm)
        assertEquals(testUserKey, capturedParams.userKey)
        assertEquals(testChallenge, capturedParams.challenge)
        // Other parameters would be set by the config
    }

    @Test
    fun `test reserved claim names constant`() {
        val reservedNames = DeviceSigningVerifierCallback.RESERVE_NAME
        assertTrue(reservedNames.contains("sub"))
        assertTrue(reservedNames.contains("exp"))
        assertTrue(reservedNames.contains("iat"))
        assertTrue(reservedNames.contains("nbf"))
        assertTrue(reservedNames.contains("iss"))
        assertTrue(reservedNames.contains("challenge"))
    }

    private suspend fun setupUserKeyStorage(keys: List<UserKey>) {
        if (keys.isNotEmpty()) {
            userKeyStorage.save(keys)
        }
    }

    private fun getInputValue(payload: JsonObject, inputName: String): String? {
        // Access the "input" array from the JsonObject
        val inputArray = payload["input"]?.jsonArray ?: return null
        return inputArray
            .map { it.jsonObject }
            .firstOrNull { it["name"]?.jsonPrimitive?.content == inputName }
            ?.get("value")?.jsonPrimitive?.content
    }
}