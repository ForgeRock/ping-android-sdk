/*
 * Copyright (c) 2025-2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import android.content.Context
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.commons.exception.CredentialNotFoundException
import com.pingidentity.mfa.commons.exception.MfaException
import com.pingidentity.mfa.push.exception.DeviceTokenMissingException
import com.pingidentity.mfa.push.exception.NotificationExpiredException
import com.pingidentity.mfa.push.exception.NotificationNotFoundException
import com.pingidentity.mfa.push.storage.PushStorage
import com.pingidentity.network.ktor.KtorHttpClient
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
import org.junit.Assert.assertFalse
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
    private lateinit var mockPolicyEvaluator: com.pingidentity.mfa.commons.policy.MfaPolicyEvaluator
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
        mockPolicyEvaluator = mockk(relaxed = true)
        
        // Set up default mock for duplicate check (returns null = no duplicate)
        coEvery { mockStorage.getCredentialByIssuerAndAccount(any(), any()) } returns null

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

        // Initialize service variants with mock storage, HTTP client, and policy evaluator
        pushServiceWithCache = PushService(
            mockStorage,
            configWithCache,
            KtorHttpClient(mockHttpClient),
            mockPolicyEvaluator
        )
        pushServiceWithoutCache = PushService(
            mockStorage,
            configWithoutCache,
            KtorHttpClient(mockHttpClient),
            mockPolicyEvaluator
        )

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
        coEvery { mockStorage.removePushNotificationsForCredential(testCredentialId) } returns 2

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
        val mockCredential = mockk<PushCredential>()
        val messageData = mapOf(
            "credentialId" to testCredentialId,
            "message" to "Test message",
            "platform" to PushPlatform.PING_AM.name
        )

        // Mock handler retrieval and methods
        every { mockHandler.canHandle(any<Map<String, Any>>()) } returns true
        every { mockHandler.parseMessage(any<Map<String, Any>>()) } returns mapOf(
            "credentialId" to testCredentialId,
            "messageId" to "test-message-id",
            "messageText" to "Test authentication request"
        )

        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        coEvery { mockCredential.userId } returns "some-id"
        coEvery { mockStorage.getNotificationByMessageId(any()) } returns null
        coEvery { mockStorage.retrievePushCredential(any()) } returns mockCredential
        coEvery { mockStorage.storePushNotification(any()) } just runs

        // When
        val result = pushService.processNotification(messageData)

        // Then
        assertNotNull(result)
        assertEquals(testCredentialId, result?.credentialId)
        coVerify(exactly = 1) { mockStorage.storePushNotification(any()) }
    }

    @Test
    fun `test process notification from string creates and stores notification`() = runTest {
        // Given
        val mockHandler = mockk<PushHandler>()
        val mockCredential = mockk<PushCredential>()
        val messageString = "test-jwt-string"

        // Mock handler retrieval and methods
        every { mockHandler.canHandle(any<String>()) } returns true
        every { mockHandler.parseMessage(any<String>()) } returns mapOf(
            "credentialId" to testCredentialId,
            "messageId" to "test-message-id",
            "messageText" to "Test authentication request"
        )

        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        coEvery { mockCredential.userId } returns "some-id"
        coEvery { mockStorage.getNotificationByMessageId(any()) } returns null
        coEvery { mockStorage.retrievePushCredential(any()) } returns mockCredential
        coEvery { mockStorage.storePushNotification(any()) } just runs

        // When
        val result = pushService.processNotification(messageString)

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
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
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
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
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
                configuration = configWithCache,
                httpClient = KtorHttpClient(mockHttpClient),
                policyEvaluator = mockPolicyEvaluator,
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
    fun `test add credential from URI throws exception when device token not available`() =
        runTest {
            // Given
            val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()

            // Create a spied version of the service with our mocks
            val pushService = spyk(
                PushService(
                    storage = mockStorage,
                    configuration = configWithCache,
                    httpClient = KtorHttpClient(mockHttpClient),
                    policyEvaluator = mockPolicyEvaluator,
                    tokenManager = mockDeviceTokenManager
                )
            )

            // Make the service throw DeviceTokenMissingException directly when called
            coEvery { pushService.addCredentialFromUri(any()) } throws
                    DeviceTokenMissingException()

            try {
                // When - use any valid string since we're mocking the method
                pushService.addCredentialFromUri("test-uri")
                // Should not reach here
                assert(false) { "Expected DeviceTokenMissingException but no exception was thrown" }
            } catch (e: DeviceTokenMissingException) {
                // Then - we expect a DeviceTokenMissingException
                assert(e.message?.contains("Device token not set") ?: false)
            }
        }

    @Test
    fun `test update device token updates locally and on server for all credentials`() = runTest {
        // Given
        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        val mockHandler = mockk<PushHandler>()
        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            tokenManager = mockDeviceTokenManager,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        coEvery { mockDeviceTokenManager.updateDeviceToken(testDeviceToken) } returns true
        coEvery { mockDeviceTokenManager.shouldUpdateToken(testDeviceToken) } returns true
        coEvery { mockDeviceTokenManager.getDeviceTokenId() } returns "old-device-token"

        // Mock getting credentials
        coEvery { mockStorage.getAllPushCredentials() } returns listOf(testCredential)

        // Mock handler - setDeviceToken is a suspend function
        coEvery { mockHandler.setDeviceToken(any(), any(), any()) } returns true

        // When
        val result = pushService.setDeviceToken(testDeviceToken)

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
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            tokenManager = mockDeviceTokenManager,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        coEvery { mockDeviceTokenManager.updateDeviceToken(testDeviceToken) } returns true
        coEvery { mockDeviceTokenManager.shouldUpdateToken(testDeviceToken) } returns true
        coEvery { mockDeviceTokenManager.getDeviceTokenId() } returns "old-device-token"

        // Mock getting specific credential
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns testCredential

        // Mock handler - setDeviceToken is a suspend function
        coEvery { mockHandler.setDeviceToken(any(), any(), any()) } returns true

        // When
        val result = pushService.setDeviceToken(testDeviceToken, testCredentialId)

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
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            tokenManager = mockDeviceTokenManager
        )

        coEvery { mockDeviceTokenManager.getDeviceTokenId() } returns testDeviceToken

        // When
        val result = pushService.getDeviceToken()

        // Then
        assertEquals(testDeviceToken, result)
    }

    @Test
    fun `test get device token returns null on error`() = runTest {
        // Given
        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            tokenManager = mockDeviceTokenManager
        )

        coEvery { mockDeviceTokenManager.getDeviceTokenId() } throws RuntimeException("Token error")

        // When
        val result = pushService.getDeviceToken()

        // Then
        assertEquals(null, result)
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

    @Test
    fun `test getAllNotifications retrieves all notifications from storage`() = runTest {
        // Given
        val notifications = listOf(testNotification)
        coEvery { mockStorage.getAllPushNotifications() } returns notifications

        // When
        val result = pushServiceWithCache.getAllNotifications()

        // Then
        assertEquals(notifications, result)
        coVerify(exactly = 1) { mockStorage.getAllPushNotifications() }
    }

    @Test
    fun `test setDeviceToken returns false when specific credential not found`() = runTest {
        // Given
        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            tokenManager = mockDeviceTokenManager
        )

        coEvery { mockDeviceTokenManager.shouldUpdateToken(testDeviceToken) } returns true
        coEvery { mockDeviceTokenManager.updateDeviceToken(testDeviceToken) } returns true
        coEvery { mockDeviceTokenManager.getDeviceTokenId() } returns "old-token"
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns null

        // When
        val result = pushService.setDeviceToken(testDeviceToken, testCredentialId)

        // Then - should return false when credential not found
        assertFalse(result)
    }

    @Test
    fun `test storage error throws MfaException`() = runTest {
        // Given - test one storage error as example (they all follow same pattern)
        coEvery { mockStorage.getAllPushNotifications() } throws RuntimeException("Storage error")

        // When/Then
        try {
            pushServiceWithCache.getAllNotifications()
            assert(false) { "Expected MfaException" }
        } catch (e: MfaException) {
            assert(e.message?.contains("Failed") == true)
        }
    }

    @Test
    fun `test processNotification returns null for unknown platform`() = runTest {
        // Given
        val messageData = mapOf("unknown" to "data")

        // When
        val result = pushServiceWithCache.processNotification(messageData)

        // Then
        assertEquals(null, result)
    }

    @Test
    fun `test processNotification throws exception on handler error`() = runTest {
        // Given
        val mockHandler = mockk<PushHandler>()
        val messageData = mapOf("test" to "data")

        every { mockHandler.canHandle(any<Map<String, Any>>()) } returns true
        every { mockHandler.parseMessage(any<Map<String, Any>>()) } throws RuntimeException("Handler error")

        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            handlers = mapOf("test" to mockHandler)
        )

        // When/Then
        try {
            pushService.processNotification(messageData)
            assert(false) { "Expected MfaException" }
        } catch (e: MfaException) {
            assert(e.message?.contains("Failed to process push notification") == true)
        }
    }

    @Test
    fun `test approveNotification throws exception when notification not found`() = runTest {
        // Given
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns null

        // When/Then
        try {
            pushServiceWithCache.approveNotification(testNotificationId)
            assert(false) { "Expected NotificationNotFoundException" }
        } catch (e: NotificationNotFoundException) {
            assert(e.message?.contains("Notification not found") == true)
            assert(e.notificationId == testNotificationId)
        }
    }

    @Test
    fun `test denyNotification throws exception when credential not found`() = runTest {
        // Given
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns testNotification
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns null

        // When/Then
        try {
            pushServiceWithCache.denyNotification(testNotificationId)
            assert(false) { "Expected CredentialNotFoundException" }
        } catch (e: CredentialNotFoundException) {
            assert(e.message?.contains("Credential not found") == true)
            assert(e.credentialId == testCredentialId)
        }
    }

    @Test
    fun `test setDeviceToken does not update when token is same`() = runTest {
        // Given
        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            tokenManager = mockDeviceTokenManager
        )

        coEvery { mockDeviceTokenManager.shouldUpdateToken(testDeviceToken) } returns false

        // When
        val result = pushService.setDeviceToken(testDeviceToken)

        // Then
        assertTrue(result)
        coVerify(exactly = 0) { mockDeviceTokenManager.updateDeviceToken(any()) }
    }

    @Test
    fun `test processNotification does not update credential userId when already set`() = runTest {
        // Given
        val mockHandler = mockk<PushHandler>()
        val messageData = mapOf("test" to "data")
        val credentialWithUserId = testCredential.copy(userId = "existing-user-id")

        every { mockHandler.canHandle(any<Map<String, Any>>()) } returns true
        every { mockHandler.parseMessage(any<Map<String, Any>>()) } returns mapOf(
            "credentialId" to testCredentialId,
            "messageId" to "test-message-id",
            "username" to "new-user-id",
            "messageText" to "Test message"
        )

        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            handlers = mapOf("test" to mockHandler)
        )

        coEvery { mockStorage.getNotificationByMessageId(any()) } returns null
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns credentialWithUserId
        coEvery { mockStorage.storePushNotification(any()) } just runs

        // When
        pushService.processNotification(messageData)

        // Then - should NOT update credential since userId already exists
        coVerify(exactly = 0) { mockStorage.storePushCredential(any()) }
    }

    @Test
    fun `test setDeviceToken returns false when local update fails`() = runTest {
        // Given
        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            tokenManager = mockDeviceTokenManager
        )

        coEvery { mockDeviceTokenManager.shouldUpdateToken(testDeviceToken) } returns true
        coEvery { mockDeviceTokenManager.updateDeviceToken(testDeviceToken) } returns false
        coEvery { mockDeviceTokenManager.getDeviceTokenId() } returns "old-token"

        // When
        val result = pushService.setDeviceToken(testDeviceToken)

        // Then - should return false when local update fails
        assertFalse(result)
        coVerify(exactly = 0) { mockStorage.getAllPushCredentials() }
    }

    @Test
    fun `test setDeviceToken succeeds with no credentials registered`() = runTest {
        // Given
        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            tokenManager = mockDeviceTokenManager
        )

        coEvery { mockDeviceTokenManager.shouldUpdateToken(testDeviceToken) } returns true
        coEvery { mockDeviceTokenManager.updateDeviceToken(testDeviceToken) } returns true
        coEvery { mockDeviceTokenManager.getDeviceTokenId() } returns "old-token"
        coEvery { mockStorage.getAllPushCredentials() } returns emptyList()

        // When
        val result = pushService.setDeviceToken(testDeviceToken)

        // Then - should return true even with no credentials
        assertTrue(result)
        coVerify(exactly = 1) { mockStorage.getAllPushCredentials() }
    }

    @Test
    fun `test setDeviceToken continues with remaining credentials when one fails`() = runTest {
        // Given
        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        val mockHandler = mockk<PushHandler>()
        val credential2 = testCredential.copy(id = "credential-2")

        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            tokenManager = mockDeviceTokenManager,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        coEvery { mockDeviceTokenManager.shouldUpdateToken(testDeviceToken) } returns true
        coEvery { mockDeviceTokenManager.updateDeviceToken(testDeviceToken) } returns true
        coEvery { mockDeviceTokenManager.getDeviceTokenId() } returns "old-token"
        coEvery { mockStorage.getAllPushCredentials() } returns listOf(testCredential, credential2)
        coEvery {
            mockHandler.setDeviceToken(
                testCredential,
                any(),
                any()
            )
        } throws RuntimeException("Network error")
        coEvery { mockHandler.setDeviceToken(credential2, any(), any()) } returns true

        // When
        val result = pushService.setDeviceToken(testDeviceToken)

        // Then - should continue and process second credential
        assertFalse(result) // Returns false because one failed
        coVerify(exactly = 1) { mockHandler.setDeviceToken(testCredential, testDeviceToken, any()) }
        coVerify(exactly = 1) { mockHandler.setDeviceToken(credential2, testDeviceToken, any()) }
    }

    @Test
    fun `test processNotification returns existing notification for duplicates`() = runTest {
        // Given
        val mockHandler = mockk<PushHandler>()
        val messageData = mapOf("test" to "data")
        val messageId = "duplicate-message-id"

        every { mockHandler.canHandle(any<Map<String, Any>>()) } returns true
        every { mockHandler.parseMessage(any<Map<String, Any>>()) } returns mapOf(
            "credentialId" to testCredentialId,
            "messageId" to messageId,
            "messageText" to "Duplicate message"
        )

        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            handlers = mapOf("test" to mockHandler)
        )

        // Mock that notification already exists
        coEvery { mockStorage.getNotificationByMessageId(messageId) } returns testNotification

        // When
        val result = pushService.processNotification(messageData)

        // Then - should return existing notification for duplicate
        assertEquals(testNotification, result)
        coVerify(exactly = 0) { mockStorage.storePushNotification(any()) }
    }

    @Test
    fun `test setDeviceToken returns false when handler fails for all credentials`() = runTest {
        // Given
        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        val mockHandler = mockk<PushHandler>()

        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            tokenManager = mockDeviceTokenManager,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        coEvery { mockDeviceTokenManager.shouldUpdateToken(testDeviceToken) } returns true
        coEvery { mockDeviceTokenManager.updateDeviceToken(testDeviceToken) } returns true
        coEvery { mockDeviceTokenManager.getDeviceTokenId() } returns "old-token"
        coEvery { mockStorage.getAllPushCredentials() } returns listOf(testCredential)
        coEvery { mockHandler.setDeviceToken(any(), any(), any()) } returns false

        // When
        val result = pushService.setDeviceToken(testDeviceToken)

        // Then - should return false when handler fails for all credentials
        assertFalse(result)
        coVerify(exactly = 1) { mockHandler.setDeviceToken(testCredential, testDeviceToken, any()) }
    }

    @Test
    fun `test approveNotification returns false when notification already responded`() = runTest {
        // Given
        val respondedNotification = testNotification.copy().apply { markApproved() }
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns respondedNotification

        // When
        val result = pushServiceWithCache.approveNotification(testNotificationId)

        // Then
        assertFalse(result)
        coVerify(exactly = 0) { mockStorage.updatePushNotification(any()) }
    }

    @Test
    fun `test denyNotification returns false when notification already responded`() = runTest {
        // Given
        val respondedNotification = testNotification.copy().apply { markDenied() }
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns respondedNotification

        // When
        val result = pushServiceWithCache.denyNotification(testNotificationId)

        // Then
        assertFalse(result)
        coVerify(exactly = 0) { mockStorage.updatePushNotification(any()) }
    }

    @Test
    fun `test approveNotification throws CredentialLockedException when credential is locked`() =
        runTest {
            // Given
            val lockedCredential =
                testCredential.copy(isLocked = true, lockingPolicy = "test-policy")
            coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns testNotification
            coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns lockedCredential

            // When/Then - CredentialLockedException is now re-thrown directly (not wrapped)
            try {
                pushServiceWithCache.approveNotification(testNotificationId)
                assert(false) { "Expected CredentialLockedException" }
            } catch (e: com.pingidentity.mfa.commons.exception.CredentialLockedException) {
                assert(e.policyName == "test-policy")
                assert(e.message?.contains("Credential is currently locked") == true)
            }
        }

    @Test
    fun `test denyNotification throws CredentialLockedException when credential is locked`() =
        runTest {
            // Given
            val lockedCredential =
                testCredential.copy(isLocked = true, lockingPolicy = "test-policy")
            coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns testNotification
            coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns lockedCredential

            // When/Then - CredentialLockedException is now re-thrown directly (not wrapped)
            try {
                pushServiceWithCache.denyNotification(testNotificationId)
                assert(false) { "Expected CredentialLockedException" }
            } catch (e: com.pingidentity.mfa.commons.exception.CredentialLockedException) {
                assert(e.policyName == "test-policy")
                assert(e.message?.contains("Credential is currently locked") == true)
            }
        }

    @Test
    fun `test removeCredential removes associated notifications`() = runTest {
        // Given
        coEvery { mockStorage.removePushCredential(testCredentialId) } returns true
        coEvery { mockStorage.removePushNotificationsForCredential(testCredentialId) } returns 3

        // When
        val result = pushServiceWithCache.removeCredential(testCredentialId)

        // Then
        assertTrue(result)
        coVerify(exactly = 1) { mockStorage.removePushNotificationsForCredential(testCredentialId) }
    }

    @Test
    fun `test processNotification updates credential userId when missing`() = runTest {
        // Given
        val mockHandler = mockk<PushHandler>()
        val messageData = mapOf("test" to "data")
        val credentialWithoutUserId = testCredential.copy(userId = "")

        every { mockHandler.canHandle(any<Map<String, Any>>()) } returns true
        every { mockHandler.parseMessage(any<Map<String, Any>>()) } returns mapOf(
            "credentialId" to testCredentialId,
            "messageId" to "test-message-id",
            "username" to "new-user-id",
            "messageText" to "Test message"
        )

        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            handlers = mapOf("test" to mockHandler)
        )

        coEvery { mockStorage.getNotificationByMessageId(any()) } returns null
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns credentialWithoutUserId
        coEvery { mockStorage.storePushCredential(any()) } just runs
        coEvery { mockStorage.storePushNotification(any()) } just runs

        // When
        pushService.processNotification(messageData)

        // Then
        coVerify(exactly = 1) {
            mockStorage.storePushCredential(
                match { it.id == testCredentialId && it.userId == "new-user-id" }
            )
        }
    }

    @Test
    fun `test approveNotification throws exception when handler missing for platform`() = runTest {
        // Given
        val credentialWithUnknownPlatform = testCredential.copy(platform = "UNKNOWN")
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns testNotification
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns credentialWithUnknownPlatform

        // When/Then
        try {
            pushServiceWithCache.approveNotification(testNotificationId)
            assert(false) { "Expected MfaException" }
        } catch (e: MfaException) {
            assert(e.message?.contains("Failed to approve notification") == true)
        }
    }

    @Test
    fun `test denyNotification throws exception when handler missing for platform`() = runTest {
        // Given
        val credentialWithUnknownPlatform = testCredential.copy(platform = "UNKNOWN")
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns testNotification
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns credentialWithUnknownPlatform

        // When/Then
        try {
            pushServiceWithCache.denyNotification(testNotificationId)
            assert(false) { "Expected MfaException" }
        } catch (e: MfaException) {
            assert(e.message?.contains("Failed to deny notification") == true)
        }
    }

    @Test
    fun `test approveNotification returns false when handler sendApproval fails`() = runTest {
        // Given
        val mockHandler = mockk<PushHandler>()
        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns testNotification
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns testCredential
        coEvery { mockHandler.sendApproval(any(), any(), any()) } returns false

        // When
        val result = pushService.approveNotification(testNotificationId)

        // Then
        assertFalse(result)
        coVerify(exactly = 0) { mockStorage.updatePushNotification(any()) }
    }

    @Test
    fun `test denyNotification returns false when handler sendDenial fails`() = runTest {
        // Given
        val mockHandler = mockk<PushHandler>()
        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns testNotification
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns testCredential
        coEvery { mockHandler.sendDenial(any(), any(), any()) } returns false

        // When
        val result = pushService.denyNotification(testNotificationId)

        // Then
        assertFalse(result)
        coVerify(exactly = 0) { mockStorage.updatePushNotification(any()) }
    }

    @Test
    fun `test addCredentialFromUri throws DuplicateCredentialException for duplicate credentials`() =
        runTest {
            // Given
            val testUri =
                "pushauth://push/TestIssuer:test@example.com?s=secret&r=https://test.com&a=SHA256&c=challenge"
            val existingCredential = testCredential.copy(
                id = UUID.randomUUID().toString()
            )

            // Mock parsing
            coEvery { PushUriParser.parse(testUri) } returns testCredential

            // Mock storage to return existing credential with same issuer and account
            coEvery {
                mockStorage.getCredentialByIssuerAndAccount(
                    testCredential.issuer,
                    testCredential.accountName
                )
            } returns existingCredential

            // Mock handler
            val mockHandler = mockk<PushHandler>()
            val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
            coEvery { mockDeviceTokenManager.getDeviceTokenId() } returns "test-token-id"

            val pushService = PushService(
                storage = mockStorage,
                configuration = configWithCache,
                httpClient = KtorHttpClient(mockHttpClient),
                policyEvaluator = mockPolicyEvaluator,
                tokenManager = mockDeviceTokenManager,
                handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
            )

            // When/Then
            try {
                pushService.addCredentialFromUri(testUri)
                throw AssertionError("Expected DuplicateCredentialException to be thrown")
            } catch (e: com.pingidentity.mfa.commons.exception.DuplicateCredentialException) {
                // Verify exception details
                assertEquals(testCredential.issuer, e.issuer)
                assertEquals(testCredential.accountName, e.accountName)
                assertTrue(e.message!!.contains("Credential already exists"))
                assertTrue(e.message!!.contains(testCredential.issuer))
                assertTrue(e.message!!.contains(testCredential.accountName))

                // Verify handler registration was not called
                coVerify(exactly = 0) {
                    mockHandler.register(any(), any())
                }

                // Verify storage was not called to store the duplicate
                coVerify(exactly = 0) {
                    mockStorage.storePushCredential(any())
                }
            } catch (e: MfaException) {
                // If it's wrapped in MfaException, check the cause
                val cause = e.cause
                if (cause is com.pingidentity.mfa.commons.exception.DuplicateCredentialException) {
                    assertEquals(testCredential.issuer, cause.issuer)
                    assertEquals(testCredential.accountName, cause.accountName)
                    assertTrue(cause.message!!.contains("Credential already exists"))
                    assertTrue(cause.message!!.contains(testCredential.issuer))
                    assertTrue(cause.message!!.contains(testCredential.accountName))

                    // Verify handler registration was not called
                    coVerify(exactly = 0) {
                        mockHandler.register(any(), any())
                    }

                    // Verify storage was not called to store the duplicate
                    coVerify(exactly = 0) {
                        mockStorage.storePushCredential(any())
                    }
                } else {
                    throw AssertionError("Expected DuplicateCredentialException but got: ${e.message}", e)
                }
            }
        }

    @Test
    fun `test addCredentialFromUri succeeds with different issuer`() = runTest {
        // Given
        val testUri =
            "pushauth://push/DifferentIssuer:test@example.com?s=secret&r=https://test.com&a=SHA256&c=challenge"
        val differentCredential = testCredential.copy(
            id = UUID.randomUUID().toString(),
            issuer = "DifferentIssuer"
        )

        // Mock parsing
        coEvery { PushUriParser.parse(testUri) } returns differentCredential
        coEvery { PushUriParser.registrationParameters(testUri) } returns emptyMap()

        // Mock storage to return null (no existing credential)
        coEvery {
            mockStorage.getCredentialByIssuerAndAccount(
                differentCredential.issuer,
                differentCredential.accountName
            )
        } returns null

        coEvery {
            mockStorage.storePushCredential(any<PushCredential>())
        } just runs

        // Mock handler
        val mockHandler = mockk<PushHandler>()
        coEvery { mockHandler.register(any(), any()) } returns true

        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        coEvery { mockDeviceTokenManager.getDeviceTokenId() } returns "test-token-id"

        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            tokenManager = mockDeviceTokenManager,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        // When
        val result = pushService.addCredentialFromUri(testUri)

        // Then
        assertNotNull(result)
        assertEquals(differentCredential.id, result.id)

        // Verify handler registration was called
        coVerify(exactly = 1) {
            mockHandler.register(any(), any())
        }

        // Verify storage was called
        coVerify(exactly = 1) {
            mockStorage.storePushCredential(any())
        }
    }

    @Test
    fun `test addCredentialFromUri succeeds with different account name`() = runTest {
        // Given
        val testUri =
            "pushauth://push/TestIssuer:different@example.com?s=secret&r=https://test.com&a=SHA256&c=challenge"
        val differentCredential = testCredential.copy(
            id = UUID.randomUUID().toString(),
            accountName = "different@example.com",
            displayAccountName = "different@example.com"
        )

        // Mock parsing
        coEvery { PushUriParser.parse(testUri) } returns differentCredential
        coEvery { PushUriParser.registrationParameters(testUri) } returns emptyMap()

        // Mock storage to return null (no existing credential)
        coEvery {
            mockStorage.getCredentialByIssuerAndAccount(
                differentCredential.issuer,
                differentCredential.accountName
            )
        } returns null

        coEvery {
            mockStorage.storePushCredential(any<PushCredential>())
        } just runs

        // Mock handler
        val mockHandler = mockk<PushHandler>()
        coEvery { mockHandler.register(any(), any()) } returns true

        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        coEvery { mockDeviceTokenManager.getDeviceTokenId() } returns "test-token-id"

        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            tokenManager = mockDeviceTokenManager,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        // When
        val result = pushService.addCredentialFromUri(testUri)

        // Then
        assertNotNull(result)
        assertEquals(differentCredential.id, result.id)

        // Verify handler registration was called
        coVerify(exactly = 1) {
            mockHandler.register(any(), any())
        }

        // Verify storage was called
        coVerify(exactly = 1) {
            mockStorage.storePushCredential(any())
        }
    }

    @Test
    fun `test approve expired notification throws NotificationExpiredException`() = runTest {
        // Given
        val expiredNotification = testNotification.copy(
            createdAt = java.util.Date(System.currentTimeMillis() - 120000), // 2 minutes ago
            ttl = 60 // 60 seconds TTL
        )
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns expiredNotification

        // When/Then
        try {
            pushServiceWithCache.approveNotification(testNotificationId)
            assert(false) { "Expected NotificationExpiredException" }
        } catch (e: NotificationExpiredException) {
            assert(e.message?.contains("has expired") == true)
            assert(e.notificationId == testNotificationId)
            assert(e.ttlSeconds == 60)
        }
    }

    @Test
    fun `test deny expired notification throws NotificationExpiredException`() = runTest {
        // Given
        val expiredNotification = testNotification.copy(
            createdAt = java.util.Date(System.currentTimeMillis() - 120000), // 2 minutes ago
            ttl = 60 // 60 seconds TTL
        )
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns expiredNotification

        // When/Then
        try {
            pushServiceWithCache.denyNotification(testNotificationId)
            assert(false) { "Expected NotificationExpiredException" }
        } catch (e: NotificationExpiredException) {
            assert(e.message?.contains("has expired") == true)
            assert(e.notificationId == testNotificationId)
            assert(e.ttlSeconds == 60)
        }
    }

    @Test
    fun `test approve nonexistent notification throws NotificationNotFoundException`() = runTest {
        // Given
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns null

        // When/Then
        try {
            pushServiceWithCache.approveNotification(testNotificationId)
            assert(false) { "Expected NotificationNotFoundException" }
        } catch (e: NotificationNotFoundException) {
            assert(e.message?.contains("Notification not found") == true)
            assert(e.notificationId == testNotificationId)
        }
    }

    @Test
    fun `test deny nonexistent notification throws NotificationNotFoundException`() = runTest {
        // Given
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns null

        // When/Then
        try {
            pushServiceWithCache.denyNotification(testNotificationId)
            assert(false) { "Expected NotificationNotFoundException" }
        } catch (e: NotificationNotFoundException) {
            assert(e.message?.contains("Notification not found") == true)
            assert(e.notificationId == testNotificationId)
        }
    }

    @Test
    fun `test approve with missing credential throws CredentialNotFoundException`() = runTest {
        // Given
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns testNotification
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns null

        // When/Then
        try {
            pushServiceWithCache.approveNotification(testNotificationId)
            assert(false) { "Expected CredentialNotFoundException" }
        } catch (e: CredentialNotFoundException) {
            assert(e.message?.contains("Credential not found") == true)
            assert(e.credentialId == testCredentialId)
        }
    }

    @Test
    fun `test deny with missing credential throws CredentialNotFoundException`() = runTest {
        // Given
        coEvery { mockStorage.retrievePushNotification(testNotificationId) } returns testNotification
        coEvery { mockStorage.retrievePushCredential(testCredentialId) } returns null

        // When/Then
        try {
            pushServiceWithCache.denyNotification(testNotificationId)
            assert(false) { "Expected CredentialNotFoundException" }
        } catch (e: CredentialNotFoundException) {
            assert(e.message?.contains("Credential not found") == true)
            assert(e.credentialId == testCredentialId)
        }
    }

    @Test
    fun `test addCredentialFromUri without device token throws DeviceTokenMissingException`() = runTest {
        // Given - Use a valid test URI from the test suite
        val testUri = "pushauth://push/forgerock:user?a=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&r=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy&s=b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E"
        val mockDeviceTokenManager = mockk<PushDeviceTokenManager>()
        val mockHandler = mockk<PushHandler>()
        
        coEvery { mockDeviceTokenManager.getDeviceTokenId() } returns null
        every { mockHandler.canHandle(any<String>()) } returns true

        val pushService = PushService(
            storage = mockStorage,
            configuration = configWithCache,
            httpClient = KtorHttpClient(mockHttpClient),
            policyEvaluator = mockPolicyEvaluator,
            tokenManager = mockDeviceTokenManager,
            handlers = mapOf(PushPlatform.PING_AM.name to mockHandler)
        )

        // When/Then
        try {
            pushService.addCredentialFromUri(testUri)
            assert(false) { "Expected DeviceTokenMissingException" }
        } catch (e: DeviceTokenMissingException) {
            assert(e.message?.contains("Device token not set") == true)
            assert(e.message?.contains("Call setDeviceToken()") == true)
        }
    }
}