/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import android.content.Context
import com.google.firebase.messaging.RemoteMessage
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.push.storage.PushStorage
import com.pingidentity.mfa.push.storage.SQLPushStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.UUID

/**
 * Unit tests for the PushClient class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@ExperimentalCoroutinesApi
class PushClientTest {

    // Mock objects
    private lateinit var mockContext: Context
    private lateinit var mockStorage: PushStorage
    private lateinit var mockLogger: Logger
    private lateinit var mockConfiguration: PushConfiguration
    private lateinit var mockPushService: PushService
    private lateinit var mockCleanupManager: NotificationCleanupManager

    // Test subject
    private lateinit var pushClient: PushClient

    // Test data
    private lateinit var testCredential: PushCredential
    private lateinit var testCredentialId: String
    private lateinit var testNotification: PushNotification
    private lateinit var testNotificationId: String
    private lateinit var testDeviceToken: String
    private lateinit var testUri: String

    @Before
    fun setup() {
        // Create mock objects
        mockContext = mockk(relaxed = true)
        mockStorage = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        
        // Create test configuration
        mockConfiguration = mockk<PushConfiguration>().apply {
            every { logger } returns mockLogger
            every { storage } returns mockStorage
            every { enableCredentialCache } returns true
            every { notificationCleanupConfig } returns NotificationCleanupConfig {
                cleanupMode = NotificationCleanupConfig.CleanupMode.COUNT_BASED
                maxStoredNotifications = 100
            }
            every { customPushHandlers } returns emptyMap()
        }

        // Create mock service and cleanup manager
        mockPushService = mockk(relaxed = true)
        mockCleanupManager = mockk(relaxed = true)

        // Create test data
        testCredentialId = UUID.randomUUID().toString()
        testCredential = PushCredential(
            id = testCredentialId,
            userId = "test-user",
            resourceId = UUID.randomUUID().toString(),
            issuer = "ForgeRock",
            displayIssuer = "ForgeRock",
            accountName = "testuser",
            displayAccountName = "Test User",
            serverEndpoint = "https://example.com/push",
            sharedSecret = "test-secret",
            createdAt = Date(),
            platform = PushPlatform.PING_AM.name
        )

        testNotificationId = UUID.randomUUID().toString()
        testNotification = PushNotification(
            id = testNotificationId,
            credentialId = testCredentialId,
            ttl = 120,
            messageId = "msg-123",
            messageText = "Test message",
            pushType = PushType.DEFAULT
        )

        testDeviceToken = "test-device-token-12345"
        testUri = "pushauth://push/forgerock:user?a=aHR0cDovL2V4YW1wbGUuY29tL3B1c2g&s=c2VjcmV0"

        // Create PushClient with mocked dependencies
        pushClient = spyk(PushClient(mockConfiguration))
        
        // Replace internal services with mocks using reflection
        val pushServiceField = PushClient::class.java.getDeclaredField("pushService")
        pushServiceField.isAccessible = true
        pushServiceField.set(pushClient, mockPushService)

        val cleanupManagerField = PushClient::class.java.getDeclaredField("cleanupManager")
        cleanupManagerField.isAccessible = true
        cleanupManagerField.set(pushClient, mockCleanupManager)
        
        // Mark as initialized
        val isInitializedField = PushClient::class.java.superclass.getDeclaredField("isInitialized")
        isInitializedField.isAccessible = true
        isInitializedField.setBoolean(pushClient, true)
    }

    @Test
    fun `test client is initialized and can be used`() = runTest {
        // Given - client is already initialized in setup
        coEvery { mockPushService.getCredentials() } returns listOf(testCredential)

        // When - calling a method that requires initialization
        val result = pushClient.getCredentials()

        // Then - should succeed without throwing MfaClientNotInitializedException
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
    }

    @Test
    fun `test addCredentialFromUri succeeds`() = runTest {
        // Given
        coEvery { mockPushService.addCredentialFromUri(testUri) } returns testCredential

        // When
        val result = pushClient.addCredentialFromUri(testUri)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testCredential, result.getOrNull())
        coVerify(exactly = 1) { mockPushService.addCredentialFromUri(testUri) }
    }

    @Test
    fun `test addCredentialFromUri handles failure`() = runTest {
        // Given
        coEvery { mockPushService.addCredentialFromUri(testUri) } throws IllegalArgumentException("Invalid URI")

        // When
        val result = pushClient.addCredentialFromUri(testUri)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `test saveCredential succeeds`() = runTest {
        // Given
        coEvery { mockPushService.addCredential(testCredential) } returns testCredential

        // When
        val result = pushClient.saveCredential(testCredential)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testCredential, result.getOrNull())
        coVerify(exactly = 1) { mockPushService.addCredential(testCredential) }
    }

    @Test
    fun `test saveCredential handles failure`() = runTest {
        // Given
        coEvery { mockPushService.addCredential(testCredential) } throws RuntimeException("Save failed")

        // When
        val result = pushClient.saveCredential(testCredential)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test getCredentials succeeds`() = runTest {
        // Given
        val credentials = listOf(testCredential)
        coEvery { mockPushService.getCredentials() } returns credentials

        // When
        val result = pushClient.getCredentials()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(credentials, result.getOrNull())
        coVerify(exactly = 1) { mockPushService.getCredentials() }
    }

    @Test
    fun `test getCredentials handles failure`() = runTest {
        // Given
        coEvery { mockPushService.getCredentials() } throws RuntimeException("Retrieval failed")

        // When
        val result = pushClient.getCredentials()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test getCredential succeeds`() = runTest {
        // Given
        coEvery { mockPushService.getCredential(testCredentialId) } returns testCredential

        // When
        val result = pushClient.getCredential(testCredentialId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testCredential, result.getOrNull())
        coVerify(exactly = 1) { mockPushService.getCredential(testCredentialId) }
    }

    @Test
    fun `test getCredential returns null when not found`() = runTest {
        // Given
        coEvery { mockPushService.getCredential(testCredentialId) } returns null

        // When
        val result = pushClient.getCredential(testCredentialId)

        // Then
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `test getCredential handles failure`() = runTest {
        // Given
        coEvery { mockPushService.getCredential(testCredentialId) } throws RuntimeException("Retrieval failed")

        // When
        val result = pushClient.getCredential(testCredentialId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test deleteCredential succeeds`() = runTest {
        // Given
        coEvery { mockPushService.removeCredential(testCredentialId) } returns true

        // When
        val result = pushClient.deleteCredential(testCredentialId)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify(exactly = 1) { mockPushService.removeCredential(testCredentialId) }
    }

    @Test
    fun `test deleteCredential returns false when not found`() = runTest {
        // Given
        coEvery { mockPushService.removeCredential(testCredentialId) } returns false

        // When
        val result = pushClient.deleteCredential(testCredentialId)

        // Then
        assertTrue(result.isSuccess)
        assertFalse(result.getOrNull() == true)
    }

    @Test
    fun `test deleteCredential handles failure`() = runTest {
        // Given
        coEvery { mockPushService.removeCredential(testCredentialId) } throws RuntimeException("Delete failed")

        // When
        val result = pushClient.deleteCredential(testCredentialId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test setDeviceToken for all credentials succeeds`() = runTest {
        // Given
        coEvery { mockPushService.setDeviceToken(testDeviceToken, null) } returns true

        // When
        val result = pushClient.setDeviceToken(testDeviceToken)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify(exactly = 1) { mockPushService.setDeviceToken(testDeviceToken, null) }
    }

    @Test
    fun `test setDeviceToken for specific credential succeeds`() = runTest {
        // Given
        coEvery { mockPushService.setDeviceToken(testDeviceToken, testCredentialId) } returns true

        // When
        val result = pushClient.setDeviceToken(testDeviceToken, testCredentialId)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify(exactly = 1) { mockPushService.setDeviceToken(testDeviceToken, testCredentialId) }
    }

    @Test
    fun `test setDeviceToken handles failure`() = runTest {
        // Given
        coEvery { mockPushService.setDeviceToken(testDeviceToken, null) } throws RuntimeException("Token update failed")

        // When
        val result = pushClient.setDeviceToken(testDeviceToken)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test getDeviceToken succeeds`() = runTest {
        // Given
        coEvery { mockPushService.getDeviceToken() } returns testDeviceToken

        // When
        val result = pushClient.getDeviceToken()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testDeviceToken, result.getOrNull())
        coVerify(exactly = 1) { mockPushService.getDeviceToken() }
    }

    @Test
    fun `test getDeviceToken returns null when not set`() = runTest {
        // Given
        coEvery { mockPushService.getDeviceToken() } returns null

        // When
        val result = pushClient.getDeviceToken()

        // Then
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `test getDeviceToken handles failure`() = runTest {
        // Given
        coEvery { mockPushService.getDeviceToken() } throws RuntimeException("Retrieval failed")

        // When
        val result = pushClient.getDeviceToken()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test processNotification with Map succeeds and triggers auto-cleanup`() = runTest {
        // Given
        val messageData = mapOf<String, Any>(
            "messageId" to "msg-123",
            "pushType" to "AUTHENTICATION",
            "title" to "Test",
            "message" to "Test message"
        )
        coEvery { mockPushService.processNotification(messageData) } returns testNotification
        coEvery { mockCleanupManager.runCleanup(testCredentialId) } returns 5

        // When
        val result = pushClient.processNotification(messageData)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testNotification, result.getOrNull())
        coVerify(exactly = 1) { mockPushService.processNotification(messageData) }
        coVerify(exactly = 1) { mockCleanupManager.runCleanup(testCredentialId) }
    }

    @Test
    fun `test processNotification with Map returns null for invalid message`() = runTest {
        // Given
        val messageData = mapOf<String, Any>("invalid" to "data")
        coEvery { mockPushService.processNotification(messageData) } returns null

        // When
        val result = pushClient.processNotification(messageData)

        // Then
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        coVerify(exactly = 0) { mockCleanupManager.runCleanup(any()) }
    }

    @Test
    fun `test processNotification with Map handles failure`() = runTest {
        // Given
        val messageData = mapOf<String, Any>("test" to "data")
        coEvery { mockPushService.processNotification(messageData) } throws RuntimeException("Processing failed")

        // When
        val result = pushClient.processNotification(messageData)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test processNotification with String succeeds and triggers auto-cleanup`() = runTest {
        // Given
        val message = "test-jwt-string"
        coEvery { mockPushService.processNotification(message) } returns testNotification
        coEvery { mockCleanupManager.runCleanup(testCredentialId) } returns 5

        // When
        val result = pushClient.processNotification(message)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testNotification, result.getOrNull())
        coVerify(exactly = 1) { mockPushService.processNotification(message) }
        coVerify(exactly = 1) { mockCleanupManager.runCleanup(testCredentialId) }
    }

    @Test
    fun `test processNotification with String handles failure`() = runTest {
        // Given
        val message = "test-jwt-string"
        coEvery { mockPushService.processNotification(message) } throws RuntimeException("Processing failed")

        // When
        val result = pushClient.processNotification(message)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test processNotification with RemoteMessage succeeds`() = runTest {
        // Given
        val remoteMessage = mockk<RemoteMessage>()
        val messageData = mapOf<String, Any>("test" to "data")
        every { remoteMessage.data } returns messageData.mapValues { it.value.toString() }
        coEvery { mockPushService.processNotification(any<Map<String, Any>>()) } returns testNotification
        coEvery { mockCleanupManager.runCleanup(testCredentialId) } returns 5

        // When
        val result = pushClient.processNotification(remoteMessage)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testNotification, result.getOrNull())
    }

    @Test
    fun `test cleanupNotifications for all credentials succeeds`() = runTest {
        // Given
        coEvery { mockCleanupManager.runCleanup(null) } returns 10

        // When
        val result = pushClient.cleanupNotifications()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrNull())
        coVerify(exactly = 1) { mockCleanupManager.runCleanup(null) }
    }

    @Test
    fun `test cleanupNotifications for specific credential succeeds`() = runTest {
        // Given
        coEvery { mockCleanupManager.runCleanup(testCredentialId) } returns 5

        // When
        val result = pushClient.cleanupNotifications(testCredentialId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(5, result.getOrNull())
        coVerify(exactly = 1) { mockCleanupManager.runCleanup(testCredentialId) }
    }

    @Test
    fun `test cleanupNotifications handles failure`() = runTest {
        // Given
        coEvery { mockCleanupManager.runCleanup(null) } throws RuntimeException("Cleanup failed")

        // When
        val result = pushClient.cleanupNotifications()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test approveNotification succeeds`() = runTest {
        // Given
        coEvery { mockPushService.approveNotification(testNotificationId, emptyMap()) } returns true

        // When
        val result = pushClient.approveNotification(testNotificationId)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify(exactly = 1) { mockPushService.approveNotification(testNotificationId, emptyMap()) }
    }

    @Test
    fun `test approveNotification handles failure`() = runTest {
        // Given
        coEvery { mockPushService.approveNotification(testNotificationId, emptyMap()) } throws RuntimeException("Approval failed")

        // When
        val result = pushClient.approveNotification(testNotificationId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test approveChallengeNotification succeeds`() = runTest {
        // Given
        val challengeResponse = "12345"
        val expectedParams = mapOf("challengeResponse" to challengeResponse)
        coEvery { mockPushService.approveNotification(testNotificationId, expectedParams) } returns true

        // When
        val result = pushClient.approveChallengeNotification(testNotificationId, challengeResponse)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify(exactly = 1) { mockPushService.approveNotification(testNotificationId, expectedParams) }
    }

    @Test
    fun `test approveChallengeNotification handles failure`() = runTest {
        // Given
        val challengeResponse = "12345"
        val expectedParams = mapOf("challengeResponse" to challengeResponse)
        coEvery { mockPushService.approveNotification(testNotificationId, expectedParams) } throws RuntimeException("Challenge approval failed")

        // When
        val result = pushClient.approveChallengeNotification(testNotificationId, challengeResponse)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test approveBiometricNotification succeeds`() = runTest {
        // Given
        val authMethod = "fingerprint"
        val expectedParams = mapOf("authenticationMethod" to authMethod)
        coEvery { mockPushService.approveNotification(testNotificationId, expectedParams) } returns true

        // When
        val result = pushClient.approveBiometricNotification(testNotificationId, authMethod)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify(exactly = 1) { mockPushService.approveNotification(testNotificationId, expectedParams) }
    }

    @Test
    fun `test approveBiometricNotification handles failure`() = runTest {
        // Given
        val authMethod = "face"
        val expectedParams = mapOf("authenticationMethod" to authMethod)
        coEvery { mockPushService.approveNotification(testNotificationId, expectedParams) } throws RuntimeException("Biometric approval failed")

        // When
        val result = pushClient.approveBiometricNotification(testNotificationId, authMethod)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test denyNotification succeeds`() = runTest {
        // Given
        coEvery { mockPushService.denyNotification(testNotificationId, emptyMap()) } returns true

        // When
        val result = pushClient.denyNotification(testNotificationId)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        coVerify(exactly = 1) { mockPushService.denyNotification(testNotificationId, emptyMap()) }
    }

    @Test
    fun `test denyNotification handles failure`() = runTest {
        // Given
        coEvery { mockPushService.denyNotification(testNotificationId, emptyMap()) } throws RuntimeException("Denial failed")

        // When
        val result = pushClient.denyNotification(testNotificationId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test getPendingNotifications succeeds`() = runTest {
        // Given
        val notifications = listOf(testNotification)
        coEvery { mockPushService.getPendingNotifications() } returns notifications

        // When
        val result = pushClient.getPendingNotifications()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(notifications, result.getOrNull())
        coVerify(exactly = 1) { mockPushService.getPendingNotifications() }
    }

    @Test
    fun `test getPendingNotifications handles failure`() = runTest {
        // Given
        coEvery { mockPushService.getPendingNotifications() } throws RuntimeException("Retrieval failed")

        // When
        val result = pushClient.getPendingNotifications()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test auto-cleanup skips when mode is NONE`() = runTest {
        // Given - configure cleanup mode to NONE
        every { mockConfiguration.notificationCleanupConfig } returns NotificationCleanupConfig {
            cleanupMode = NotificationCleanupConfig.CleanupMode.NONE
        }
        val messageData = mapOf<String, Any>("test" to "data")
        coEvery { mockPushService.processNotification(messageData) } returns testNotification

        // When
        val result = pushClient.processNotification(messageData)

        // Then
        assertTrue(result.isSuccess)
        // Auto-cleanup should not be triggered when mode is NONE
        coVerify(exactly = 0) { mockCleanupManager.runCleanup(any()) }
    }

    @Test
    fun `test auto-cleanup continues processing even if cleanup fails`() = runTest {
        // Given
        val messageData = mapOf<String, Any>("test" to "data")
        coEvery { mockPushService.processNotification(messageData) } returns testNotification
        coEvery { mockCleanupManager.runCleanup(testCredentialId) } throws RuntimeException("Cleanup failed")

        // When
        val result = pushClient.processNotification(messageData)

        // Then - processing should still succeed despite cleanup failure
        assertTrue(result.isSuccess)
        assertEquals(testNotification, result.getOrNull())
        coVerify(exactly = 1) { mockCleanupManager.runCleanup(testCredentialId) }
    }

    @Test
    fun `test getAllNotifications succeeds`() = runTest {
        // Given
        val notifications = listOf(testNotification)
        coEvery { mockPushService.getAllNotifications() } returns notifications

        // When
        val result = pushClient.getAllNotifications()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(notifications, result.getOrNull())
        coVerify(exactly = 1) { mockPushService.getAllNotifications() }
    }

    @Test
    fun `test getAllNotifications handles failure`() = runTest {
        // Given
        coEvery { mockPushService.getAllNotifications() } throws RuntimeException("Retrieval failed")

        // When
        val result = pushClient.getAllNotifications()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test getNotification succeeds`() = runTest {
        // Given
        coEvery { mockPushService.getNotification(testNotificationId) } returns testNotification

        // When
        val result = pushClient.getNotification(testNotificationId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(testNotification, result.getOrNull())
        coVerify(exactly = 1) { mockPushService.getNotification(testNotificationId) }
    }

    @Test
    fun `test getNotification returns null when not found`() = runTest {
        // Given
        coEvery { mockPushService.getNotification(testNotificationId) } returns null

        // When
        val result = pushClient.getNotification(testNotificationId)

        // Then
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `test getNotification handles failure`() = runTest {
        // Given
        coEvery { mockPushService.getNotification(testNotificationId) } throws RuntimeException("Retrieval failed")

        // When
        val result = pushClient.getNotification(testNotificationId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `test close clears cache and closes client`() = runTest {
        // Given
        coEvery { mockPushService.clearCache() } returns Unit

        // When
        pushClient.close()

        // Then - verify cache was cleared
        coVerify(exactly = 1) { mockPushService.clearCache() }
    }

    @Test
    fun `test close handles cache clearing failure gracefully`() = runTest {
        // Given
        coEvery { mockPushService.clearCache() } throws RuntimeException("Cache clear failed")

        // When - should not throw exception
        pushClient.close()

        // Then - verify cache clear was attempted
        coVerify(exactly = 1) { mockPushService.clearCache() }
    }

    @Test
    fun `test methods throw exception when client not initialized`() = runTest {
        // Given - mark client as not initialized
        val isInitializedField = PushClient::class.java.superclass.getDeclaredField("isInitialized")
        isInitializedField.isAccessible = true
        isInitializedField.setBoolean(pushClient, false)

        // When/Then - all methods should fail with not initialized error
        var credentialsResult = pushClient.getCredentials()
        assertTrue(credentialsResult.isFailure)
        
        var credentialResult = pushClient.getCredential(testCredentialId)
        assertTrue(credentialResult.isFailure)
        
        var addResult = pushClient.saveCredential(testCredential)
        assertTrue(addResult.isFailure)
        
        var deleteResult = pushClient.deleteCredential(testCredentialId)
        assertTrue(deleteResult.isFailure)
        
        var tokenResult = pushClient.setDeviceToken(testDeviceToken)
        assertTrue(tokenResult.isFailure)
        
        var getTokenResult = pushClient.getDeviceToken()
        assertTrue(getTokenResult.isFailure)
        
        var processResult = pushClient.processNotification(mapOf<String, Any>())
        assertTrue(processResult.isFailure)
        
        var approveResult = pushClient.approveNotification(testNotificationId)
        assertTrue(approveResult.isFailure)
        
        var denyResult = pushClient.denyNotification(testNotificationId)
        assertTrue(denyResult.isFailure)
        
        var pendingResult = pushClient.getPendingNotifications()
        assertTrue(pendingResult.isFailure)
        
        var allNotificationsResult = pushClient.getAllNotifications()
        assertTrue(allNotificationsResult.isFailure)
        
        var getNotificationResult = pushClient.getNotification(testNotificationId)
        assertTrue(getNotificationResult.isFailure)
        
        var cleanupResult = pushClient.cleanupNotifications()
        assertTrue(cleanupResult.isFailure)
    }

    @Test
    fun `test companion object defaultStorage creates encrypted storage by default`() = runTest {
        // Given - set up context provider
        ContextProvider.init(mockContext)
        val config = mockk<PushConfiguration>().apply {
            every { context } returns mockContext
            every { logger } returns mockLogger
        }

        // When
        val storage = PushClient.defaultStorage(config)

        // Then - verify storage is created with encryption (KeyStorePassphraseProvider by default)
        assertNotNull(storage)
        assertTrue(storage is PushStorage)
        assertTrue(storage is SQLPushStorage)
    }

    @Test
    fun `test companion object invoke creates and initializes client`() = runTest {
        // Given
        coEvery { mockStorage.initialize() } returns Unit
        coEvery { mockStorage.getAllPushCredentials() } returns emptyList()

        // When
        val newClient = PushClient {
            logger = mockLogger
            storage = mockStorage
        }

        // Then
        assertNotNull(newClient)
        // Verify storage was initialized
        coVerify(exactly = 1) { mockStorage.initialize() }
    }

    @Test
    fun `test approveNotification returns failure with NotificationExpiredException`() = runTest {
        // Given
        val expiredException = com.pingidentity.mfa.push.exception.NotificationExpiredException(
            testNotificationId,
            60
        )
        coEvery { mockPushService.approveNotification(testNotificationId, emptyMap()) } throws expiredException

        // When
        val result = pushClient.approveNotification(testNotificationId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is com.pingidentity.mfa.push.exception.NotificationExpiredException)
        val exception = result.exceptionOrNull() as com.pingidentity.mfa.push.exception.NotificationExpiredException
        assertEquals(testNotificationId, exception.notificationId)
        assertEquals(60, exception.ttlSeconds)
    }

    @Test
    fun `test approveNotification returns failure with NotificationNotFoundException`() = runTest {
        // Given
        val notFoundException = com.pingidentity.mfa.push.exception.NotificationNotFoundException(
            testNotificationId
        )
        coEvery { mockPushService.approveNotification(testNotificationId, emptyMap()) } throws notFoundException

        // When
        val result = pushClient.approveNotification(testNotificationId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is com.pingidentity.mfa.push.exception.NotificationNotFoundException)
        val exception = result.exceptionOrNull() as com.pingidentity.mfa.push.exception.NotificationNotFoundException
        assertEquals(testNotificationId, exception.notificationId)
    }

    @Test
    fun `test approveNotification returns failure with CredentialNotFoundException`() = runTest {
        // Given
        val credentialNotFoundException = com.pingidentity.mfa.commons.exception.CredentialNotFoundException(
            "test-credential-id"
        )
        coEvery { mockPushService.approveNotification(testNotificationId, emptyMap()) } throws credentialNotFoundException

        // When
        val result = pushClient.approveNotification(testNotificationId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is com.pingidentity.mfa.commons.exception.CredentialNotFoundException)
        val exception = result.exceptionOrNull() as com.pingidentity.mfa.commons.exception.CredentialNotFoundException
        assertEquals("test-credential-id", exception.credentialId)
    }

    @Test
    fun `test denyNotification returns failure with NotificationExpiredException`() = runTest {
        // Given
        val expiredException = com.pingidentity.mfa.push.exception.NotificationExpiredException(
            testNotificationId,
            60
        )
        coEvery { mockPushService.denyNotification(testNotificationId, emptyMap()) } throws expiredException

        // When
        val result = pushClient.denyNotification(testNotificationId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is com.pingidentity.mfa.push.exception.NotificationExpiredException)
    }

    @Test
    fun `test addCredentialFromUri returns failure with DeviceTokenMissingException`() = runTest {
        // Given
        val deviceTokenException = com.pingidentity.mfa.push.exception.DeviceTokenMissingException()
        coEvery { mockPushService.addCredentialFromUri(testUri) } throws deviceTokenException

        // When
        val result = pushClient.addCredentialFromUri(testUri)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is com.pingidentity.mfa.push.exception.DeviceTokenMissingException)
        assertTrue(result.exceptionOrNull()?.message?.contains("Device token not set") == true)
    }
}

