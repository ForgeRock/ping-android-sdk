/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.logger.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for NotificationCleanupManager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationCleanupManagerTest {

    private lateinit var mockStorage: PushStorage
    private lateinit var mockLogger: Logger
    private lateinit var cleanupManager: NotificationCleanupManager

    @Before
    fun setup() {
        // Create mock objects
        mockStorage = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
    }

    /**
     * Test NONE cleanup mode does not perform any cleanup
     */
    @Test
    fun `test none cleanup mode`() = runTest {
        // Configure manager with NONE mode
        val config = NotificationCleanupConfig {
            cleanupMode = NotificationCleanupConfig.CleanupMode.NONE
        }
        cleanupManager = NotificationCleanupManager(mockStorage, config, mockLogger)

        // Run cleanup
        val result = cleanupManager.runCleanup()

        // Verify result is 0 (no notifications removed)
        assertEquals("NONE mode should not remove any notifications", 0, result)

        // Verify no storage methods were called
        coVerify(exactly = 0) { mockStorage.purgePushNotificationsByCount(any(), any()) }
        coVerify(exactly = 0) { mockStorage.purgePushNotificationsByAge(any(), any()) }
    }

    /**
     * Test COUNT_BASED cleanup mode calls the correct storage method
     */
    @Test
    fun `test count based cleanup mode`() = runTest {
        // Configure mock storage to return a specific count when purging
        coEvery { mockStorage.purgePushNotificationsByCount(50, null) } returns 10

        // Configure manager with COUNT_BASED mode
        val config = NotificationCleanupConfig {
            cleanupMode = NotificationCleanupConfig.CleanupMode.COUNT_BASED
            maxStoredNotifications = 50
        }
        cleanupManager = NotificationCleanupManager(mockStorage, config, mockLogger)

        // Run cleanup
        val result = cleanupManager.runCleanup()

        // Verify result is 10 (matching the mock return value)
        assertEquals("COUNT_BASED mode should return count from storage", 10, result)

        // Verify the correct storage method was called with the right parameters
        coVerify(exactly = 1) { mockStorage.purgePushNotificationsByCount(50, null) }

        // Verify age-based method was not called
        coVerify(exactly = 0) { mockStorage.purgePushNotificationsByAge(any(), any()) }
    }

    /**
     * Test AGE_BASED cleanup mode calls the correct storage method
     */
    @Test
    fun `test age based cleanup mode`() = runTest {
        // Configure mock storage to return a specific count when purging
        coEvery { mockStorage.purgePushNotificationsByAge(30, null) } returns 15

        // Configure manager with AGE_BASED mode
        val config = NotificationCleanupConfig {
            cleanupMode = NotificationCleanupConfig.CleanupMode.AGE_BASED
            maxNotificationAgeDays = 30
        }
        cleanupManager = NotificationCleanupManager(mockStorage, config, mockLogger)

        // Run cleanup
        val result = cleanupManager.runCleanup()

        // Verify result is 15 (matching the mock return value)
        assertEquals("AGE_BASED mode should return count from storage", 15, result)

        // Verify the correct storage method was called with the right parameters
        coVerify(exactly = 1) { mockStorage.purgePushNotificationsByAge(30, null) }

        // Verify count-based method was not called
        coVerify(exactly = 0) { mockStorage.purgePushNotificationsByCount(any(), any()) }
    }

    /**
     * Test HYBRID cleanup mode calls both storage methods
     */
    @Test
    fun `test Hybrid cleanup mode`() = runTest {
        // Configure mock storage to return specific counts when purging
        coEvery { mockStorage.purgePushNotificationsByAge(20, null) } returns 10
        coEvery { mockStorage.purgePushNotificationsByCount(75, null) } returns 5

        // Configure manager with HYBRID mode
        val config = NotificationCleanupConfig {
            cleanupMode = NotificationCleanupConfig.CleanupMode.HYBRID
            maxNotificationAgeDays = 20
            maxStoredNotifications = 75
        }
        cleanupManager = NotificationCleanupManager(mockStorage, config, mockLogger)

        // Run cleanup
        val result = cleanupManager.runCleanup()

        // Verify result is 15 (sum of both cleanup operations)
        assertEquals("HYBRID mode should return sum of both cleanup operations", 15, result)

        // Verify both storage methods were called with the right parameters
        coVerify(exactly = 1) { mockStorage.purgePushNotificationsByAge(20, null) }
        coVerify(exactly = 1) { mockStorage.purgePushNotificationsByCount(75, null) }
    }

    /**
     * Test cleanup with credential ID passes the ID to storage methods
     */
    @Test
    fun `test cleanup with credentialId`() = runTest {
        // Test credential ID
        val testCredentialId = "test-credential-id"

        // Configure mock storage to return specific counts when purging
        coEvery { mockStorage.purgePushNotificationsByCount(50, testCredentialId) } returns 7

        // Configure manager with COUNT_BASED mode
        val config = NotificationCleanupConfig {
            cleanupMode = NotificationCleanupConfig.CleanupMode.COUNT_BASED
            maxStoredNotifications = 50
        }
        cleanupManager = NotificationCleanupManager(mockStorage, config, mockLogger)

        // Run cleanup with credential ID
        val result = cleanupManager.runCleanup(testCredentialId)

        // Verify result is 7 (matching the mock return value)
        assertEquals("Cleanup should return count from storage", 7, result)

        // Verify the correct storage method was called with the credential ID
        coVerify(exactly = 1) { mockStorage.purgePushNotificationsByCount(50, testCredentialId) }
    }

    /**
     * Test invalid configuration values are handled gracefully
     */
    @Test
    fun `test invalid configuration values`() = runTest {
        // Configure manager with invalid values
        val config = NotificationCleanupConfig {
            cleanupMode = NotificationCleanupConfig.CleanupMode.HYBRID
            maxNotificationAgeDays = -5  // Invalid negative value
            maxStoredNotifications = -10  // Invalid negative value
        }
        cleanupManager = NotificationCleanupManager(mockStorage, config, mockLogger)

        // Run cleanup
        val result = cleanupManager.runCleanup()

        // Verify result is 0 (no cleanup performed due to invalid config)
        assertEquals("Invalid config values should result in no cleanup", 0, result)

        // Verify no storage methods were called
        coVerify(exactly = 0) { mockStorage.purgePushNotificationsByCount(any(), any()) }
        coVerify(exactly = 0) { mockStorage.purgePushNotificationsByAge(any(), any()) }
    }

    /**
     * Test storage exceptions are handled gracefully
     */
    @Test
    fun `test storage exceptions`() = runTest {
        // Configure mock storage to throw an exception
        coEvery { mockStorage.purgePushNotificationsByCount(any(), null) } throws RuntimeException("Storage error")

        // Configure manager with COUNT_BASED mode
        val config = NotificationCleanupConfig {
            cleanupMode = NotificationCleanupConfig.CleanupMode.COUNT_BASED
            maxStoredNotifications = 50
        }
        cleanupManager = NotificationCleanupManager(mockStorage, config, mockLogger)

        // Run cleanup
        val result = cleanupManager.runCleanup()

        // Verify result is 0 (no cleanup performed due to exception)
        assertEquals("Exceptions should be caught and result in no cleanup", 0, result)

        // Verify error was logged
        val messageSlot = slot<String>()
        coVerify(exactly = 1) { mockLogger.e(capture(messageSlot), any<RuntimeException>()) }
        assert(messageSlot.captured.contains("Failed to perform count-based notification cleanup"))
    }
}
