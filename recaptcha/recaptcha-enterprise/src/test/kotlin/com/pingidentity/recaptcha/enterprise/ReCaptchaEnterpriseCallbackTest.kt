/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recaptcha.enterprise

import android.app.Application
import android.content.Context
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaClient
import com.pingidentity.android.ContextProvider
import com.pingidentity.journey.plugin.Journey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReCaptchaEnterpriseCallbackTest {

    private val mockContext = mockk<Context>()
    private val mockApplication = mockk<Application>()
    private val mockJourney = mockk<Journey>()
    private val mockProvider = mockk<RecaptchaClientProvider>()
    private val mockClient = mockk<RecaptchaClient>()

    @BeforeTest
    fun setup() {
        // Setup context mocking
        every { mockContext.applicationContext } returns mockApplication
        mockkObject(ContextProvider)
        every { ContextProvider.context } returns mockApplication
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `init should set reCaptchaSiteKey when property name matches`() {
        // Arrange
        val callback = ReCaptchaEnterpriseCallback()
        val siteKey = "test-site-key-123"
        val jsonValue = JsonPrimitive(siteKey)

        // Act
        callback.initForTest("recaptchaSiteKey", jsonValue)

        // Assert
        assertEquals(siteKey, callback.reCaptchaSiteKey)
    }

    @Test
    fun `init should ignore other property names`() {
        // Arrange
        val callback = ReCaptchaEnterpriseCallback()
        val originalSiteKey = callback.reCaptchaSiteKey
        val jsonValue = JsonPrimitive("some-value")

        // Act
        callback.initForTest("otherProperty", jsonValue)

        // Assert
        assertEquals(originalSiteKey, callback.reCaptchaSiteKey)
    }

    @Test
    fun `verify should successfully return token with default configuration`() = runTest {
        // Arrange
        val callback = spyk(ReCaptchaEnterpriseCallback())
        callback.journey = mockJourney
        val siteKey = "test-site-key"
        val token = "verification-token-123"
        val expectedJsonObject = buildJsonObject { put("token", token) }

        callback.initForTest("recaptchaSiteKey", JsonPrimitive(siteKey))

        coEvery { mockProvider.fetchClient(mockApplication, siteKey) } returns mockClient
        coEvery { mockProvider.execute(mockClient, RecaptchaAction.LOGIN, 10000L) } returns token
        every { callback.input(token) } returns expectedJsonObject

        // Act
        val result = callback.verify {
            setProvider(mockProvider)
        }

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(expectedJsonObject, result.getOrNull())
        coVerify { mockProvider.fetchClient(mockApplication, siteKey) }
        coVerify { mockProvider.execute(mockClient, RecaptchaAction.LOGIN, 10000L) }
    }

    @Test
    fun `verify should use custom configuration parameters`() = runTest {
        // Arrange
        val callback = spyk(ReCaptchaEnterpriseCallback())
        callback.journey = mockJourney
        val siteKey = "custom-site-key"
        val token = "custom-token"
        val customTimeout = 15000L
        val expectedJsonObject = buildJsonObject { put("token", token) }

        callback.initForTest("recaptchaSiteKey", JsonPrimitive(siteKey))

        coEvery { mockProvider.fetchClient(mockApplication, siteKey) } returns mockClient
        coEvery { mockProvider.execute(mockClient, RecaptchaAction.SIGNUP, customTimeout) } returns token
        every { callback.input(token) } returns expectedJsonObject

        // Act
        val result = callback.verify {
            recaptchaAction = RecaptchaAction.SIGNUP
            timeoutInMills = customTimeout
            setProvider(mockProvider)
        }

        // Assert
        assertTrue(result.isSuccess)
        coVerify { mockProvider.execute(mockClient, RecaptchaAction.SIGNUP, customTimeout) }
    }

    @Test
    fun `verify should throw IllegalStateException when provider is not set`() = runTest {
        // Arrange
        val callback = ReCaptchaEnterpriseCallback()
        callback.journey = mockJourney

        // Act & Assert
        val exception = assertFailsWith<IllegalStateException> {
            callback.verify {
                // No provider set
            }
        }
        assertEquals("RecaptchaClientProvider is not set", exception.message)
    }

    @Test
    fun `verify should return failure when fetchClient throws exception`() = runTest {
        // Arrange
        val callback = spyk(ReCaptchaEnterpriseCallback())
        callback.journey = mockJourney
        val siteKey = "test-site-key"
        val expectedException = RuntimeException("Network error")

        callback.initForTest("recaptchaSiteKey", JsonPrimitive(siteKey))
        coEvery { mockProvider.fetchClient(mockApplication, siteKey) } throws expectedException

        // Act
        val result = callback.verify {
            setProvider(mockProvider)
        }

        // Assert
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }

    @Test
    fun `verify should return failure when execute returns null token`() = runTest {
        // Arrange
        val callback = spyk(ReCaptchaEnterpriseCallback())
        callback.journey = mockJourney
        val siteKey = "test-site-key"

        callback.initForTest("recaptchaSiteKey", JsonPrimitive(siteKey))
        coEvery { mockProvider.fetchClient(mockApplication, siteKey) } returns mockClient
        coEvery { mockProvider.execute(mockClient, RecaptchaAction.LOGIN, 10000L) } returns null

        // Act
        val result = callback.verify {
            setProvider(mockProvider)
        }

        // Assert
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as IllegalStateException
        assertEquals("Invalid captcha token", exception.message)
    }

    @Test
    fun `verify should return failure when execute throws exception`() = runTest {
        // Arrange
        val callback = spyk(ReCaptchaEnterpriseCallback())
        callback.journey = mockJourney
        val siteKey = "test-site-key"
        val expectedException = RuntimeException("ReCaptcha execution failed")

        callback.initForTest("recaptchaSiteKey", JsonPrimitive(siteKey))
        coEvery { mockProvider.fetchClient(mockApplication, siteKey) } returns mockClient
        coEvery { mockProvider.execute(mockClient, RecaptchaAction.LOGIN, 10000L) } throws expectedException

        // Act
        val result = callback.verify {
            setProvider(mockProvider)
        }

        // Assert
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }

    @Test
    fun `verify should work with all RecaptchaAction types`() = runTest {
        // Test different actions
        val actions = listOf(
            RecaptchaAction.LOGIN,
            RecaptchaAction.SIGNUP,
            RecaptchaAction.custom("CUSTOM_ACTION")
        )

        for (action in actions) {
            // Arrange
            val callback = spyk(ReCaptchaEnterpriseCallback())
            callback.journey = mockJourney
            val siteKey = "test-site-key"
            val token = "token-for-$action"
            val expectedJsonObject = buildJsonObject { put("token", token) }

            callback.initForTest("recaptchaSiteKey", JsonPrimitive(siteKey))
            coEvery { mockProvider.fetchClient(mockApplication, siteKey) } returns mockClient
            coEvery { mockProvider.execute(mockClient, action, 10000L) } returns token
            every { callback.input(token) } returns expectedJsonObject

            // Act
            val result = callback.verify {
                recaptchaAction = action
                setProvider(mockProvider)
            }

            // Assert
            assertTrue(result.isSuccess, "Failed for action: $action")
            coVerify { mockProvider.execute(mockClient, action, 10000L) }
        }
    }

    @Test
    fun `verify should use site key from initialization in config`() = runTest {
        // Arrange
        val callback = spyk(ReCaptchaEnterpriseCallback())
        callback.journey = mockJourney
        val originalSiteKey = "original-site-key"
        val token = "verification-token"
        val expectedJsonObject = buildJsonObject { put("token", token) }

        callback.initForTest("recaptchaSiteKey", JsonPrimitive(originalSiteKey))

        val configSlot = slot<ReCaptchaEnterpriseConfig>()
        coEvery { mockProvider.fetchClient(mockApplication, originalSiteKey) } returns mockClient
        coEvery { mockProvider.execute(mockClient, RecaptchaAction.LOGIN, 10000L) } returns token
        every { callback.input(token) } returns expectedJsonObject

        // Act
        val result = callback.verify {
            setProvider(mockProvider)
            // Verify that the config has the correct site key
            assertEquals(originalSiteKey, this.reCaptchaSiteKey)
        }

        // Assert
        assertTrue(result.isSuccess)
        coVerify { mockProvider.fetchClient(mockApplication, originalSiteKey) }
    }

    @Test
    fun `ReCaptchaEnterpriseConfig should have correct default values`() {
        // Arrange & Act
        val config = ReCaptchaEnterpriseConfig()

        // Assert
        assertEquals("", config.reCaptchaSiteKey)
        assertEquals(RecaptchaAction.LOGIN, config.recaptchaAction)
        assertEquals(10000L, config.timeoutInMills)
        assertEquals(null, config.recaptchaClientProvider)
    }

    @Test
    fun `ReCaptchaEnterpriseConfig setProvider should set the provider`() {
        // Arrange
        val config = ReCaptchaEnterpriseConfig()
        val provider = mockk<RecaptchaClientProvider>()

        // Act
        config.setProvider(provider)

        // Assert
        assertEquals(provider, config.recaptchaClientProvider)
    }

    @Test
    fun `verify should work with DefaultRecaptchaClientProvider`() = runTest {
        // Arrange
        val callback = spyk(ReCaptchaEnterpriseCallback())
        callback.journey = mockJourney
        val siteKey = "test-site-key"
        val token = "default-provider-token"
        val expectedJsonObject = buildJsonObject { put("token", token) }
        val defaultProvider = spyk(DefaultRecaptchaClientProvider())

        callback.initForTest("recaptchaSiteKey", JsonPrimitive(siteKey))
        coEvery { defaultProvider.fetchClient(mockApplication, siteKey) } returns mockClient
        coEvery { defaultProvider.execute(mockClient, RecaptchaAction.LOGIN, 10000L) } returns token
        every { callback.input(token) } returns expectedJsonObject

        // Act
        val result = callback.verify {
            setProvider(defaultProvider)
        }

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(expectedJsonObject, result.getOrNull())
        coVerify { defaultProvider.fetchClient(mockApplication, siteKey) }
        coVerify { defaultProvider.execute(mockClient, RecaptchaAction.LOGIN, 10000L) }
    }
}