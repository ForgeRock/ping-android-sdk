/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.journey

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.UserKey
import com.pingidentity.device.binding.authenticator.Attestation
import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType
import com.pingidentity.device.binding.authenticator.KeyPair
import com.pingidentity.device.binding.authenticator.NoneAuthenticator
import com.pingidentity.device.binding.authenticator.SigningParameters
import com.pingidentity.device.binding.authenticator.UserKeySigningParameters
import com.pingidentity.device.binding.authenticator.exception.DeviceNotSupportedException
import com.pingidentity.device.id.DeviceIdentifier
import com.pingidentity.journey.plugin.Journey
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.WorkflowConfig
import com.pingidentity.storage.MemoryStorage
import com.pingidentity.storage.Storage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class DeviceBindingCallbackTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }
    private lateinit var callback: DeviceBindingCallback
    private lateinit var mockJourney: Journey
    private lateinit var mockJourneyConfig: WorkflowConfig
    private lateinit var mockLogger: Logger
    private lateinit var deviceBindingConfig: DeviceBindingConfig
    private lateinit var mockAuthenticator: NoneAuthenticator
    private lateinit var userKeyStorage: Storage<List<UserKey>>
    private lateinit var input: JsonObject
    private lateinit var mockDeviceIdentifier: DeviceIdentifier

    private val testUserId = "test-user-123"
    private val testUserName = "testuser"
    private val testChallenge = "dGVzdC1jaGFsbGVuZ2U="
    private val testTitle = "Device Binding"
    private val testSubtitle = "Authenticate to bind device"
    private val testDescription = "Please authenticate to continue"

    @BeforeTest
    fun setUp() {
        ContextProvider.init(context)

        mockLogger = mockk(relaxed = true)
        mockJourneyConfig = mockk(relaxed = true)
        mockJourney = mockk(relaxed = true)
        deviceBindingConfig = DeviceBindingConfig()
        mockAuthenticator = mockk(relaxed = true)
        userKeyStorage = MemoryStorage()
        mockDeviceIdentifier = mockk(relaxed = true)

        every { mockJourney.config } returns mockJourneyConfig
        every { mockJourneyConfig.logger } returns mockLogger
        coEvery { mockDeviceIdentifier.id() } returns "test-device-id"

        callback = DeviceBindingCallback().apply {
            journey = mockJourney
        }

        input = Json.parseToJsonElement(
            """
                {
      "type": "DeviceBindingCallback",
      "output": [
        {
          "name": "userId",
          "value": "$testUserId"
        },
        {
          "name": "username",
          "value": "$testUserName"
        },
        {
          "name": "authenticationType",
          "value": "BIOMETRIC_ALLOW_FALLBACK"
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
          "value": 60
        },
        {
          "name": "attestation",
          "value": true
        }
      ],
      "input": [
        {
          "name": "IDToken1jws",
          "value": ""
        },
        {
          "name": "IDToken1deviceName",
          "value": ""
        },
        {
          "name": "IDToken1deviceId",
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
        unmockkObject(ContextProvider)
        unmockkStatic(Base64::class)
    }

    @Test
    fun `test initialization with JSON values`() {
        callback.init(input)

        assertEquals(testUserId, callback.userId)
        assertEquals(testUserName, callback.userName)
        assertEquals(testChallenge, callback.challenge)
        assertEquals(
            DeviceBindingAuthenticationType.BIOMETRIC_ALLOW_FALLBACK,
            callback.deviceBindingAuthenticationType
        )
        assertEquals(testTitle, callback.title)
        assertEquals(testSubtitle, callback.subtitle)
        assertEquals(testDescription, callback.description)
        assertEquals(60, callback.timeout)
        assertTrue(callback.attestation is Attestation.Default)
    }

    @Test
    fun `test initialization with attestation false`() {
        callback.init(setValue(input, "attestation", JsonPrimitive(false)))
        assertTrue(callback.attestation is Attestation.None)
    }

    @Test
    fun `test bind success with authenticator`() = runTest {
        // Initialize callback
        callback.init(input)
        val mockKeyPair = createMockKeyPair()
        val mockPrivateKey = mockk<PrivateKey>()

        // Mock authenticator behavior
        every { mockAuthenticator.isSupported(context, any()) } returns true
        coEvery { mockAuthenticator.register(context, any()) } returns Result.success(mockKeyPair)
        coEvery { mockAuthenticator.authenticate(context) } returns Result.success(
            Pair(mockPrivateKey, null)
        )
        every { mockAuthenticator.sign(any<UserKeySigningParameters>()) } returns "test-jwt-token"
        every { mockAuthenticator.sign(any<SigningParameters>()) } returns "test-jwt-token"
        coEvery { mockAuthenticator.deleteKeys() } returns Unit

        val result = callback.bind {
            deviceIdentifier = mockDeviceIdentifier
            deviceAuthenticator = { mockAuthenticator }
            userKeyStorage {
                storage = { userKeyStorage }
            }
        }

        assertTrue(result.isSuccess)
        assertEquals("test-jwt-token", result.getOrThrow())

        // Verify payload contains the signed JWT in the input structure
        val payload = callback.payload()
        val jwsValue = getInputValue(payload, "IDToken1jws")
        assertEquals("test-jwt-token", jwsValue)

        verify { mockLogger.i(match { it.contains("Starting bind process") }) }
        verify { mockLogger.i(match { it.contains("Successfully bound device") }) }
        coVerify { mockAuthenticator.register(context, any()) }
        coVerify { mockAuthenticator.authenticate(context) }

        assertEquals(1, userKeyStorage.get()?.size)
        val storedKey = userKeyStorage.get()?.first()
        assertEquals(testUserId, storedKey?.userId)
        assertEquals(mockKeyPair.keyAlias, storedKey?.id)
        assertEquals(testUserName, storedKey?.userName)
        assertEquals(DeviceBindingAuthenticationType.BIOMETRIC_ALLOW_FALLBACK, storedKey?.authType)

    }

    @Test
    fun `test bind failure when device not supported`() = runTest {
        callback.init(input)

        every { mockAuthenticator.isSupported(context, any()) } returns false

        val result = callback.bind {
            deviceIdentifier = mockDeviceIdentifier
            deviceAuthenticator = { mockAuthenticator }
            userKeyStorage {
                storage = { userKeyStorage }
            }
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DeviceNotSupportedException)

        // Verify clientError is populated with the correct error code
        val payload = callback.payload()
        val clientErrorValue = getInputValue(payload, "IDToken1clientError")
        assertEquals("Unsupported", clientErrorValue)
    }

    @Test
    fun `test bind failure during registration`() = runTest {
        callback.init(input)

        every { mockAuthenticator.isSupported(context, any()) } returns true
        coEvery { mockAuthenticator.register(context, any()) } returns Result.failure(
            RuntimeException("Registration failed")
        )
        coEvery { mockAuthenticator.deleteKeys() } returns Unit

        val result = callback.bind {
            deviceIdentifier = mockDeviceIdentifier
            deviceAuthenticator = { mockAuthenticator }
            userKeyStorage {
                storage = { userKeyStorage }
            }
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Registration failed", result.exceptionOrNull()?.message)
        assertNull(userKeyStorage.get())

        // Verify clientError is populated with the correct error code
        val payload = callback.payload()
        val clientErrorValue = getInputValue(payload, "IDToken1clientError")
        assertEquals("Abort", clientErrorValue)
    }

    @Test
    fun `test bind failure during authentication with cleanup`() = runTest {
        callback.init(input)

        val mockKeyPair = createMockKeyPair()

        every { mockAuthenticator.isSupported(context, any()) } returns true
        coEvery { mockAuthenticator.register(context, any()) } returns Result.success(mockKeyPair)
        coEvery { mockAuthenticator.authenticate(context) } returns Result.failure(
            RuntimeException(
                "Authentication failed"
            )
        )
        coEvery { mockAuthenticator.deleteKeys() } returns Unit

        val result = callback.bind {
            deviceIdentifier = mockDeviceIdentifier
            deviceAuthenticator = { mockAuthenticator }
            userKeyStorage {
                storage = { userKeyStorage }
            }
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Authentication failed", result.exceptionOrNull()?.message)

        coVerify { mockAuthenticator.deleteKeys() }
        assertNull(userKeyStorage.get())

        // Verify clientError is populated with the correct error code
        val payload = callback.payload()
        val clientErrorValue = getInputValue(payload, "IDToken1clientError")
        assertEquals("Abort", clientErrorValue)
    }

    @Test
    fun `test bind timeout handling`() = runTest {
        callback.init(setValue(input, "timeout", JsonPrimitive(1))) // Set timeout to 1 second

        every { mockAuthenticator.isSupported(context, any()) } returns true
        // Simulate slow registration
        coEvery { mockAuthenticator.register(context, any()) } coAnswers {
            kotlinx.coroutines.delay(2000) // 2 seconds delay
            Result.success(createMockKeyPair())
        }
        coEvery { mockAuthenticator.deleteKeys() } returns Unit

        val result = callback.bind {
            deviceIdentifier = mockDeviceIdentifier
            deviceAuthenticator = { mockAuthenticator }
            userKeyStorage {
                storage = { userKeyStorage }
            }
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is kotlinx.coroutines.TimeoutCancellationException)
        assertNull(userKeyStorage.get())

        // Verify clientError is populated with the correct error code
        val payload = callback.payload()
        val clientErrorValue = getInputValue(payload, "IDToken1clientError")
        assertEquals("Timeout", clientErrorValue)
    }

    @Test
    fun `test signing parameters are correctly populated`() = runTest {
        callback.init(input)

        val mockKeyPair = createMockKeyPair()
        val mockPrivateKey = mockk<PrivateKey>()
        val signingParamsSlot = slot<SigningParameters>()

        every { mockAuthenticator.isSupported(context, any()) } returns true
        coEvery { mockAuthenticator.register(context, any()) } returns Result.success(mockKeyPair)
        coEvery { mockAuthenticator.authenticate(context) } returns Result.success(
            Pair(
                mockPrivateKey,
                null
            )
        )
        every { mockAuthenticator.sign(capture(signingParamsSlot)) } returns "test-jwt"
        coEvery { mockAuthenticator.deleteKeys() } returns Unit

        val result = callback.bind {
            deviceIdentifier = mockDeviceIdentifier
            deviceAuthenticator = { mockAuthenticator }
            userKeyStorage {
                storage = { userKeyStorage }
            }
        }

        assertTrue(result.isSuccess)

        // Verify payload contains the signed JWT in the input structure
        val payload = callback.payload()
        val jwsValue = getInputValue(payload, "IDToken1jws")
        assertEquals("test-jwt", jwsValue)

        val capturedParams = signingParamsSlot.captured
        assertEquals(context, capturedParams.context)
        assertEquals("RS512", capturedParams.algorithm)
        assertTrue(mockKeyPair === capturedParams.keyPair)
        assertEquals(testUserId, capturedParams.userId)
        assertEquals(testChallenge, capturedParams.challenge)
        assertTrue(capturedParams.attestation is Attestation.Default)
    }

    @Test
    fun `test journey property is lateinit and accessible`() {
        val testJourney = mockk<Journey>()
        callback.journey = testJourney
        assertEquals(testJourney, callback.journey)
    }

    @Test
    fun `test userName can be overridden`() {
        callback.init(input)
        assertEquals("testuser", callback.userName)

        callback.userName = "overridden"
        assertEquals("overridden", callback.userName)
    }

    @Test
    fun `test clientError can be set`() {
        callback.clientError = "Custom error message"
        assertEquals("Custom error message", callback.clientError)
    }

    private fun createMockKeyPair(): KeyPair {
        val mockPublicKey = mockk<RSAPublicKey>()
        val mockPrivateKey = mockk<PrivateKey>()
        return KeyPair(mockPublicKey, mockPrivateKey, "test-key-alias")
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

fun setValue(inputJson: JsonObject, key: String, value: JsonPrimitive): JsonObject {
    // 1. Get the original 'output' array. If it doesn't exist, return the input unchanged.
    val outputArray = inputJson["output"]?.jsonArray ?: return inputJson

    // 2. Create a new list of JSON elements by transforming the original array.
    val newOutputElements = outputArray.map { element ->
        val obj = element.jsonObject
        // Check if this is the object we want to change.
        if (obj["name"]?.jsonPrimitive?.content == key) {
            // If it is, build a new JsonObject with the value set to false.
            buildJsonObject {
                put("name", JsonPrimitive(key))
                put("value", value)
            }
        } else {
            // Otherwise, return the original element without any changes.
            element
        }
    }

    // 3. Build a new top-level JsonObject by combining the original content
    //    with the newly created 'output' array.
    return JsonObject(inputJson + ("output" to JsonArray(newOutputElements)))
}
