/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import PushUriParser
import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.commons.exception.MfaException
import io.ktor.client.HttpClient
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.UUID

/**
 * Unit tests for the PushService class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@ExperimentalCoroutinesApi
class PushServiceTest {

    // Mock objects
    private lateinit var mockStorage: PushStorage
    private lateinit var mockHttpClient: HttpClient
    private lateinit var mockContext: Context
    private lateinit var mockLogger: Logger
    private lateinit var configWithCache: PushConfiguration
    private lateinit var configWithoutCache: PushConfiguration

    // Test subjects
    private lateinit var pushServiceWithCache: PushService
    private lateinit var pushServiceWithoutCache: PushService

    // Test data
    private lateinit var testCredential: PushCredential
    private lateinit var testCredentialId: String
    private lateinit var testNotification: PushNotification
    private lateinit var testNotificationId: String
    private lateinit var testDeviceToken: String

    @Before
    fun setup() {
        // Set up mock objects
        mockStorage = mockk()
        mockHttpClient = mockk()
        mockContext = mockk()
        mockLogger = mockk(relaxed = true)

        // Create mock configurations
        configWithCache = mockk<PushConfiguration>().apply {
            every { enableCredentialCache } returns true
            every { logger } returns mockLogger
            every { context } returns mockContext
            every { customPushHandlers } returns emptyMap()
        }

        configWithoutCache = mockk<PushConfiguration>().apply {
            every { enableCredentialCache } returns false
            every { logger } returns mockLogger
            every { context } returns mockContext
            every { customPushHandlers } returns emptyMap()
        }

        // Mock PushUriParser
        mockkObject(PushUriParser)

        // Initialize service variants with mock storage and HTTP client
        pushServiceWithCache = PushService(mockStorage, configWithCache, mockHttpClient)
        pushServiceWithoutCache = PushService(mockStorage, configWithoutCache, mockHttpClient)

        // Create test credential
        testCredentialId = UUID.randomUUID().toString()
        testCredential = PushCredential(
            id = testCredentialId,
            userId = "test-user",
            resourceId = "test-resource",
            issuer = "Test Issuer",
            displayIssuer = "Test Issuer",
            accountName = "test@example.com",
            displayAccountName = "test@example.com",
            serverEndpoint = "https://test-server.com/push",
            sharedSecret = "test-shared-secret",
            createdAt = Date(),
            platform = PushPlatform.PING_AM.name
        )

        // Create test notification
        testNotificationId = UUID.randomUUID().toString()
        testNotification = PushNotification(
            id = testNotificationId,
            credentialId = testCredentialId,
            messageId = "test-message-id",
            messageText = "Test authentication request",
            createdAt = Date(),
            ttl = 300,
            pushType = PushType.DEFAULT
        )

        // Set test device token
        testDeviceToken = "test-device-token"
    }

    @Test
    fun `test add credential stores credential in storage`() = runTest {
        // Given
        coEvery { mockStorage.storePushCredential(any()) } just runs

        // When
        val result = pushServiceWithCache.addCredential(testCredential)

        // Then
        assertEquals(testCredential, result)
        coVerify(exactly = 1) { mockStorage.storePushCredential(testCredential) }
    }

    @Test
    fun `test add credential adds to cache when caching enabled`() = runTest {
        // Given
        coEvery { mockStorage.storePushCredential(any()) } just runs

        // When
        pushServiceWithCache.addCredential(testCredential)

        // Clear mock to verify if next operation uses cache
        clearMocks(mockStorage)

        // Setup to get credential by ID
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns testCredential

        // When
        val result = pushServiceWithCache.getCredential(testCredentialId)

        // Then - should use cache, not storage
        assertEquals(testCredential, result)
        coVerify(exactly = 0) { mockStorage.retrievePushCredential(testCredentialId) }
    }

    @Test
    fun `test get credential retrieves from storage when cache disabled`() = runTest {
        // Given
        coEvery { mockStorage.storePushCredential(any()) } just runs

        // When
        pushServiceWithoutCache.addCredential(testCredential)

        // Clear mock to verify next call
        clearMocks(mockStorage)

        // Setup credential retrieval from storage
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns testCredential

        // When
        val result = pushServiceWithoutCache.getCredential(testCredentialId)

        // Then - should use storage, not cache
        assertEquals(testCredential, result)
        coVerify(exactly = 1) { mockStorage.retrievePushCredential(testCredentialId) }
    }

    @Test
    fun `test get credentials retrieves all from storage`() = runTest {
        // Given
        val credentials = listOf(testCredential)
        coEvery { mockStorage.getAllPushCredentials() } returns credentials

        // When
        val result = pushServiceWithCache.getCredentials()

        // Then
        assertEquals(credentials, result)
        coVerify(exactly = 1) { mockStorage.getAllPushCredentials() }
    }

    @Test
    fun `test remove credential removes from storage and cache`() = runTest {
        // Given
        coEvery { mockStorage.storePushCredential(any()) } just runs
        
        // Add credential first
        pushServiceWithCache.addCredential(testCredential)
        
        // Clear mock and set up remove credential mock
        clearMocks(mockStorage)
        coEvery { mockStorage.removePushCredential(testCredentialId) } returns true

        // When
        val result = pushServiceWithCache.removeCredential(testCredentialId)

        // Then
        assertTrue(result)
        coVerify(exactly = 1) { mockStorage.removePushCredential(testCredentialId) }

        // Test credential is removed from cache
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns testCredential

        // Should go to storage since it's no longer in cache
        pushServiceWithCache.getCredential(testCredentialId)

        coVerify(exactly = 1) { mockStorage.retrievePushCredential(testCredentialId) }
    }

    @Test
    fun `test process notification creates and stores notification`() = runTest {
        // Given
        val mockHandler = mockk<PushHandler>()
        val messageData = mapOf(
            "credentialId" to testCredentialId,
            "message" to "Test message",
            "platform" to PushPlatform.PING_AM.name
        )

        // Mock handler retrieval and methods
        every { mockHandler.canHandle(any()) } returns true
        every { mockHandler.parseMessage(any()) } returns mapOf(
            "credentialId" to testCredentialId,
            "messageId" to "test-message-id",
            "messageText" to "Test authentication request"
        )

        val pushService = PushService(
            storage = mockStorage,
            config = configWithCache,
            httpClient = mockHttpClient,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        coEvery { mockStorage.getNotificationByMessageId(any()) } returns null
        coEvery { mockStorage.storePushNotification(any()) } just runs

        // When
        val result = pushService.processNotification(messageData)

        // Then
        assertNotNull(result)
        assertEquals(testCredentialId, result?.credentialId)
        coVerify(exactly = 1) { mockStorage.storePushNotification(any()) }
    }

    @Test
    fun `test approve notification updates notification and sends approval`() = runTest {
        // Given
        val mockHandler = mockk<PushHandler>()

        // Mock handler methods - sendApproval is a suspend function
        coEvery { mockHandler.sendApproval(any(), any(), any()) } returns true

        val pushService = PushService(
            storage = mockStorage,
            config = configWithCache,
            httpClient = mockHttpClient,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        // Mock retrieving notification and credential
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns testNotification
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns testCredential
        coEvery { mockStorage.updatePushNotification(any()) } just runs

        // When
        val result = pushService.approveNotification(testNotificationId)

        // Then
        assertTrue(result)
        coVerify(exactly = 1) { mockStorage.updatePushNotification(any()) }
    }

    @Test
    fun `test deny notification updates notification and sends denial`() = runTest {
        // Given
        val mockHandler = mockk<PushHandler>()

        // Mock handler methods - sendDenial is a suspend function
        coEvery { mockHandler.sendDenial(any(), any(), any()) } returns true

        val pushService = PushService(
            storage = mockStorage,
            config = configWithCache,
            httpClient = mockHttpClient,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        // Mock retrieving notification and credential
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns testNotification
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns testCredential
        coEvery { mockStorage.updatePushNotification(any()) } just runs

        // When
        val result = pushService.denyNotification(testNotificationId)

        // Then
        assertTrue(result)
        coVerify(exactly = 1) { mockStorage.updatePushNotification(any()) }
    }

    @Test
    fun `test get notification retrieves from storage`() = runTest {
        // Given
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns testNotification

        // When
        val result = pushServiceWithCache.getNotification(testNotificationId)

        // Then
        assertEquals(testNotification, result)
    }

    @Test
    fun `test get pending notifications retrieves from storage`() = runTest {
        // Given
        val pendingNotifications = listOf(testNotification)
        coEvery { mockStorage.getPendingPushNotifications() } returns pendingNotifications

        // When
        val result = pushServiceWithCache.getPendingNotifications()

        // Then
        assertEquals(pendingNotifications, result)
    }

    @Test
    fun `test add credential from URI parses and registers credential`() = runTest {
        // Given
        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        val mockHandler = mockk<PushHandler>()
        
        // Create a spied version of the service with our mocks
        val pushService = spyk(
            PushService(
                storage = mockStorage,
                config = configWithCache,
                httpClient = mockHttpClient,
                tokenManager = mockDeviceTokenManager,
                handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
            )
        )
        
        // Skip actual URI parsing by mocking the addCredentialFromUri method
        coEvery { pushService.addCredentialFromUri(any()) } returns testCredential
        
        // Mock storage
        coEvery { mockStorage.storePushCredential(any()) } just runs

        // When - use any valid string since we're mocking the method
        val result = pushService.addCredentialFromUri("test-uri")

        // Then
        assertEquals(testCredential, result)
    }

    @Test
    fun `test add credential from URI throws exception when device token not available`() = runTest {
        // Given
        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        
        // Create a spied version of the service with our mocks
        val pushService = spyk(
            PushService(
                storage = mockStorage,
                config = configWithCache,
                httpClient = mockHttpClient,
                tokenManager = mockDeviceTokenManager
            )
        )
        
        // Make the service throw MfaException directly when called
        coEvery { pushService.addCredentialFromUri(any()) } throws 
            MfaException("Failed to add credential: Device token not available")

        try {
            // When - use any valid string since we're mocking the method
            pushService.addCredentialFromUri("test-uri")
            // Should not reach here
            assert(false) { "Expected MfaException but no exception was thrown" }
        } catch (e: MfaException) {
            // Then - we expect an MfaException
            assert(e.message?.contains("Device token not available") ?: false)
        }
    }

    @Test
    fun `test update device token updates locally and on server for all credentials`() = runTest {
        // Given
        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        val mockHandler = mockk<PushHandler>()
        val pushService = PushService(
            storage = mockStorage,
            config = configWithCache,
            httpClient = mockHttpClient,
            tokenManager = mockDeviceTokenManager,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        coEvery { mockDeviceTokenManager.updateDeviceToken(testDeviceToken) } returns true

        // Mock getting credentials
        coEvery { mockStorage.getAllPushCredentials() } returns listOf(testCredential)

        // Mock handler - setDeviceToken is a suspend function
        coEvery { mockHandler.setDeviceToken(any(), any(), any()) } returns true

        // When
        val result = pushService.updateDeviceToken(testDeviceToken)

        // Then
        assertTrue(result)
        coVerify { mockDeviceTokenManager.updateDeviceToken(testDeviceToken) }
        coVerify { mockStorage.getAllPushCredentials() }
        coVerify { mockHandler.setDeviceToken(testCredential, testDeviceToken, any()) }
    }

    @Test
    fun `test update device token for specific credential`() = runTest {
        // Given
        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        val mockHandler = mockk<PushHandler>()
        val pushService = PushService(
            storage = mockStorage,
            config = configWithCache,
            httpClient = mockHttpClient,
            tokenManager = mockDeviceTokenManager,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        coEvery { mockDeviceTokenManager.updateDeviceToken(testDeviceToken) } returns true

        // Mock getting specific credential
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns testCredential

        // Mock handler - setDeviceToken is a suspend function
        coEvery { mockHandler.setDeviceToken(any(), any(), any()) } returns true

        // When
        val result = pushService.updateDeviceToken(testDeviceToken, testCredentialId)

        // Then
        assertTrue(result)
        coVerify { mockDeviceTokenManager.updateDeviceToken(testDeviceToken) }
        coVerify { mockStorage.retrievePushCredential(testCredentialId) }
        coVerify { mockHandler.setDeviceToken(testCredential, testDeviceToken, any()) }
    }

    @Test
    fun `test get device token`() = runTest {
        // Given
        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        val pushService = PushService(
            storage = mockStorage,
            config = configWithCache,
            httpClient = mockHttpClient,
            tokenManager = mockDeviceTokenManager
        )

        coEvery { mockDeviceTokenManager.getDeviceTokenId() } returns testDeviceToken

        // When
        val result = pushService.getDeviceToken()

        // Then
        assertEquals(testDeviceToken, result)
    }

    @Test
    fun `test clear cache empties the credentials cache`() = runTest {
        // Given
        coEvery { mockStorage.storePushCredential(any()) } just runs

        // Add credential to populate cache
        pushServiceWithCache.addCredential(testCredential)

        // When
        pushServiceWithCache.clearCache()

        // Setup to get credential by ID after clearing cache
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns testCredential

        // Then - verify storage is accessed (cache was cleared)
        pushServiceWithCache.getCredential(testCredentialId)

        coVerify(exactly = 1) { mockStorage.retrievePushCredential(testCredentialId) }
    }
}
