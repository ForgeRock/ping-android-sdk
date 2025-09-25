/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.push.TestPushHandler.Companion.HANDLER_NAME
import com.pingidentity.mfa.push.storage.SharedPrefsPushStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Instrumented test specifically for notification cleanup functionality to verify
 * the different cleanup modes work as expected.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationCleanupTest {

    private lateinit var pushStorage: SharedPrefsPushStorage
    private lateinit var cleanupManager: NotificationCleanupManager
    private lateinit var testCredential: PushCredential
    private val testPrefName = "cleanup_test_prefs_${System.currentTimeMillis()}"
    private val logger: Logger = Logger.logger

    @Before
    fun setup() = runTest {
        // Get the application context
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // Create storage instance with a unique test preferences name (use SharedPrefs for simplicity in tests)
        pushStorage = SharedPrefsPushStorage(appContext, testPrefName)

        // Initialize the storage
        pushStorage.initialize()

        // Clean up any existing test data
        cleanupTestData()

        // Create and store a test credential that will be used in all tests
        testCredential = createTestCredential()
        pushStorage.storePushCredential(testCredential)
    }

    @After
    fun tearDown() = runTest {
        // Clean up after tests
        cleanupTestData()
        pushStorage.close()
    }

    /**
     * Helper method to clean up any test data
     */
    private suspend fun cleanupTestData() {
        try {
            // Clear all data
            pushStorage.clearPushCredentials()
            pushStorage.clearPushNotifications()
        } catch (e: Exception) {
            println("Warning: Failed to clean up test data: ${e.message}")
        }
    }

    /**
     * Helper method to create test credential
     */
    private fun createTestCredential(): PushCredential {
        return PushCredential(
            id = UUID.randomUUID().toString(),
            userId = "test-user",
            resourceId = "test-resource",
            issuer = "Test Issuer",
            displayIssuer = "Test Issuer",
            accountName = "test@example.com",
            displayAccountName = "test@example.com",
            serverEndpoint = "https://test-endpoint.example.com",
            sharedSecret = "test-shared-secret",
            platform = PushPlatform.PING_ONE.name,
            createdAt = Date(),
            imageURL = null,
            backgroundColor = null,
            policies = null,
            lockingPolicy = null,
            isLocked = false
        )
    }

    /**
     * Helper method to create a notification with a specific age in days
     */
    private fun createNotificationWithAge(ageInDays: Int): PushNotification {
        val pastDate = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ageInDays.toLong()))

        return PushNotification(
            id = UUID.randomUUID().toString(),
            credentialId = testCredential.id,
            messageId = "msg-${UUID.randomUUID()}",
            messageText = "Test notification aged $ageInDays days old",
            customPayload = null,
            challenge = "test-challenge",
            numbersChallenge = "1234",
            loadBalancer = null,
            contextInfo = null,
            pushType = PushType.DEFAULT,
            ttl = 300,
            pending = true,
            approved = false,
            createdAt = pastDate,
            respondedAt = null,
            additionalData = null
        )
    }

    /**
     * Test cleanup with NONE mode does not remove any notifications
     */
    @Test
    fun testNoCleanup() = runTest {
        // Create a cleanup manager with NONE mode
        val config = NotificationCleanupConfig {
            cleanupMode = NotificationCleanupConfig.CleanupMode.NONE
        }
        cleanupManager = NotificationCleanupManager(pushStorage, config, logger)

        // Store test notifications with various ages
        val notification1 = createNotificationWithAge(5)
        val notification2 = createNotificationWithAge(10)
        val notification3 = createNotificationWithAge(20)
        val notification4 = createNotificationWithAge(40)

        pushStorage.storePushNotification(notification1)
        pushStorage.storePushNotification(notification2)
        pushStorage.storePushNotification(notification3)
        pushStorage.storePushNotification(notification4)

        // Verify all notifications are stored
        val beforeCleanup = pushStorage.getAllPushNotifications()
        assertEquals("Should have 4 notifications before cleanup", 4, beforeCleanup.size)

        // Run cleanup
        val removed = cleanupManager.runCleanup()

        // Verify no notifications were removed
        assertEquals("Should not remove any notifications with NONE mode", 0, removed)

        // Verify all notifications still exist
        val afterCleanup = pushStorage.getAllPushNotifications()
        assertEquals("Should still have 4 notifications after cleanup", 4, afterCleanup.size)
    }

    /**
     * Test COUNT_BASED cleanup removes oldest notifications when count exceeds maximum
     */
    @Test
    fun testCountBasedCleanup() = runTest {
        // Create a cleanup manager with COUNT_BASED mode
        val config = NotificationCleanupConfig {
            cleanupMode = NotificationCleanupConfig.CleanupMode.COUNT_BASED
            maxStoredNotifications = 2
        }
        cleanupManager = NotificationCleanupManager(pushStorage, config, logger)

        // Store test notifications with various ages
        val notification1 = createNotificationWithAge(5)  // Newer
        val notification2 = createNotificationWithAge(10) // Newer
        val notification3 = createNotificationWithAge(20) // Older - should be removed
        val notification4 = createNotificationWithAge(40) // Oldest - should be removed

        // Store in reverse order to ensure we're testing by timestamp, not insertion order
        pushStorage.storePushNotification(notification4)
        pushStorage.storePushNotification(notification3)
        pushStorage.storePushNotification(notification2)
        pushStorage.storePushNotification(notification1)

        // Verify all notifications are stored
        val beforeCleanup = pushStorage.getAllPushNotifications()
        assertEquals("Should have 4 notifications before cleanup", 4, beforeCleanup.size)

        // Run cleanup
        val removed = cleanupManager.runCleanup()

        // Verify 2 notifications were removed (to keep only 2)
        assertEquals("Should remove 2 notifications with COUNT_BASED mode", 2, removed)

        // Verify only the 2 newest notifications remain
        val afterCleanup = pushStorage.getAllPushNotifications()
        assertEquals("Should have 2 notifications after cleanup", 2, afterCleanup.size)

        // Verify the correct notifications remain (the newer ones)
        val remainingIds = afterCleanup.map { it.id }.toSet()
        assertTrue("Should keep notification 1", remainingIds.contains(notification1.id))
        assertTrue("Should keep notification 2", remainingIds.contains(notification2.id))
    }

    /**
     * Test AGE_BASED cleanup removes notifications older than maximum age
     */
    @Test
    fun testAgeBasedCleanup() = runTest {
        // Create a cleanup manager with AGE_BASED mode
        val config = NotificationCleanupConfig {
            cleanupMode = NotificationCleanupConfig.CleanupMode.AGE_BASED
            maxNotificationAgeDays = 15
        }
        cleanupManager = NotificationCleanupManager(pushStorage, config, logger)

        // Store test notifications with various ages
        val notification1 = createNotificationWithAge(5)  // Newer - should be kept
        val notification2 = createNotificationWithAge(10) // Newer - should be kept
        val notification3 = createNotificationWithAge(20) // Older than max - should be removed
        val notification4 = createNotificationWithAge(40) // Older than max - should be removed

        pushStorage.storePushNotification(notification1)
        pushStorage.storePushNotification(notification2)
        pushStorage.storePushNotification(notification3)
        pushStorage.storePushNotification(notification4)

        // Verify all notifications are stored
        val beforeCleanup = pushStorage.getAllPushNotifications()
        assertEquals("Should have 4 notifications before cleanup", 4, beforeCleanup.size)

        // Run cleanup
        val removed = cleanupManager.runCleanup()

        // Verify 2 notifications were removed (those older than 15 days)
        assertEquals("Should remove 2 notifications with AGE_BASED mode", 2, removed)

        // Verify only the notifications newer than maxAge remain
        val afterCleanup = pushStorage.getAllPushNotifications()
        assertEquals("Should have 2 notifications after cleanup", 2, afterCleanup.size)

        // Verify the correct notifications remain (the newer ones)
        val remainingIds = afterCleanup.map { it.id }.toSet()
        assertTrue("Should keep notification 1", remainingIds.contains(notification1.id))
        assertTrue("Should keep notification 2", remainingIds.contains(notification2.id))
    }

    /**
     * Test HYBRID cleanup applies both count and age limits
     */
    @Test
    fun testHybridCleanup() = runTest {
        // Create a cleanup manager with HYBRID mode
        val config = NotificationCleanupConfig {
            cleanupMode = NotificationCleanupConfig.CleanupMode.HYBRID
            maxStoredNotifications = 3
            maxNotificationAgeDays = 15
        }
        cleanupManager = NotificationCleanupManager(pushStorage, config, logger)

        // Store test notifications with various ages
        val notification1 = createNotificationWithAge(5)   // Newer - should be kept
        val notification2 = createNotificationWithAge(10)  // Newer - should be kept
        val notification3 = createNotificationWithAge(12)  // Newer - should be kept
        val notification4 = createNotificationWithAge(20)  // Older than max age - should be removed
        val notification5 = createNotificationWithAge(40)  // Older than max age - should be removed

        // Store in a way that tests both count and age
        pushStorage.storePushNotification(notification1)
        pushStorage.storePushNotification(notification2)
        pushStorage.storePushNotification(notification3)
        pushStorage.storePushNotification(notification4)
        pushStorage.storePushNotification(notification5)

        // Verify all notifications are stored
        val beforeCleanup = pushStorage.getAllPushNotifications()
        assertEquals("Should have 5 notifications before cleanup", 5, beforeCleanup.size)

        // Run cleanup
        val removed = cleanupManager.runCleanup()

        // Verify 2 notifications were removed (those older than 15 days)
        assertEquals("Should remove 2 notifications with HYBRID mode", 2, removed)

        // Verify only the notifications that meet both criteria remain
        val afterCleanup = pushStorage.getAllPushNotifications()
        assertEquals("Should have 3 notifications after cleanup", 3, afterCleanup.size)

        // Verify the correct notifications remain
        val remainingIds = afterCleanup.map { it.id }.toSet()
        assertTrue("Should keep notification 1", remainingIds.contains(notification1.id))
        assertTrue("Should keep notification 2", remainingIds.contains(notification2.id))
        assertTrue("Should keep notification 3", remainingIds.contains(notification3.id))
    }

    /**
     * Test cleanup with a specific credential ID
     */
    @Test
    fun testCleanupWithCredentialId() = runTest {
        // Create two different credentials
        val credential1 = testCredential
        val credential2 = createTestCredential()
        pushStorage.storePushCredential(credential2)

        // Create a cleanup manager with COUNT_BASED mode and max 1 notification
        val config = NotificationCleanupConfig {
            cleanupMode = NotificationCleanupConfig.CleanupMode.COUNT_BASED
            maxStoredNotifications = 1
        }
        cleanupManager = NotificationCleanupManager(pushStorage, config, logger)

        // Store notifications for each credential
        val notification1ForCred1 = createNotificationWithAge(5)
        val notification2ForCred1 = createNotificationWithAge(10)
        notification1ForCred1.copy(credentialId = credential1.id).let { pushStorage.storePushNotification(it) }
        notification2ForCred1.copy(credentialId = credential1.id).let { pushStorage.storePushNotification(it) }

        val notification1ForCred2 = createNotificationWithAge(5)
        val notification2ForCred2 = createNotificationWithAge(10)
        notification1ForCred2.copy(credentialId = credential2.id).let { pushStorage.storePushNotification(it) }
        notification2ForCred2.copy(credentialId = credential2.id).let { pushStorage.storePushNotification(it) }

        // Verify we have 4 notifications total
        val allNotifications = pushStorage.getAllPushNotifications()
        assertEquals("Should have 4 notifications total", 4, allNotifications.size)

        // Run cleanup for credential1 only
        val removed = cleanupManager.runCleanup(credential1.id)

        // Verify 1 notification was removed from credential1
        assertEquals("Should remove 1 notification for credential1", 1, removed)

        // Verify we now have 3 notifications total
        val afterCleanup = pushStorage.getAllPushNotifications()
        assertEquals("Should have 3 notifications after cleanup", 3, afterCleanup.size)

        // Count notifications for each credential
        val cred1Notifications = afterCleanup.filter { it.credentialId == credential1.id }
        val cred2Notifications = afterCleanup.filter { it.credentialId == credential2.id }

        // Verify counts
        assertEquals("Should have 1 notification for credential1", 1, cred1Notifications.size)
        assertEquals("Should have 2 notifications for credential2", 2, cred2Notifications.size)
    }

    /**
     * Test auto cleanup in PushClient when processing a new notification
     */
    @Test
    fun testPushClientAutoCleanup() = runTest {
        // Create a TestPushHandler for our simplified message format
        val testHandler = TestPushHandler(logger)
        val handlers = mapOf(PushPlatform.PING_ONE.name to testHandler)

        // Create a PushConfiguration with COUNT_BASED cleanup mode and our test handler
        val config = PushConfiguration {
            // Set count-based cleanup with max 2 notifications
            notificationCleanupConfig = NotificationCleanupConfig {
                cleanupMode = NotificationCleanupConfig.CleanupMode.COUNT_BASED
                maxStoredNotifications = 2
            }
            // Add our test handler
            customPushHandlers = handlers
            // Use SharedPrefsPushStorage for simplicity in tests
            storage = pushStorage
        }

        // Create PushClient with SharedPrefsPushStorage
        val pushClient = PushClient(config)
        pushClient.initialize()

        // Create a test credential through the client
        val savedCredential = pushClient.saveCredential(testCredential).getOrThrow()

        // Create 2 test notifications with various ages
        val notification1 = createNotificationWithAge(5)  // Newer notification - should be kept
        val notification2 = createNotificationWithAge(10) // Older notification - should be removed when new one is added

        // Store the notifications directly in storage
        pushStorage.storePushNotification(notification1)
        pushStorage.storePushNotification(notification2)

        // Verify we have 2 notifications
        val beforeProcessing = pushStorage.getAllPushNotifications()
        assertEquals("Should have 2 notifications before processing new one", 2, beforeProcessing.size)

        // Process a new notification through the client (this should trigger auto-cleanup)
        val messageData = mapOf(
            "messageId" to "test-message-id",
            "credentialId" to savedCredential.id,
            "message" to "Test message",
            "challenge" to "test-challenge",
            "numbersChallenge" to "1234",
            "ttl" to "300",
            "pushType" to PushType.DEFAULT.name,
            "platform" to HANDLER_NAME // This is important - identifies the platform
        )
        val result = pushClient.processNotification(messageData)

        // Verify the notification was processed
        val processedNotification = result.getOrThrow()
        assertNotNull("Should successfully process the notification", processedNotification)

        // Verify we still have only 2 notifications (oldest should have been removed)
        val afterProcessing = pushStorage.getAllPushNotifications()
        assertEquals("Should still have only 2 notifications after processing", 2, afterProcessing.size)

        // Get the notification IDs
        val notificationIds = afterProcessing.map { it.id }.toSet()

        // Verify the oldest notification was removed and the new one was added
        assertTrue("The new notification should exist", notificationIds.contains(processedNotification?.id))
        assertTrue("The newer notification should exist", notificationIds.contains(notification1.id))
        assertTrue("The oldest notification should have been removed", !notificationIds.contains(notification2.id))
    }

    /**
     * Test manual cleanup in PushClient
     */
    @Test
    fun testPushClientManualCleanup() = runTest {
        // Create a PushConfiguration with AGE_BASED cleanup mode
        val config = PushConfiguration {
            // Set age-based cleanup with max age 15 days
            notificationCleanupConfig = NotificationCleanupConfig {
                cleanupMode = NotificationCleanupConfig.CleanupMode.AGE_BASED
                maxNotificationAgeDays = 15
            }
            // Use SharedPrefsPushStorage for simplicity in tests
            storage = pushStorage
        }

        // Create PushClient with SharedPrefsPushStorage
        val pushClient = PushClient(config)
        pushClient.initialize()

        // Create and store a test credential through the client
        val savedCredential = pushClient.saveCredential(testCredential).getOrThrow()

        // Create test notifications with various ages and store them directly
        val notification1 = createNotificationWithAge(5)
        val notification2 = createNotificationWithAge(10)
        val notification3 = createNotificationWithAge(20) // Older than max age
        val notification4 = createNotificationWithAge(30) // Older than max age

        pushStorage.storePushNotification(notification1)
        pushStorage.storePushNotification(notification2)
        pushStorage.storePushNotification(notification3)
        pushStorage.storePushNotification(notification4)

        // Verify we have 4 notifications
        val beforeCleanup = pushStorage.getAllPushNotifications()
        assertEquals("Should have 4 notifications before manual cleanup", 4, beforeCleanup.size)

        // Run manual cleanup through the client
        val cleanupResult = pushClient.cleanupNotifications()
        val removed = cleanupResult.getOrThrow()

        // Verify 2 notifications were removed
        assertEquals("Should have removed 2 notifications", 2, removed)

        // Verify only 2 notifications remain (the newer ones)
        val afterCleanup = pushStorage.getAllPushNotifications()
        assertEquals("Should have 2 notifications after cleanup", 2, afterCleanup.size)

        // Verify the correct notifications remain
        val remainingIds = afterCleanup.map { it.id }.toSet()
        assertTrue("Should keep notification 1", remainingIds.contains(notification1.id))
        assertTrue("Should keep notification 2", remainingIds.contains(notification2.id))
    }
}
