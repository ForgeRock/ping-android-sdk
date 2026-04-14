/*
 * Copyright (c) 2025-2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.pingidentity.mfa.push.PushCredential
import com.pingidentity.mfa.push.PushDeviceToken
import com.pingidentity.mfa.push.PushNotification
import com.pingidentity.mfa.push.PushPlatform
import com.pingidentity.mfa.push.PushType
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.util.Date
import java.util.UUID

/**
 * Instrumented test for SharedPrefsPushStorage to verify its operations in a real Android environment.
 *
 * This test focuses on testing the SharedPreferences implementation of the PushStorage interface,
 * including credential CRUD operations, notification management, device token management, and error handling.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
@OptIn(ExperimentalCoroutinesApi::class)
class SharedPrefsPushStorageTest {

    private lateinit var appContext: Context
    private lateinit var storage: SharedPrefsPushStorage
    private val testPrefName = "test_push_prefs_${System.currentTimeMillis()}"

    @Before
    fun setup() = runTest {
        // Get the application context
        appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // Create storage instance with a unique test preferences name
        storage = SharedPrefsPushStorage(appContext, testPrefName)

        // Initialize the storage
        storage.initialize()

        // Clean up any existing test data
        cleanupTestData()
    }

    @After
    fun tearDown() = runTest {
        // Clean up after tests
        cleanupTestData()
        storage.close()
    }

    /**
     * Helper method to clean up any test data
     */
    private suspend fun cleanupTestData() {
        try {
            // Clear all data
            storage.clear()
        } catch (e: Exception) {
            println("Warning: Failed to clean up test data: ${e.message}")
        }
    }

    /**
     * Helper method to create a test Push credential with unique ID
     */
    private fun createTestPushCredential(
        id: String = UUID.randomUUID().toString(),
        issuer: String = "Test Issuer",
        accountName: String = "test@example.com"
    ): PushCredential {
        return PushCredential(
            id = id,
            userId = "test-user",
            resourceId = "test-resource",
            issuer = issuer,
            displayIssuer = issuer,
            accountName = accountName,
            displayAccountName = accountName,
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
     * Helper method to create a test Push notification with unique ID
     */
    private fun createTestPushNotification(
        id: String = UUID.randomUUID().toString(),
        credentialId: String,
        messageText: String = "Test notification message"
    ): PushNotification {
        return PushNotification(
            id = id,
            credentialId = credentialId,
            messageId = "msg-${UUID.randomUUID()}",
            messageText = messageText,
            customPayload = null,
            challenge = "test-challenge",
            numbersChallenge = "1234",
            loadBalancer = null,
            contextInfo = null,
            pushType = PushType.DEFAULT,
            ttl = 300,
            pending = true,
            approved = false,
            createdAt = Date(),
            sentAt = Date(),
            respondedAt = null,
            additionalData = null
        )
    }

    /**
     * Helper method to create a test Push device token
     */
    private fun createTestPushDeviceToken(
        id: String = UUID.randomUUID().toString(),
        tokenId: String = "fcm-test-token-${System.currentTimeMillis()}"
    ): PushDeviceToken {
        return PushDeviceToken(
            id = id,
            tokenId = tokenId
        )
    }

    /**
     * Helper method to create a notification with a specific age in days
     */
    private fun createNotificationWithAge(credentialId: String, ageInDays: Int): PushNotification {
        val pastDate = Date(System.currentTimeMillis() - (ageInDays * 24 * 60 * 60 * 1000L))

        return PushNotification(
            id = UUID.randomUUID().toString(),
            credentialId = credentialId,
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
            sentAt = pastDate,
            respondedAt = null,
            additionalData = null
        )
    }

    /**
     * Test initialization
     */
    @Test
    fun testInitialization() = runTest {
        // Create a new storage instance
        val newPrefName = "init_test_prefs_${System.currentTimeMillis()}"
        val newStorage = SharedPrefsPushStorage(appContext, newPrefName)

        try {
            // Initialize the storage
            newStorage.initialize()

            // Try a basic operation to verify initialization
            val testCred = createTestPushCredential()
            newStorage.storePushCredential(testCred)

            // Retrieve the credential to verify it was stored correctly
            val retrievedCred = newStorage.retrievePushCredential(testCred.id)

            // Verify
            assertNotNull("Retrieved credential should not be null", retrievedCred)
            assertEquals("Retrieved credential ID should match original", testCred.id, retrievedCred?.id)

            // Clean up
            newStorage.clearPushCredentials()
        } finally {
            // Close the test storage
            newStorage.close()
        }
    }

    /**
     * Test storing and retrieving a credential
     */
    @Test
    fun testStoreAndRetrievePushCredential() = runTest {
        // Create a test credential
        val testCred = createTestPushCredential()

        // Store the credential
        storage.storePushCredential(testCred)

        // Retrieve the credential
        val retrievedCred = storage.retrievePushCredential(testCred.id)

        // Verify
        assertNotNull("Retrieved credential should not be null", retrievedCred)
        assertEquals("ID should match", testCred.id, retrievedCred?.id)
        assertEquals("Issuer should match", testCred.issuer, retrievedCred?.issuer)
        assertEquals("Account name should match", testCred.accountName, retrievedCred?.accountName)
    }

    /**
     * Test retrieving all credentials
     */
    @Test
    fun testGetAllPushCredentials() = runTest {
        // Create and store multiple test credentials
        val credential1 = createTestPushCredential(issuer = "Issuer1")
        val credential2 = createTestPushCredential(issuer = "Issuer2")
        val credential3 = createTestPushCredential(issuer = "Issuer3")

        storage.storePushCredential(credential1)
        storage.storePushCredential(credential2)
        storage.storePushCredential(credential3)

        // Retrieve all credentials
        val allCredentials = storage.getAllPushCredentials()

        // Verify
        assertEquals("Should retrieve 3 credentials", 3, allCredentials.size)
        assertTrue("Should contain credential 1",
            allCredentials.any { it.id == credential1.id && it.issuer == "Issuer1" })
        assertTrue("Should contain credential 2",
            allCredentials.any { it.id == credential2.id && it.issuer == "Issuer2" })
        assertTrue("Should contain credential 3",
            allCredentials.any { it.id == credential3.id && it.issuer == "Issuer3" })
    }

    /**
     * Test removing a credential
     */
    @Test
    fun testRemovePushCredential() = runTest {
        // Create and store a test credential
        val testCred = createTestPushCredential()
        storage.storePushCredential(testCred)

        // Verify credential exists
        val retrievedBefore = storage.retrievePushCredential(testCred.id)
        assertNotNull("Credential should exist before removal", retrievedBefore)

        // Remove the credential
        val removed = storage.removePushCredential(testCred.id)

        // Verify removal was successful
        assertTrue("Removal should be successful", removed)

        // Verify credential no longer exists
        val retrievedAfter = storage.retrievePushCredential(testCred.id)
        assertNull("Credential should not exist after removal", retrievedAfter)
    }

    /**
     * Test storing and retrieving a notification
     */
    @Test
    fun testStoreAndRetrievePushNotification() = runTest {
        // Create and store a test credential
        val testCred = createTestPushCredential()
        storage.storePushCredential(testCred)

        // Create and store a test notification
        val testNotification = createTestPushNotification(credentialId = testCred.id)
        storage.storePushNotification(testNotification)

        // Retrieve the notification
        val retrievedNotification = storage.retrievePushNotification(testNotification.id)

        // Verify
        assertNotNull("Retrieved notification should not be null", retrievedNotification)
        assertEquals("ID should match", testNotification.id, retrievedNotification?.id)
        assertEquals("Credential ID should match", testCred.id, retrievedNotification?.credentialId)
        assertEquals("Message text should match", testNotification.messageText, retrievedNotification?.messageText)
        assertNotNull("sentAt should not be null", retrievedNotification?.sentAt)
    }

    /**
     * Test retrieving pending notifications
     */
    @Test
    fun testGetPendingPushNotifications() = runTest {
        // Create and store a test credential
        val testCred = createTestPushCredential()
        storage.storePushCredential(testCred)

        // Create and store notifications with different pending states
        val pendingNotification1 = createTestPushNotification(credentialId = testCred.id)
        val pendingNotification2 = createTestPushNotification(credentialId = testCred.id)

        // Create a non-pending notification
        val respondedNotification = createTestPushNotification(credentialId = testCred.id)
            .copy(pending = false, approved = true, respondedAt = Date())

        storage.storePushNotification(pendingNotification1)
        storage.storePushNotification(pendingNotification2)
        storage.storePushNotification(respondedNotification)

        // Get pending notifications
        val pendingNotifications = storage.getPendingPushNotifications()

        // Verify
        assertEquals("Should retrieve 2 pending notifications", 2, pendingNotifications.size)
        assertTrue("Should contain pending notification 1",
            pendingNotifications.any { it.id == pendingNotification1.id })
        assertTrue("Should contain pending notification 2",
            pendingNotifications.any { it.id == pendingNotification2.id })
        assertFalse("Should not contain responded notification",
            pendingNotifications.any { it.id == respondedNotification.id })
    }

    /**
     * Test removing notifications for a specific credential
     */
    @Test
    fun testRemovePushNotificationsForCredential() = runTest {
        // Create and store two test credentials
        val credential1 = createTestPushCredential()
        val credential2 = createTestPushCredential()
        storage.storePushCredential(credential1)
        storage.storePushCredential(credential2)

        // Create and store notifications for each credential
        val notificationForCred1A = createTestPushNotification(credentialId = credential1.id)
        val notificationForCred1B = createTestPushNotification(credentialId = credential1.id)
        val notificationForCred2 = createTestPushNotification(credentialId = credential2.id)

        storage.storePushNotification(notificationForCred1A)
        storage.storePushNotification(notificationForCred1B)
        storage.storePushNotification(notificationForCred2)

        // Remove notifications for credential1
        val numRemoved = storage.removePushNotificationsForCredential(credential1.id)

        // Verify
        assertEquals("Should have removed 2 notifications", 2, numRemoved)

        // Verify only the notification for credential2 remains
        val remainingNotifications = storage.getAllPushNotifications()
        assertEquals("Should have 1 notification remaining", 1, remainingNotifications.size)
        assertEquals("Remaining notification should be for credential2",
            credential2.id, remainingNotifications[0].credentialId)
    }

    /**
     * Test storing and retrieving the current push device token
     */
    @Test
    fun testStoreAndGetCurrentPushDeviceToken() = runTest {
        // Ensure no token exists initially
        val initialToken = storage.getCurrentPushDeviceToken()
        assertNull("Should not have a current token initially", initialToken)

        // Create and store a test token
        val testToken = createTestPushDeviceToken(tokenId = "test-device-token")
        storage.storePushDeviceToken(testToken)

        // Retrieve the current token
        val currentToken = storage.getCurrentPushDeviceToken()

        // Verify
        assertNotNull("Current token should not be null", currentToken)
        assertEquals("Token ID should match", testToken.id, currentToken?.id)
        assertEquals("Token value should match", "test-device-token", currentToken?.tokenId)
    }

    /**
     * Test storing multiple tokens and verifying that the most recent one is returned as current
     */
    @Test
    fun testMultipleTokensGetsCurrent() = runTest {
        // Create and store multiple tokens
        val oldToken = createTestPushDeviceToken(tokenId = "old-token")
        val newerToken = createTestPushDeviceToken(tokenId = "newer-token")
        val newestToken = createTestPushDeviceToken(tokenId = "newest-token")

        // Store tokens in sequence
        storage.storePushDeviceToken(oldToken)
        storage.storePushDeviceToken(newerToken)
        storage.storePushDeviceToken(newestToken)

        // Get the current token
        val currentToken = storage.getCurrentPushDeviceToken()

        // Verify it's the newest one
        assertNotNull("Current token should not be null", currentToken)
        assertEquals("Current token should be the newest one", newestToken.id, currentToken?.id)
        assertEquals("Token value should match newest", "newest-token", currentToken?.tokenId)
    }

    /**
     * Test retrieving all stored push device tokens
     */
    @Test
    fun testGetAllPushDeviceTokens() = runTest {
        // Create and store multiple tokens
        val token1 = createTestPushDeviceToken(tokenId = "token-1")
        val token2 = createTestPushDeviceToken(tokenId = "token-2")
        val token3 = createTestPushDeviceToken(tokenId = "token-3")

        storage.storePushDeviceToken(token1)
        storage.storePushDeviceToken(token2)
        storage.storePushDeviceToken(token3)

        // Retrieve all tokens
        val allTokens = storage.getAllPushDeviceTokens()

        // Verify
        assertEquals("Should retrieve 3 tokens", 3, allTokens.size)
        assertTrue("Should contain token 1",
            allTokens.any { it.id == token1.id && it.tokenId == "token-1" })
        assertTrue("Should contain token 2",
            allTokens.any { it.id == token2.id && it.tokenId == "token-2" })
        assertTrue("Should contain token 3",
            allTokens.any { it.id == token3.id && it.tokenId == "token-3" })
    }

    /**
     * Test clearing all push device tokens
     */
    @Test
    fun testClearPushDeviceTokens() = runTest {
        // Create and store tokens
        val token1 = createTestPushDeviceToken()
        val token2 = createTestPushDeviceToken()

        storage.storePushDeviceToken(token1)
        storage.storePushDeviceToken(token2)

        // Verify tokens exist
        val beforeClear = storage.getAllPushDeviceTokens()
        assertEquals("Should have 2 tokens before clearing", 2, beforeClear.size)

        // Clear all tokens
        storage.clearPushDeviceTokens()

        // Verify all tokens were removed
        val afterClear = storage.getAllPushDeviceTokens()
        assertEquals("Should have 0 tokens after clearing", 0, afterClear.size)

        // Verify current token is null
        val currentToken = storage.getCurrentPushDeviceToken()
        assertNull("Current token should be null after clearing", currentToken)
    }

    /**
     * Test clearing the storage entirely
     */
    @Test
    fun testClearStorage() = runTest {
        // Create and store a credential, notification, and token
        val credential = createTestPushCredential()
        storage.storePushCredential(credential)

        val notification = createTestPushNotification(credentialId = credential.id)
        storage.storePushNotification(notification)

        val token = createTestPushDeviceToken()
        storage.storePushDeviceToken(token)

        // Verify all items were stored
        assertEquals("Should have 1 credential before clear", 1, storage.getAllPushCredentials().size)
        assertEquals("Should have 1 notification before clear", 1, storage.getAllPushNotifications().size)
        assertEquals("Should have 1 token before clear", 1, storage.getAllPushDeviceTokens().size)

        // Clear the entire storage
        storage.clear()

        // Verify everything was cleared
        assertEquals("Should have 0 credentials after clear", 0, storage.getAllPushCredentials().size)
        assertEquals("Should have 0 notifications after clear", 0, storage.getAllPushNotifications().size)
        assertEquals("Should have 0 tokens after clear", 0, storage.getAllPushDeviceTokens().size)
        assertNull("Current token should be null after clear", storage.getCurrentPushDeviceToken())
    }

    /**
     * Test persistence of data across storage instances
     */
    @Test
    fun testPersistenceAcrossInstances() = runTest {
        // Store data in current instance
        val credential = createTestPushCredential()
        storage.storePushCredential(credential)

        val token = createTestPushDeviceToken(tokenId = "persistent-token")
        storage.storePushDeviceToken(token)

        // Close current instance
        storage.close()

        // Create a new instance with the same preferences name
        val newStorage = SharedPrefsPushStorage(appContext, testPrefName)
        try {
            // Initialize the new instance
            newStorage.initialize()

            // Retrieve data from the new instance
            val retrievedCredential = newStorage.retrievePushCredential(credential.id)
            val retrievedToken = newStorage.getCurrentPushDeviceToken()

            // Verify data persisted
            assertNotNull("Credential should persist across instances", retrievedCredential)
            assertEquals("Credential ID should match", credential.id, retrievedCredential?.id)

            assertNotNull("Token should persist across instances", retrievedToken)
            assertEquals("Token ID should match", token.id, retrievedToken?.id)
            assertEquals("Token value should match", "persistent-token", retrievedToken?.tokenId)
        } finally {
            // Clean up
            newStorage.clear()
            newStorage.close()

            // Re-initialize our test storage for other tests
            storage = SharedPrefsPushStorage(appContext, testPrefName)
            storage.initialize()
        }
    }

    /**
     * Test that notifications can be retrieved by message ID in SharedPreferences storage
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testGetNotificationByMessageId() = runTest {
        // Create and store a test credential
        val testCredential = createTestPushCredential()
        storage.storePushCredential(testCredential)

        // Create a notification with a specific message ID
        val specificMessageId = "shared-prefs-message-id-123"
        val testNotification = PushNotification(
            id = UUID.randomUUID().toString(),
            credentialId = testCredential.id,
            messageId = specificMessageId,
            messageText = "Test notification message",
            customPayload = null,
            challenge = "test-challenge",
            numbersChallenge = "1234",
            loadBalancer = null,
            contextInfo = null,
            pushType = PushType.DEFAULT,
            ttl = 300,
            pending = true,
            approved = false,
            createdAt = Date(),
            respondedAt = null,
            additionalData = null
        )

        // Store the notification
        storage.storePushNotification(testNotification)

        // Create and store another notification with a different message ID
        val anotherNotification = createTestPushNotification(credentialId = testCredential.id)
        storage.storePushNotification(anotherNotification)

        // Retrieve the notification by message ID
        val retrievedNotification = storage.getNotificationByMessageId(specificMessageId)

        // Verify the notification was retrieved correctly
        assertNotNull("Retrieved notification should not be null", retrievedNotification)
        assertEquals("Retrieved notification ID should match", testNotification.id, retrievedNotification?.id)
        assertEquals("Retrieved notification message ID should match", specificMessageId, retrievedNotification?.messageId)

        // Try retrieving with a non-existent message ID
        val nonExistentNotification = storage.getNotificationByMessageId("non-existent-message-id")

        // Verify null is returned for non-existent message ID
        assertNull("No notification should be found for non-existent message ID", nonExistentNotification)

        // Try retrieving with a blank message ID
        val blankMessageIdNotification = storage.getNotificationByMessageId("")

        // Verify null is returned for blank message ID
        assertNull("No notification should be found for blank message ID", blankMessageIdNotification)
    }

    /**
     * Test counting push notifications
     */
    @Test
    fun testCountPushNotifications() = runTest {
        // Create and store two test credentials
        val credential1 = createTestPushCredential()
        val credential2 = createTestPushCredential()
        storage.storePushCredential(credential1)
        storage.storePushCredential(credential2)

        // Create and store notifications for each credential
        val notification1ForCred1 = createTestPushNotification(credentialId = credential1.id)
        val notification2ForCred1 = createTestPushNotification(credentialId = credential1.id)
        val notification1ForCred2 = createTestPushNotification(credentialId = credential2.id)

        storage.storePushNotification(notification1ForCred1)
        storage.storePushNotification(notification2ForCred1)
        storage.storePushNotification(notification1ForCred2)

        // Count all notifications
        val totalCount = storage.countPushNotifications()
        assertEquals("Should count 3 notifications total", 3, totalCount)

        // Count notifications for credential1
        val cred1Count = storage.countPushNotifications(credential1.id)
        assertEquals("Should count 2 notifications for credential1", 2, cred1Count)

        // Count notifications for credential2
        val cred2Count = storage.countPushNotifications(credential2.id)
        assertEquals("Should count 1 notification for credential2", 1, cred2Count)
    }

    /**
     * Test retrieving oldest push notifications
     */
    @Test
    fun testGetOldestPushNotifications() = runTest {
        // Create and store a test credential
        val testCred = createTestPushCredential()
        storage.storePushCredential(testCred)

        // Create notifications with different ages
        val oldestNotification = createNotificationWithAge(testCred.id, 30)
        val middleNotification = createNotificationWithAge(testCred.id, 15)
        val newestNotification = createNotificationWithAge(testCred.id, 5)

        // Store notifications in random order to ensure sorting works
        storage.storePushNotification(middleNotification)
        storage.storePushNotification(newestNotification)
        storage.storePushNotification(oldestNotification)

        // Retrieve the oldest 2 notifications
        val oldestTwo = storage.getOldestPushNotifications(2)

        // Verify we got the expected notifications in the correct order
        assertEquals("Should retrieve 2 notifications", 2, oldestTwo.size)
        assertEquals("First notification should be the oldest", oldestNotification.id, oldestTwo[0].id)
        assertEquals("Second notification should be the middle one", middleNotification.id, oldestTwo[1].id)
    }

    /**
     * Test purging push notifications by age
     */
    @Test
    fun testPurgePushNotificationsByAge() = runTest {
        // Create and store a test credential
        val testCred = createTestPushCredential()
        storage.storePushCredential(testCred)

        // Create notifications with different ages
        val veryOldNotification = createNotificationWithAge(testCred.id, 40) // Will be purged
        val oldNotification = createNotificationWithAge(testCred.id, 25)     // Will be purged
        val recentNotification = createNotificationWithAge(testCred.id, 10)  // Will be kept
        val newestNotification = createNotificationWithAge(testCred.id, 5)   // Will be kept

        // Store all notifications
        storage.storePushNotification(veryOldNotification)
        storage.storePushNotification(oldNotification)
        storage.storePushNotification(recentNotification)
        storage.storePushNotification(newestNotification)

        // Verify we have 4 notifications
        val beforeCount = storage.countPushNotifications()
        assertEquals("Should have 4 notifications initially", 4, beforeCount)

        // Purge notifications older than 20 days
        val purgedCount = storage.purgePushNotificationsByAge(20)
        assertEquals("Should have purged 2 notifications", 2, purgedCount)

        // Verify we have 2 notifications left
        val afterCount = storage.countPushNotifications()
        assertEquals("Should have 2 notifications after purge", 2, afterCount)

        // Retrieve remaining notifications and verify they're the correct ones
        val remaining = storage.getAllPushNotifications()
        val remainingIds = remaining.map { it.id }.toSet()
        assertTrue("Should have kept the recent notification", remainingIds.contains(recentNotification.id))
        assertTrue("Should have kept the newest notification", remainingIds.contains(newestNotification.id))
        assertFalse("Should have removed the very old notification", remainingIds.contains(veryOldNotification.id))
        assertFalse("Should have removed the old notification", remainingIds.contains(oldNotification.id))
    }

    /**
     * Test purging push notifications by count
     */
    @Test
    fun testPurgePushNotificationsByCount() = runTest {
        // Create and store a test credential
        val testCred = createTestPushCredential()
        storage.storePushCredential(testCred)

        // Create notifications with different ages
        val oldestNotification = createNotificationWithAge(testCred.id, 30) // Will be purged
        val olderNotification = createNotificationWithAge(testCred.id, 20)  // Will be purged
        val recentNotification = createNotificationWithAge(testCred.id, 10) // Will be kept
        val newestNotification = createNotificationWithAge(testCred.id, 5)  // Will be kept

        // Store all notifications
        storage.storePushNotification(oldestNotification)
        storage.storePushNotification(olderNotification)
        storage.storePushNotification(recentNotification)
        storage.storePushNotification(newestNotification)

        // Verify we have 4 notifications
        val beforeCount = storage.countPushNotifications()
        assertEquals("Should have 4 notifications initially", 4, beforeCount)

        // Keep only the 2 newest notifications
        val purgedCount = storage.purgePushNotificationsByCount(2)
        assertEquals("Should have purged 2 notifications", 2, purgedCount)

        // Verify we have 2 notifications left
        val afterCount = storage.countPushNotifications()
        assertEquals("Should have 2 notifications after purge", 2, afterCount)

        // Retrieve remaining notifications and verify they're the correct ones
        val remaining = storage.getAllPushNotifications().sortedBy { it.createdAt }
        assertEquals("Should have 2 notifications remaining", 2, remaining.size)

        // Make sure the remaining notifications are the newest ones
        val remainingIds = remaining.map { it.id }.toSet()
        assertTrue("Should have kept the recent notification", remainingIds.contains(recentNotification.id))
        assertTrue("Should have kept the newest notification", remainingIds.contains(newestNotification.id))
    }

    /**
     * Test purging push notifications for specific credential
     */
    @Test
    fun testPurgePushNotificationsForSpecificCredential() = runTest {
        // Create and store two test credentials
        val credential1 = createTestPushCredential()
        val credential2 = createTestPushCredential()
        storage.storePushCredential(credential1)
        storage.storePushCredential(credential2)

        // Create notifications for credential 1
        val oldNotificationCred1 = createNotificationWithAge(credential1.id, 25) // Will be purged
        val newNotificationCred1 = createNotificationWithAge(credential1.id, 5)  // Will be kept

        // Create notifications for credential 2
        val oldNotificationCred2 = createNotificationWithAge(credential2.id, 25) // Will NOT be purged
        val newNotificationCred2 = createNotificationWithAge(credential2.id, 5)  // Will be kept

        // Store all notifications
        storage.storePushNotification(oldNotificationCred1)
        storage.storePushNotification(newNotificationCred1)
        storage.storePushNotification(oldNotificationCred2)
        storage.storePushNotification(newNotificationCred2)

        // Verify we have 4 notifications
        val beforeCount = storage.countPushNotifications()
        assertEquals("Should have 4 notifications initially", 4, beforeCount)

        // Purge notifications older than 20 days for credential1 only
        val purgedCount = storage.purgePushNotificationsByAge(20, credential1.id)
        assertEquals("Should have purged 1 notification", 1, purgedCount)

        // Verify we have 3 notifications left
        val afterCount = storage.countPushNotifications()
        assertEquals("Should have 3 notifications after purge", 3, afterCount)

        // Verify credential1 has 1 notification left
        val cred1Count = storage.countPushNotifications(credential1.id)
        assertEquals("Credential1 should have 1 notification left", 1, cred1Count)

        // Verify credential2 still has 2 notifications
        val cred2Count = storage.countPushNotifications(credential2.id)
        assertEquals("Credential2 should still have 2 notifications", 2, cred2Count)
    }

    /**
     * Test getting a credential by issuer and account name
     */
    @Test
    fun testGetCredentialByIssuerAndAccount() = runTest {
        // Create and store multiple test credentials
        val credential1 = createTestPushCredential(issuer = "Issuer1", accountName = "user1@example.com")
        val credential2 = createTestPushCredential(issuer = "Issuer2", accountName = "user2@example.com")
        val credential3 = createTestPushCredential(issuer = "Issuer1", accountName = "user3@example.com")

        storage.storePushCredential(credential1)
        storage.storePushCredential(credential2)
        storage.storePushCredential(credential3)

        // Test retrieving by exact issuer and account name
        val retrieved1 = storage.getCredentialByIssuerAndAccount("Issuer1", "user1@example.com")
        assertNotNull("Should find credential1", retrieved1)
        assertEquals("Should return correct credential", credential1.id, retrieved1?.id)

        // Test retrieving different combinations
        val retrieved2 = storage.getCredentialByIssuerAndAccount("Issuer2", "user2@example.com")
        assertNotNull("Should find credential2", retrieved2)
        assertEquals("Should return correct credential", credential2.id, retrieved2?.id)

        // Test non-existent combination
        val retrievedNonExistent = storage.getCredentialByIssuerAndAccount("NonExistent", "none@example.com")
        assertNull("Should return null for non-existent combination", retrievedNonExistent)

        // Test case sensitivity
        val retrievedCaseMismatch = storage.getCredentialByIssuerAndAccount("issuer1", "user1@example.com")
        assertNull("Should be case-sensitive for issuer", retrievedCaseMismatch)

        val retrievedAccountCaseMismatch = storage.getCredentialByIssuerAndAccount("Issuer1", "USER1@EXAMPLE.COM")
        assertNull("Should be case-sensitive for account name", retrievedAccountCaseMismatch)
    }
}
