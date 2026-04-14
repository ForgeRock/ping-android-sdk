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
import com.pingidentity.android.ContextProvider
import com.pingidentity.mfa.push.PushCredential
import com.pingidentity.mfa.push.PushDeviceToken
import com.pingidentity.mfa.push.PushNotification
import com.pingidentity.mfa.push.PushPlatform
import com.pingidentity.mfa.push.PushType
import com.pingidentity.storage.sqlite.passphrase.FixedPassphraseProvider
import com.pingidentity.storage.sqlite.passphrase.PassphraseProvider
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
 * Instrumented test specifically for SQLPushStorage to verify its
 * database operations in a real Android environment.
 *
 * This test focuses on directly testing the database layer functionality
 * including table creation, credential CRUD operations, notification
 * management, and error handling.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
@OptIn(ExperimentalCoroutinesApi::class)
class SQLPushStorageTest {

    private lateinit var appContext: Context
    private lateinit var storage: SQLPushStorage
    private val testDbName = "test_push_storage_${System.currentTimeMillis()}.db"
    private val testPassphraseProvider = object : PassphraseProvider {
        override suspend fun getPassphrase() = "test_passphrase"
    }

    @Before
    fun setup() = runTest {
        // Get the application context
        appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // Initialize ContextProvider with the actual app context
        ContextProvider.init(appContext)

        // Create storage instance with a unique test database name and encryption disabled for testing
        storage = SQLPushStorage {
            context = appContext
            databaseName = testDbName
            passphraseProvider = testPassphraseProvider
        }

        // Initialize the storage to ensure tables are created
        storage.initialize()

        // Clean up any existing test data
        cleanupTestData()
    }

    @After
    fun tearDown() = runTest {
        // Ensure storage is closed after each test
        try {
            storage.close()
        } catch (e: Exception) {
            println("Warning: Failed to close storage: ${e.message}")
        }
    }

    /**
     * Helper method to clean up any test data
     */
    private suspend fun cleanupTestData() {
        try {
            // Clear all Push credentials, notifications, and device tokens from the database
            storage.clearPushCredentials()
            storage.clearPushNotifications()
            storage.clearPushDeviceTokens()
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
     * Test that database and tables are created successfully during initialization
     */
    @Test
    fun testDatabaseInitialization() = runTest {
        // Create a new storage instance with a different database name
        val initDbName = "init_test_db_${System.currentTimeMillis()}.db"
        val initStorage = SQLPushStorage {
            context = appContext
            databaseName = initDbName
            passphraseProvider = testPassphraseProvider
        }

        try {
            // Initialize the storage - this should create the database and tables
            initStorage.initialize()

            // If no exception was thrown, the initialization was successful
            // Attempt a basic operation to verify tables were created correctly
            val testCred = createTestPushCredential()

            // Store the credential - this will fail if tables weren't created properly
            initStorage.storePushCredential(testCred)

            // Retrieve the credential to verify it was stored correctly
            val retrievedCred = initStorage.retrievePushCredential(testCred.id)

            // Verify the credential was stored and retrieved correctly
            assertNotNull("Retrieved credential should not be null", retrievedCred)
            assertEquals("Retrieved credential ID should match original", testCred.id, retrievedCred?.id)

            // Clean up
            initStorage.clearPushCredentials()

            // Test passed if we got here without exceptions
            assertTrue("Database initialization successful", true)
        } finally {
            // Ensure the test storage is closed
            initStorage.close()
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

        // Verify the credential was retrieved correctly
        assertNotNull("Retrieved credential should not be null", retrievedCred)
        assertEquals("ID should match", testCred.id, retrievedCred?.id)
        assertEquals("Issuer should match", testCred.issuer, retrievedCred?.issuer)
        assertEquals("Account name should match", testCred.accountName, retrievedCred?.accountName)
        assertEquals("Server endpoint should match", testCred.serverEndpoint, retrievedCred?.serverEndpoint)
        assertEquals("Shared secret should match", testCred.sharedSecret, retrievedCred?.sharedSecret)
        assertEquals("Platform should match", testCred.platform, retrievedCred?.platform)
    }

    /**
     * Test retrieving all credentials
     */
    @Test
    fun testGetAllPushCredentials() = runTest {
        // Ensure we start with an empty database
        val initialCredentials = storage.getAllPushCredentials()
        assertEquals("Database should be empty initially", 0, initialCredentials.size)

        // Create and store multiple test credentials
        val credential1 = createTestPushCredential(issuer = "Issuer1", accountName = "user1@example.com")
        val credential2 = createTestPushCredential(issuer = "Issuer2", accountName = "user2@example.com")
        val credential3 = createTestPushCredential(issuer = "Issuer3", accountName = "user3@example.com")

        // Store all credentials
        storage.storePushCredential(credential1)
        storage.storePushCredential(credential2)
        storage.storePushCredential(credential3)

        // Retrieve all credentials
        val allCredentials = storage.getAllPushCredentials()

        // Verify all credentials were retrieved
        assertEquals("Should retrieve all 3 credentials", 3, allCredentials.size)

        // Verify each credential was retrieved correctly
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
     * Test removing a credential that doesn't exist
     */
    @Test
    fun testRemoveNonExistentPushCredential() = runTest {
        // Attempt to remove a credential with a non-existent ID
        val removed = storage.removePushCredential("non_existent_id")

        // Verify removal was not successful
        assertFalse("Removal of non-existent credential should return false", removed)
    }

    /**
     * Test clearing all credentials
     */
    @Test
    fun testClearPushCredentials() = runTest {
        // Create and store multiple test credentials with unique issuer/accountName combinations
        val credential1 = createTestPushCredential(issuer = "Issuer1", accountName = "user1@example.com")
        val credential2 = createTestPushCredential(issuer = "Issuer2", accountName = "user2@example.com")

        storage.storePushCredential(credential1)
        storage.storePushCredential(credential2)

        // Verify credentials exist
        val beforeClear = storage.getAllPushCredentials()
        assertEquals("Should have 2 credentials before clearing", 2, beforeClear.size)

        // Clear all credentials
        storage.clearPushCredentials()

        // Verify all credentials were removed
        val afterClear = storage.getAllPushCredentials()
        assertEquals("Should have 0 credentials after clearing", 0, afterClear.size)
    }

    /**
     * Test updating a credential
     */
    @Test
    fun testUpdatePushCredential() = runTest {
        // Create and store a test credential
        val originalCred = createTestPushCredential(issuer = "Original Issuer")
        storage.storePushCredential(originalCred)

        // Create an updated version of the credential with the same ID
        val updatedCred = createTestPushCredential(
            id = originalCred.id,
            issuer = "Updated Issuer",
            accountName = "updated@example.com"
        )

        // Store the updated credential (this should overwrite the original)
        storage.storePushCredential(updatedCred)

        // Retrieve the credential
        val retrievedCred = storage.retrievePushCredential(originalCred.id)

        // Verify the credential was updated
        assertNotNull("Retrieved credential should not be null", retrievedCred)
        assertEquals("ID should remain the same", originalCred.id, retrievedCred?.id)
        assertEquals("Issuer should be updated", "Updated Issuer", retrievedCred?.issuer)
        assertEquals("Account name should be updated", "updated@example.com", retrievedCred?.accountName)
    }

    /**
     * Test storing and retrieving a notification
     */
    @Test
    fun testStoreAndRetrievePushNotification() = runTest {
        // Create and store a test credential
        val testCred = createTestPushCredential()
        storage.storePushCredential(testCred)

        // Create and store a test notification for this credential
        val testNotification = createTestPushNotification(credentialId = testCred.id)
        storage.storePushNotification(testNotification)

        // Retrieve the notification
        val retrievedNotification = storage.retrievePushNotification(testNotification.id)

        // Verify the notification was retrieved correctly
        assertNotNull("Retrieved notification should not be null", retrievedNotification)
        assertEquals("Notification ID should match", testNotification.id, retrievedNotification?.id)
        assertEquals("Credential ID should match", testCred.id, retrievedNotification?.credentialId)
        assertEquals("Message text should match", testNotification.messageText, retrievedNotification?.messageText)
        assertEquals("Challenge should match", testNotification.challenge, retrievedNotification?.challenge)
        assertEquals("Numbers challenge should match", testNotification.numbersChallenge, retrievedNotification?.numbersChallenge)
        assertEquals("Push type should match", testNotification.pushType, retrievedNotification?.pushType)
        assertEquals("Pending status should match", testNotification.pending, retrievedNotification?.pending)
        assertEquals("Approved status should match", testNotification.approved, retrievedNotification?.approved)
    }

    /**
     * Test retrieving all notifications
     */
    @Test
    fun testGetAllPushNotifications() = runTest {
        // Create and store a test credential
        val testCred = createTestPushCredential()
        storage.storePushCredential(testCred)

        // Create and store multiple test notifications for this credential
        val notification1 = createTestPushNotification(credentialId = testCred.id, messageText = "Message 1")
        val notification2 = createTestPushNotification(credentialId = testCred.id, messageText = "Message 2")
        val notification3 = createTestPushNotification(credentialId = testCred.id, messageText = "Message 3")

        storage.storePushNotification(notification1)
        storage.storePushNotification(notification2)
        storage.storePushNotification(notification3)

        // Retrieve all notifications
        val allNotifications = storage.getAllPushNotifications()

        // Verify all notifications were retrieved
        assertEquals("Should retrieve all 3 notifications", 3, allNotifications.size)

        // Verify each notification was retrieved correctly
        assertTrue("Should contain notification 1",
            allNotifications.any { it.id == notification1.id && it.messageText == "Message 1" })
        assertTrue("Should contain notification 2",
            allNotifications.any { it.id == notification2.id && it.messageText == "Message 2" })
        assertTrue("Should contain notification 3",
            allNotifications.any { it.id == notification3.id && it.messageText == "Message 3" })
    }

    /**
     * Test retrieving pending notifications
     */
    @Test
    fun testGetPendingPushNotifications() = runTest {
        // Create and store a test credential
        val testCred = createTestPushCredential()
        storage.storePushCredential(testCred)

        // Create and store multiple test notifications with different pending states
        val pendingNotification1 = createTestPushNotification(credentialId = testCred.id, messageText = "Pending 1")
        val pendingNotification2 = createTestPushNotification(credentialId = testCred.id, messageText = "Pending 2")

        // Create a notification that is not pending (has been responded to)
        val respondedNotification = createTestPushNotification(credentialId = testCred.id, messageText = "Responded")
            .copy(pending = false, approved = true, respondedAt = Date())

        storage.storePushNotification(pendingNotification1)
        storage.storePushNotification(pendingNotification2)
        storage.storePushNotification(respondedNotification)

        // Retrieve all pending notifications
        val pendingNotifications = storage.getPendingPushNotifications()

        // Verify only pending notifications were retrieved
        assertEquals("Should retrieve only 2 pending notifications", 2, pendingNotifications.size)

        // Verify the correct notifications were retrieved
        assertTrue("Should contain pending notification 1",
            pendingNotifications.any { it.id == pendingNotification1.id && it.messageText == "Pending 1" })
        assertTrue("Should contain pending notification 2",
            pendingNotifications.any { it.id == pendingNotification2.id && it.messageText == "Pending 2" })
        assertFalse("Should not contain responded notification",
            pendingNotifications.any { it.id == respondedNotification.id })
    }

    /**
     * Test removing a notification
     */
    @Test
    fun testRemovePushNotification() = runTest {
        // Create and store a test credential
        val testCred = createTestPushCredential()
        storage.storePushCredential(testCred)

        // Create and store a test notification
        val testNotification = createTestPushNotification(credentialId = testCred.id)
        storage.storePushNotification(testNotification)

        // Verify notification exists
        val retrievedBefore = storage.retrievePushNotification(testNotification.id)
        assertNotNull("Notification should exist before removal", retrievedBefore)

        // Remove the notification
        val removed = storage.removePushNotification(testNotification.id)

        // Verify removal was successful
        assertTrue("Removal should be successful", removed)

        // Verify notification no longer exists
        val retrievedAfter = storage.retrievePushNotification(testNotification.id)
        assertNull("Notification should not exist after removal", retrievedAfter)
    }

    /**
     * Test updating a notification
     */
    @Test
    fun testUpdatePushNotification() = runTest {
        // Create and store a test credential
        val testCred = createTestPushCredential()
        storage.storePushCredential(testCred)

        // Create and store a test notification
        val originalNotification = createTestPushNotification(
            credentialId = testCred.id,
            messageText = "Original message"
        )
        storage.storePushNotification(originalNotification)

        // Create an updated version of the notification (approve it)
        val updatedNotification = originalNotification.copy(
            messageText = "Updated message",
            pending = false,
            approved = true,
            respondedAt = Date()
        )

        // Store the updated notification
        storage.updatePushNotification(updatedNotification)

        // Retrieve the notification
        val retrievedNotification = storage.retrievePushNotification(originalNotification.id)

        // Verify the notification was updated
        assertNotNull("Retrieved notification should not be null", retrievedNotification)
        assertEquals("ID should remain the same", originalNotification.id, retrievedNotification?.id)
        assertEquals("Message text should be updated", "Updated message", retrievedNotification?.messageText)
        assertFalse("Notification should no longer be pending", retrievedNotification?.pending ?: true)
        assertTrue("Notification should be approved", retrievedNotification?.approved ?: false)
        assertNotNull("Responded date should be set", retrievedNotification?.respondedAt)
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

        // Create and store multiple test notifications for each credential
        val notificationForCred1A = createTestPushNotification(credentialId = credential1.id)
        val notificationForCred1B = createTestPushNotification(credentialId = credential1.id)
        val notificationForCred2 = createTestPushNotification(credentialId = credential2.id)

        storage.storePushNotification(notificationForCred1A)
        storage.storePushNotification(notificationForCred1B)
        storage.storePushNotification(notificationForCred2)

        // Verify all notifications exist
        val allNotificationsBefore = storage.getAllPushNotifications()
        assertEquals("Should have 3 notifications total", 3, allNotificationsBefore.size)

        // Remove all notifications for credential1
        val numRemoved = storage.removePushNotificationsForCredential(credential1.id)

        // Verify correct number of notifications were removed
        assertEquals("Should have removed 2 notifications", 2, numRemoved)

        // Verify only the notifications for credential2 remain
        val allNotificationsAfter = storage.getAllPushNotifications()
        assertEquals("Should have 1 notification remaining", 1, allNotificationsAfter.size)
        assertEquals("Remaining notification should be for credential2",
            credential2.id, allNotificationsAfter[0].credentialId)
    }

    /**
     * Test clearing all notifications
     */
    @Test
    fun testClearPushNotifications() = runTest {
        // Create and store a test credential
        val testCred = createTestPushCredential()
        storage.storePushCredential(testCred)

        // Create and store multiple test notifications
        val notification1 = createTestPushNotification(credentialId = testCred.id)
        val notification2 = createTestPushNotification(credentialId = testCred.id)

        storage.storePushNotification(notification1)
        storage.storePushNotification(notification2)

        // Verify notifications exist
        val beforeClear = storage.getAllPushNotifications()
        assertEquals("Should have 2 notifications before clearing", 2, beforeClear.size)

        // Clear all notifications
        storage.clearPushNotifications()

        // Verify all notifications were removed
        val afterClear = storage.getAllPushNotifications()
        assertEquals("Should have 0 notifications after clearing", 0, afterClear.size)

        // Verify credential still exists
        val credential = storage.retrievePushCredential(testCred.id)
        assertNotNull("Credential should still exist after clearing notifications", credential)
    }

    /**
     * Test reopening the database works correctly
     */
    @Test
    fun testReopenDatabase() = runTest {
        // Create and store a test credential
        val testCred = createTestPushCredential()
        storage.storePushCredential(testCred)

        // Create and store a test notification
        val testNotification = createTestPushNotification(credentialId = testCred.id)
        storage.storePushNotification(testNotification)

        // Close the storage
        storage.close()

        // Create a new storage instance with the same database name
        val reopenedStorage = SQLPushStorage {
            context = appContext
            databaseName = testDbName
            passphraseProvider = testPassphraseProvider
        }

        try {
            // Initialize the storage
            reopenedStorage.initialize()

            // Retrieve the credential from the reopened database
            val retrievedCred = reopenedStorage.retrievePushCredential(testCred.id)

            // Verify the credential was retrieved correctly
            assertNotNull("Retrieved credential should not be null after reopening", retrievedCred)
            assertEquals("ID should match after reopening", testCred.id, retrievedCred?.id)
            assertEquals("Issuer should match after reopening", testCred.issuer, retrievedCred?.issuer)

            // Retrieve the notification from the reopened database
            val retrievedNotification = reopenedStorage.retrievePushNotification(testNotification.id)

            // Verify the notification was retrieved correctly
            assertNotNull("Retrieved notification should not be null after reopening", retrievedNotification)
            assertEquals("Notification ID should match after reopening", testNotification.id, retrievedNotification?.id)
        } finally {
            // Ensure the reopened storage is closed
            reopenedStorage.close()
        }
    }

    /**
     * Test storage builder pattern
     */
    @Test
    fun testStorageBuilder() = runTest {
        // Create a new storage instance with the builder
        val builderStorage = SQLPushStorage {
            context = appContext
            databaseName = "builder_test_db_${System.currentTimeMillis()}.db"
            initialPassphrase = "test-secret-key"
            passphraseProvider = testPassphraseProvider
        }

        try {
            // Initialize and verify
            builderStorage.initialize()
            assertTrue("Builder-based storage initialization successful", true)

            // Store a credential to verify functionality
            val testCred = createTestPushCredential()
            builderStorage.storePushCredential(testCred)

            // Retrieve and verify
            val retrievedCred = builderStorage.retrievePushCredential(testCred.id)
            assertNotNull("Retrieved credential should not be null", retrievedCred)
            assertEquals("ID should match", testCred.id, retrievedCred?.id)
        } finally {
            try {
                builderStorage.clearPushCredentials()
                builderStorage.close()
            } catch (e: Exception) {
                println("Warning: Failed to clean up builder storage: ${e.message}")
            }
        }
    }

    /**
     * Test creating storage with a custom passphrase
     */
    @Test
    fun testStorageWithCustomPassphrase() = runTest {
        // Create a new storage instance with a custom passphrase
        val customPassphrase = "my-custom-passphrase-for-testing"
        val passphraseStorage = SQLPushStorage {
            context = appContext
            databaseName = "passphrase_test_db_${System.currentTimeMillis()}.db"
            // Use a custom fixed passphrase provider instead of the default test provider
            passphraseProvider = FixedPassphraseProvider(customPassphrase)
        }

        try {
            // Initialize and verify
            passphraseStorage.initialize()
            assertTrue("Custom passphrase storage initialization successful", true)

            // Store a credential to verify functionality
            val testCred = createTestPushCredential()
            passphraseStorage.storePushCredential(testCred)

            // Retrieve and verify
            val retrievedCred = passphraseStorage.retrievePushCredential(testCred.id)
            assertNotNull("Retrieved credential should not be null", retrievedCred)
            assertEquals("ID should match", testCred.id, retrievedCred?.id)
        } finally {
            try {
                passphraseStorage.clearPushCredentials()
                passphraseStorage.close()
            } catch (e: Exception) {
                println("Warning: Failed to clean up passphrase storage: ${e.message}")
            }
        }
    }

    /**
     * Test cascade delete of notifications when a credential is removed
     */
    @Test
    fun testCascadeDeleteNotifications() = runTest {
        // Create and store a test credential
        val testCred = createTestPushCredential()
        storage.storePushCredential(testCred)

        // Create and store multiple test notifications
        val notification1 = createTestPushNotification(credentialId = testCred.id)
        val notification2 = createTestPushNotification(credentialId = testCred.id)
        storage.storePushNotification(notification1)
        storage.storePushNotification(notification2)

        // Verify notifications exist
        val notificationsBefore = storage.getAllPushNotifications()
        assertEquals("Should have 2 notifications before removing credential", 2, notificationsBefore.size)

        // Remove the credential (should cascade delete notifications)
        storage.removePushCredential(testCred.id)

        // Verify all related notifications were removed
        val notificationsAfter = storage.getAllPushNotifications()
        assertEquals("Should have 0 notifications after removing credential", 0, notificationsAfter.size)
    }

    /**
     * Test with different push platforms
     */
    @Test
    fun testDifferentPushPlatforms() = runTest {
        // Create credentials with different platforms
        val pingOneCred = createTestPushCredential(issuer = "PingOne Test").copy(
            platform = PushPlatform.PING_ONE.name
        )

        val pingIdCred = createTestPushCredential(issuer = "PingAM Test").copy(
            platform = PushPlatform.PING_AM.name
        )

        // Store both credentials
        storage.storePushCredential(pingOneCred)
        storage.storePushCredential(pingIdCred)

        // Retrieve both credentials
        val retrievedPingOne = storage.retrievePushCredential(pingOneCred.id)
        val retrievedPingId = storage.retrievePushCredential(pingIdCred.id)

        // Verify platforms were preserved
        assertEquals("PingOne platform should be preserved", PushPlatform.PING_ONE.name, retrievedPingOne?.platform)
        assertEquals("PingAM platform should be preserved", PushPlatform.PING_AM.name, retrievedPingId?.platform)
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
        val testToken = createTestPushDeviceToken()
        storage.storePushDeviceToken(testToken)

        // Retrieve the current token
        val currentToken = storage.getCurrentPushDeviceToken()

        // Verify the token was retrieved correctly
        assertNotNull("Current token should not be null", currentToken)
        assertEquals("Token ID should match", testToken.id, currentToken?.id)
        assertEquals("Token value should match", testToken.tokenId, currentToken?.tokenId)
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

        // Store tokens in sequence (simulating token refreshes over time)
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
        // Ensure we start with no tokens
        val initialTokens = storage.getAllPushDeviceTokens()
        assertEquals("Should have no tokens initially", 0, initialTokens.size)

        // Create and store multiple tokens
        val token1 = createTestPushDeviceToken(tokenId = "token-1")
        val token2 = createTestPushDeviceToken(tokenId = "token-2")
        val token3 = createTestPushDeviceToken(tokenId = "token-3")

        storage.storePushDeviceToken(token1)
        storage.storePushDeviceToken(token2)
        storage.storePushDeviceToken(token3)

        // Retrieve all tokens
        val allTokens = storage.getAllPushDeviceTokens()

        // Verify all tokens were retrieved
        assertEquals("Should retrieve all 3 tokens", 3, allTokens.size)

        // Verify each token was retrieved correctly
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
        // Create and store multiple tokens
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
     * Test that notifications can be retrieved by message ID
     */
    @Test
    fun testGetNotificationByMessageId() = runTest {
        // Create and store a test credential
        val testCredential = createTestPushCredential()
        storage.storePushCredential(testCredential)

        // Create a notification with a specific message ID
        val specificMessageId = "unique-message-id-123"
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
        assertEquals("First notification should be the recent one", recentNotification.id, remaining[0].id)
        assertEquals("Second notification should be the newest one", newestNotification.id, remaining[1].id)
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

        // Verify the right notifications remain
        val allRemaining = storage.getAllPushNotifications()
        val remainingIds = allRemaining.map { it.id }.toSet()
        assertTrue("Should have kept credential1's new notification", remainingIds.contains(newNotificationCred1.id))
        assertTrue("Should have kept credential2's old notification", remainingIds.contains(oldNotificationCred2.id))
        assertTrue("Should have kept credential2's new notification", remainingIds.contains(newNotificationCred2.id))
        assertFalse("Should have removed credential1's old notification", remainingIds.contains(oldNotificationCred1.id))
    }
}
