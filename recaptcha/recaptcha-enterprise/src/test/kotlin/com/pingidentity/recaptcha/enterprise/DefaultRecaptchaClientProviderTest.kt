/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recaptcha.enterprise

import android.app.Application
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DefaultRecaptchaClientProviderTest {

    private val mockApplication = mockk<Application>()
    private val mockClient = mockk<RecaptchaClient>()

    @BeforeTest
    fun setup() {
        // Mock the static Recaptcha object
        mockkObject(Recaptcha)
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `fetchClient should return RecaptchaClient when successful`() = runTest {
        // Arrange
        val provider = DefaultRecaptchaClientProvider()
        val siteKey = "test-site-key-123"

        coEvery { Recaptcha.fetchClient(mockApplication, siteKey) } returns mockClient

        // Act
        val result = provider.fetchClient(mockApplication, siteKey)

        // Assert
        assertEquals(mockClient, result)
        coVerify { Recaptcha.fetchClient(mockApplication, siteKey) }
    }

    @Test
    fun `fetchClient should throw exception when Recaptcha fetchClient fails`() = runTest {
        // Arrange
        val provider = DefaultRecaptchaClientProvider()
        val siteKey = "invalid-site-key"
        val expectedException = RuntimeException("Network error")

        coEvery { Recaptcha.fetchClient(mockApplication, siteKey) } throws expectedException

        // Act & Assert
        val thrownException = assertFailsWith<RuntimeException> {
            provider.fetchClient(mockApplication, siteKey)
        }
        assertEquals(expectedException, thrownException)
        coVerify { Recaptcha.fetchClient(mockApplication, siteKey) }
    }

    @Test
    fun `fetchClient should work with different site keys`() = runTest {
        // Arrange
        val provider = DefaultRecaptchaClientProvider()
        val siteKey1 = "site-key-1"
        val siteKey2 = "site-key-2"
        val mockClient1 = mockk<RecaptchaClient>()
        val mockClient2 = mockk<RecaptchaClient>()

        coEvery { Recaptcha.fetchClient(mockApplication, siteKey1) } returns mockClient1
        coEvery { Recaptcha.fetchClient(mockApplication, siteKey2) } returns mockClient2

        // Act
        val result1 = provider.fetchClient(mockApplication, siteKey1)
        val result2 = provider.fetchClient(mockApplication, siteKey2)

        // Assert
        assertEquals(mockClient1, result1)
        assertEquals(mockClient2, result2)
        coVerify { Recaptcha.fetchClient(mockApplication, siteKey1) }
        coVerify { Recaptcha.fetchClient(mockApplication, siteKey2) }
    }

    @Test
    fun `execute should return token when client execution is successful`() = runTest {
        // Arrange
        val provider = DefaultRecaptchaClientProvider()
        val action = RecaptchaAction.LOGIN
        val timeout = 10000L
        val expectedToken = "valid-token-123"

        coEvery { mockClient.execute(action, timeout) } returns Result.success(expectedToken)

        // Act
        val result = provider.execute(mockClient, action, timeout)

        // Assert
        assertEquals(expectedToken, result)
        coVerify { mockClient.execute(action, timeout) }
    }

    @Test
    fun `execute should return null when client execution fails`() = runTest {
        // Arrange
        val provider = DefaultRecaptchaClientProvider()
        val action = RecaptchaAction.LOGIN
        val timeout = 10000L
        val exception = RuntimeException("Execution failed")

        coEvery { mockClient.execute(action, timeout) } returns Result.failure(exception)

        // Act
        val result = provider.execute(mockClient, action, timeout)

        // Assert
        assertNull(result)
        coVerify { mockClient.execute(action, timeout) }
    }

    @Test
    fun `execute should work with different RecaptchaAction types`() = runTest {
        // Arrange
        val provider = DefaultRecaptchaClientProvider()
        val timeout = 10000L
        val actions = listOf(
            RecaptchaAction.LOGIN,
            RecaptchaAction.SIGNUP,
            RecaptchaAction.custom("CUSTOM_ACTION")
        )

        // Act & Assert for each action
        for (action in actions) {
            val expectedToken = "token-for-$action"
            coEvery { mockClient.execute(action, timeout) } returns Result.success(expectedToken)

            val result = provider.execute(mockClient, action, timeout)

            assertEquals(expectedToken, result)
            coVerify { mockClient.execute(action, timeout) }
        }
    }

    @Test
    fun `execute should work with different timeout values`() = runTest {
        // Arrange
        val provider = DefaultRecaptchaClientProvider()
        val action = RecaptchaAction.LOGIN
        val timeouts = listOf(5000L, 10000L, 15000L, 30000L)

        // Act & Assert for each timeout
        for (timeout in timeouts) {
            val expectedToken = "token-for-timeout-$timeout"
            coEvery { mockClient.execute(action, timeout) } returns Result.success(expectedToken)

            val result = provider.execute(mockClient, action, timeout)

            assertEquals(expectedToken, result)
            coVerify { mockClient.execute(action, timeout) }
        }
    }

    @Test
    fun `execute should handle empty token from successful Result`() = runTest {
        // Arrange
        val provider = DefaultRecaptchaClientProvider()
        val action = RecaptchaAction.LOGIN
        val timeout = 10000L

        coEvery { mockClient.execute(action, timeout) } returns Result.success("")

        // Act
        val result = provider.execute(mockClient, action, timeout)

        // Assert
        assertEquals("", result.toString())
        coVerify { mockClient.execute(action, timeout) }
    }

    @Test
    fun `execute should throw exception when client execute throws uncaught exception`() = runTest {
        // Arrange
        val provider = DefaultRecaptchaClientProvider()
        val action = RecaptchaAction.LOGIN
        val timeout = 10000L
        val expectedException = RuntimeException("Unexpected error")

        coEvery { mockClient.execute(action, timeout) } throws expectedException

        // Act & Assert
        val thrownException = assertFailsWith<RuntimeException> {
            provider.execute(mockClient, action, timeout)
        }
        assertEquals(expectedException, thrownException)
        coVerify { mockClient.execute(action, timeout) }
    }

    @Test
    fun `provider should handle edge case with empty site key`() = runTest {
        // Arrange
        val provider = DefaultRecaptchaClientProvider()
        val emptySiteKey = ""

        coEvery { Recaptcha.fetchClient(mockApplication, emptySiteKey) } returns mockClient

        // Act
        val result = provider.fetchClient(mockApplication, emptySiteKey)

        // Assert
        assertEquals(mockClient, result)
        coVerify { Recaptcha.fetchClient(mockApplication, emptySiteKey) }
    }

    @Test
    fun `provider should handle very long site key`() = runTest {
        // Arrange
        val provider = DefaultRecaptchaClientProvider()
        val longSiteKey = "a".repeat(1000) // Very long site key

        coEvery { Recaptcha.fetchClient(mockApplication, longSiteKey) } returns mockClient

        // Act
        val result = provider.fetchClient(mockApplication, longSiteKey)

        // Assert
        assertEquals(mockClient, result)
        coVerify { Recaptcha.fetchClient(mockApplication, longSiteKey) }
    }

    @Test
    fun `execute should handle zero timeout`() = runTest {
        // Arrange
        val provider = DefaultRecaptchaClientProvider()
        val action = RecaptchaAction.LOGIN
        val zeroTimeout = 0L
        val expectedToken = "token-with-zero-timeout"

        coEvery { mockClient.execute(action, zeroTimeout) } returns Result.success(expectedToken)

        // Act
        val result = provider.execute(mockClient, action, zeroTimeout)

        // Assert
        assertEquals(expectedToken, result)
        coVerify { mockClient.execute(action, zeroTimeout) }
    }

    @Test
    fun `execute should handle negative timeout`() = runTest {
        // Arrange
        val provider = DefaultRecaptchaClientProvider()
        val action = RecaptchaAction.LOGIN
        val negativeTimeout = -1000L
        val expectedToken = "token-with-negative-timeout"

        coEvery { mockClient.execute(action, negativeTimeout) } returns Result.success(expectedToken)

        // Act
        val result = provider.execute(mockClient, action, negativeTimeout)

        // Assert
        assertEquals(expectedToken, result)
        coVerify { mockClient.execute(action, negativeTimeout) }
    }

    @Test
    fun `provider should be reusable for multiple operations`() = runTest {
        // Arrange
        val provider = DefaultRecaptchaClientProvider()
        val siteKey = "reusable-site-key"
        val action = RecaptchaAction.LOGIN
        val timeout = 10000L
        val token1 = "token-1"
        val token2 = "token-2"

        coEvery { Recaptcha.fetchClient(mockApplication, siteKey) } returns mockClient
        coEvery { mockClient.execute(action, timeout) } returnsMany listOf(
            Result.success(token1),
            Result.success(token2)
        )

        // Act
        val client = provider.fetchClient(mockApplication, siteKey)
        val result1 = provider.execute(client, action, timeout)
        val result2 = provider.execute(client, action, timeout)

        // Assert
        assertEquals(mockClient, client)
        assertEquals(token1, result1)
        assertEquals(token2, result2)
        coVerify(exactly = 1) { Recaptcha.fetchClient(mockApplication, siteKey) }
        coVerify(exactly = 2) { mockClient.execute(action, timeout) }
    }
}
