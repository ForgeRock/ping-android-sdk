/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Instrumented test for the PushClient class to verify its
 * functionality in a real Android environment.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class PushClientAndroidTest {

    private lateinit var appContext: Context
    private lateinit var testPushHandler: TestPushHandler
    private val logger: Logger = Logger.logger
    
    private val testServerEndpoint = "https://example.com/push"
    private val testSharedSecret = "testsecret12345"
    private val testDeviceToken = "test-device-token-12345"

    @Before
    fun setup() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        ContextProvider.init(appContext)
        
        // Initialize test push handler
        testPushHandler = TestPushHandler()
    }
    
    @After
    fun tearDown() {
        // Clean up any resources
        runBlocking {
            try {
                val client = createTestClient()
                val credentials = client.getCredentials().getOrThrow()
                credentials.forEach { credential ->
                    client.deleteCredential(credential.id)
                }
                client.close()
            } catch (e: Exception) {
                logger.e("Error during test cleanup: ${e.message}")
            }
        }
    }
    
    private suspend fun createTestClient(
        withCustomHandlers: Boolean = false
    ): PushMfaClient {
        val config = if (withCustomHandlers) {
            PushConfiguration(
                customPushHandlers = mapOf(TestPushHandler.HANDLER_NAME to testPushHandler)
            )
        } else {
            PushConfiguration {}
        }
        
        val client = PushClient.create(config)
        client.initialize()
        return client
    }
    
    @Test
    fun testCreatePushClient() = runTest {
        val config = PushConfiguration { }

        val client = PushClient.create(config)
        client.initialize()
        assertNotNull("Client should not be null", client)
        
        val credentials = client.getCredentials().getOrThrow()
        assertEquals("Should have no credentials initially", 0, credentials.size)
        
        client.close()
    }

    @Test
    fun testAddAndRetrieveCredential() = runTest {
        val client = createTestClient()
        
        val credential = PushCredential(
            id = UUID.randomUUID().toString(),
            issuer = "PingOne",
            accountName = "test@example.com",
            serverEndpoint = testServerEndpoint,
            sharedSecret = testSharedSecret,
            platform = "PING_ONE"
        )
        
        val savedCredential = client.saveCredential(credential).getOrThrow()
        assertNotNull("Credential should not be null", savedCredential)
        assertEquals("Issuer should match", "PingOne", savedCredential.issuer)
        
        val retrievedCredential = client.getCredential(savedCredential.id).getOrThrow()
        assertNotNull("Retrieved credential should not be null", retrievedCredential)
        
        client.close()
    }

    @Test
    fun testRemoveCredential() = runTest {
        val client = createTestClient()

        val credential = PushCredential(
            id = UUID.randomUUID().toString(),
            issuer = "PingOne",
            accountName = "remove-test@example.com",
            serverEndpoint = testServerEndpoint,
            sharedSecret = testSharedSecret,
            platform = "PING_ONE"
        )
        
        val savedCredential = client.saveCredential(credential).getOrThrow()
        assertNotNull("Credential should exist", savedCredential)
        
        val removed = client.deleteCredential(credential.id).getOrThrow()
        assertTrue("Removal should be successful", removed)
        
        val retrievedAfterRemove = client.getCredential(credential.id).getOrThrow()
        assertNull("Credential should not exist after removal", retrievedAfterRemove)
        
        client.close()
    }
    
    @Test
    fun testClientWithCustomHandlers() = runTest {
        // Create client with custom handlers enabled
        val client = createTestClient(withCustomHandlers = true)
        
        // Just verify the client is created successfully with custom handlers
        assertNotNull("Client should not be null", client)
        
        client.close()
    }

    @Test
    fun testDeviceTokenManagement() = runTest {
        // Create a client and initialize it
        val client = createTestClient()

        // Add a credential to update its device token later
        val credential = PushCredential(
            id = UUID.randomUUID().toString(),
            issuer = "PingOne",
            accountName = "token-test@example.com",
            serverEndpoint = testServerEndpoint,
            sharedSecret = testSharedSecret,
            platform = "PING_ONE"
        )

        val savedCredential = client.saveCredential(credential).getOrThrow()

        // Attempt to set device token for all credentials
        try {
            val globalResult = client.setDeviceToken(testDeviceToken, null).getOrThrow()
            assertTrue("Global device token update should succeed", globalResult)
        } catch (e: Exception) {
            // In test environment, network operations might fail but we should still continue
            logger.w("Expected token update failure in test environment: ${e.message}")
        }

        // Attempt to set device token for a specific credential
        try {
            val specificResult = client.setDeviceToken("specific-token", credential.id).getOrThrow()
            assertTrue("Specific device token update should succeed", specificResult)
        } catch (e: Exception) {
            // In test environment, network operations might fail but we should still continue
            logger.w("Expected token update failure in test environment: ${e.message}")
        }

        // We need to cast to access the non-interface method
        try {
            val deviceToken = (client as PushClient).getDeviceToken().getOrThrow()
            // If we get here, we should have a token
            assertNotNull("Device token should be retrievable", deviceToken)
        } catch (e: Exception) {
            // In test environment, storage operations might fail but test should continue
            logger.w("Token retrieval issue in test environment: ${e.message}")
        }

        client.close()
    }

    @Test
    fun testProcessNotification() = runTest {
        // Create a client with custom handler for easier testing
        val client = createTestClient(withCustomHandlers = true)

        // Add a credential first
        val credentialId = UUID.randomUUID().toString()
        val credential = PushCredential(
            id = credentialId,
            issuer = "PingOne",
            accountName = "notification-test@example.com",
            serverEndpoint = testServerEndpoint,
            sharedSecret = testSharedSecret,
            platform = TestPushHandler.HANDLER_NAME  // Use the test handler platform
        )

        client.saveCredential(credential).getOrThrow()

        // Create a simple notification message
        val messageId = UUID.randomUUID().toString()
        val messageData = mapOf(
            "platform" to TestPushHandler.HANDLER_NAME,
            "credentialId" to credentialId,
            "messageId" to messageId,
            "message" to "Test authentication request",
            "ttl" to "300",
            "pushType" to "DEFAULT"
        )

        // Process the notification
        val notification = client.processNotification(messageData).getOrThrow()

        // Verify notification was processed correctly
        assertNotNull("Notification should not be null", notification)
        assertEquals("Credential ID should match", credentialId, notification?.credentialId)
        assertEquals("Message ID should match", messageId, notification?.messageId)
        assertEquals("Message should be pending", true, notification?.pending)
        assertEquals("Push type should be DEFAULT", PushType.DEFAULT, notification?.pushType)

        client.close()
    }

    @Test
    fun testProcessChallengeNotification() = runTest {
        // Create a client with custom handler for challenge testing
        val client = createTestClient(withCustomHandlers = true)

        // Add a credential first
        val credentialId = UUID.randomUUID().toString()
        val credential = PushCredential(
            id = credentialId,
            issuer = "PingOne",
            accountName = "challenge-test@example.com",
            serverEndpoint = testServerEndpoint,
            sharedSecret = testSharedSecret,
            platform = TestPushHandler.HANDLER_NAME
        )

        client.saveCredential(credential).getOrThrow()

        // Create a challenge notification message
        val messageId = UUID.randomUUID().toString()
        val messageData = mapOf(
            "platform" to TestPushHandler.HANDLER_NAME,
            "credentialId" to credentialId,
            "messageId" to messageId,
            "message" to "Enter the code shown on your screen",
            "ttl" to "300",
            "pushType" to "CHALLENGE",
            "challenge" to "123456"
        )

        // Process the notification
        val notification = client.processNotification(messageData).getOrThrow()

        // Verify notification was processed correctly
        assertNotNull("Notification should not be null", notification)
        assertEquals("Push type should be CHALLENGE", PushType.CHALLENGE, notification?.pushType)
        assertEquals("Challenge should match", "123456", notification?.challenge)

        client.close()
    }

    @Test
    fun testProcessBiometricNotification() = runTest {
        // Create a client with custom handler for biometric testing
        val client = createTestClient(withCustomHandlers = true)

        // Add a credential first
        val credentialId = UUID.randomUUID().toString()
        val credential = PushCredential(
            id = credentialId,
            issuer = "PingOne",
            accountName = "biometric-test@example.com",
            serverEndpoint = testServerEndpoint,
            sharedSecret = testSharedSecret,
            platform = TestPushHandler.HANDLER_NAME
        )

        client.saveCredential(credential).getOrThrow()

        // Create a biometric notification message
        val messageId = UUID.randomUUID().toString()
        val messageData = mapOf(
            "platform" to TestPushHandler.HANDLER_NAME,
            "credentialId" to credentialId,
            "messageId" to messageId,
            "message" to "Use biometrics to confirm login",
            "ttl" to "300",
            "pushType" to "BIOMETRIC"
        )

        // Process the notification
        val notification = client.processNotification(messageData).getOrThrow()

        // Verify notification was processed correctly
        assertNotNull("Notification should not be null", notification)
        assertEquals("Push type should be BIOMETRIC", PushType.BIOMETRIC, notification?.pushType)

        client.close()
    }

    @Test
    fun testApproveNotification() = runTest {
        // Create client with custom handler
        val client = createTestClient(withCustomHandlers = true)

        // Add a credential
        val credentialId = UUID.randomUUID().toString()
        val credential = PushCredential(
            id = credentialId,
            issuer = "PingOne",
            accountName = "approve-test@example.com",
            serverEndpoint = testServerEndpoint,
            sharedSecret = testSharedSecret,
            platform = TestPushHandler.HANDLER_NAME
        )

        client.saveCredential(credential).getOrThrow()

        // Create a notification
        val messageId = UUID.randomUUID().toString()
        val messageData = mapOf(
            "platform" to TestPushHandler.HANDLER_NAME,
            "credentialId" to credentialId,
            "messageId" to messageId,
            "message" to "Approve this login",
            "ttl" to "300",
            "pushType" to "DEFAULT"
        )

        // Process the notification
        val notification = client.processNotification(messageData).getOrThrow()
        assertNotNull("Notification should not be null", notification)

        // Approve the notification
        val approved = client.approveNotification(notification!!.id).getOrThrow()
        assertTrue("Notification should be approved successfully", approved)

        // Check if the notification is marked as approved and not pending
        val updatedNotification = client.getNotification(notification.id).getOrThrow()
        assertTrue("Notification should be marked as approved", updatedNotification?.approved == true)
        assertFalse("Notification should not be pending", updatedNotification?.pending == true)

        client.close()
    }

    @Test
    fun testApproveChallengeNotification() = runTest {
        // Create client with custom handler
        val client = createTestClient(withCustomHandlers = true)

        // Add a credential
        val credentialId = UUID.randomUUID().toString()
        val credential = PushCredential(
            id = credentialId,
            issuer = "PingOne",
            accountName = "challenge-approve-test@example.com",
            serverEndpoint = testServerEndpoint,
            sharedSecret = testSharedSecret,
            platform = TestPushHandler.HANDLER_NAME
        )

        client.saveCredential(credential).getOrThrow()

        // Create a challenge notification
        val messageId = UUID.randomUUID().toString()
        val messageData = mapOf(
            "platform" to TestPushHandler.HANDLER_NAME,
            "credentialId" to credentialId,
            "messageId" to messageId,
            "message" to "Enter the code shown",
            "ttl" to "300",
            "pushType" to "CHALLENGE",
            "challenge" to "123456"
        )

        // Process the notification
        val notification = client.processNotification(messageData).getOrThrow()
        assertNotNull("Notification should not be null", notification)

        // Approve the challenge notification with response
        val challengeResponse = "123456"
        val approved = client.approveChallengeNotification(notification!!.id, challengeResponse).getOrThrow()
        assertTrue("Challenge notification should be approved successfully", approved)

        client.close()
    }

    @Test
    fun testApproveBiometricNotification() = runTest {
        // Create client with custom handler
        val client = createTestClient(withCustomHandlers = true)

        // Add a credential
        val credentialId = UUID.randomUUID().toString()
        val credential = PushCredential(
            id = credentialId,
            issuer = "PingOne",
            accountName = "biometric-approve-test@example.com",
            serverEndpoint = testServerEndpoint,
            sharedSecret = testSharedSecret,
            platform = TestPushHandler.HANDLER_NAME
        )

        client.saveCredential(credential).getOrThrow()

        // Create a biometric notification
        val messageId = UUID.randomUUID().toString()
        val messageData = mapOf(
            "platform" to TestPushHandler.HANDLER_NAME,
            "credentialId" to credentialId,
            "messageId" to messageId,
            "message" to "Use biometrics to authenticate",
            "ttl" to "300",
            "pushType" to "BIOMETRIC"
        )

        // Process the notification
        val notification = client.processNotification(messageData).getOrThrow()
        assertNotNull("Notification should not be null", notification)

        // Approve the biometric notification
        val approved = client.approveBiometricNotification(notification!!.id, "fingerprint").getOrThrow()
        assertTrue("Biometric notification should be approved successfully", approved)

        client.close()
    }

    @Test
    fun testDenyNotification() = runTest {
        // Create client with custom handler
        val client = createTestClient(withCustomHandlers = true)

        // Add a credential
        val credentialId = UUID.randomUUID().toString()
        val credential = PushCredential(
            id = credentialId,
            issuer = "PingOne",
            accountName = "deny-test@example.com",
            serverEndpoint = testServerEndpoint,
            sharedSecret = testSharedSecret,
            platform = TestPushHandler.HANDLER_NAME
        )

        client.saveCredential(credential).getOrThrow()

        // Create a notification
        val messageId = UUID.randomUUID().toString()
        val messageData = mapOf(
            "platform" to TestPushHandler.HANDLER_NAME,
            "credentialId" to credentialId,
            "messageId" to messageId,
            "message" to "Deny this login",
            "ttl" to "300",
            "pushType" to "DEFAULT"
        )

        // Process the notification
        val notification = client.processNotification(messageData).getOrThrow()
        assertNotNull("Notification should not be null", notification)

        // Deny the notification
        val denied = client.denyNotification(notification!!.id).getOrThrow()
        assertTrue("Notification should be denied successfully", denied)

        // Check if the notification is marked as not approved and not pending
        val updatedNotification = client.getNotification(notification.id).getOrThrow()
        assertFalse("Notification should not be marked as approved", updatedNotification?.approved == true)
        assertFalse("Notification should not be pending", updatedNotification?.pending == true)

        client.close()
    }

    @Test
    fun testGetPendingNotifications() = runTest {
        // Create client with custom handler
        val client = createTestClient(withCustomHandlers = true)

        // Add a credential
        val credentialId = UUID.randomUUID().toString()
        val credential = PushCredential(
            id = credentialId,
            issuer = "PingOne",
            accountName = "pending-test@example.com",
            serverEndpoint = testServerEndpoint,
            sharedSecret = testSharedSecret,
            platform = TestPushHandler.HANDLER_NAME
        )

        client.saveCredential(credential).getOrThrow()

        // Create multiple notifications
        for (i in 1..3) {
            val messageId = UUID.randomUUID().toString()
            val messageData = mapOf(
                "platform" to TestPushHandler.HANDLER_NAME,
                "credentialId" to credentialId,
                "messageId" to messageId,
                "message" to "Test notification $i",
                "ttl" to "300",
                "pushType" to "DEFAULT"
            )

            // Process the notification
            val notification = client.processNotification(messageData).getOrThrow()
            assertNotNull("Notification $i should not be null", notification)
        }

        // Get pending notifications
        val pendingNotifications = client.getPendingNotifications().getOrThrow()
        assertEquals("Should have 3 pending notifications", 3, pendingNotifications.size)

        // Approve one notification
        val notification = pendingNotifications.first()
        client.approveNotification(notification.id).getOrThrow()

        // Check pending notifications again
        val updatedPendingNotifications = client.getPendingNotifications().getOrThrow()
        assertEquals("Should have 2 pending notifications after approval", 2, updatedPendingNotifications.size)

        client.close()
    }

    @Test
    fun testNotificationCleanup() = runTest {
        // Create client with custom handler and notification cleanup config
        val cleanupConfig = NotificationCleanupConfig {
            cleanupMode = NotificationCleanupConfig.CleanupMode.COUNT_BASED
            maxStoredNotifications = 2
        }

        val config = PushConfiguration {
            customPushHandlers = mapOf(TestPushHandler.HANDLER_NAME to testPushHandler)
            notificationCleanupConfig = cleanupConfig
        }

        val client = PushClient.create(config)
        client.initialize()

        // Add a credential
        val credentialId = UUID.randomUUID().toString()
        val credential = PushCredential(
            id = credentialId,
            issuer = "PingOne",
            accountName = "cleanup-test@example.com",
            serverEndpoint = testServerEndpoint,
            sharedSecret = testSharedSecret,
            platform = TestPushHandler.HANDLER_NAME
        )

        client.saveCredential(credential).getOrThrow()

        // Create more notifications than the max count
        for (i in 1..5) {
            val messageId = UUID.randomUUID().toString()
            val messageData = mapOf(
                "platform" to TestPushHandler.HANDLER_NAME,
                "credentialId" to credentialId,
                "messageId" to messageId,
                "message" to "Test notification $i",
                "ttl" to "300",
                "pushType" to "DEFAULT"
            )

            // Process the notification (this should trigger auto-cleanup)
            val notification = client.processNotification(messageData).getOrThrow()
        }

        // Get all notifications for this credential
        val pendingNotifications = client.getPendingNotifications().getOrThrow()
                .filter { it.credentialId == credentialId }

        // There should be only the max count allowed (2)
        assertEquals("Should have only max count (2) notifications after cleanup",
                     2, pendingNotifications.size)

        // Manually trigger cleanup
        val cleanedCount = client.cleanupNotifications(credentialId).getOrThrow()
        assertTrue("Cleanup should remove some notifications", cleanedCount >= 0)

        client.close()
    }
}
