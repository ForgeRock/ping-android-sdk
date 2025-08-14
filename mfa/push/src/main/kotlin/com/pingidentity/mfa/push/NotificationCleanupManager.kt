/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.logger.Logger
import com.pingidentity.mfa.push.storage.PushStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manager class for handling push notification cleanup operations.
 * It implements the cleanup strategies defined in [NotificationCleanupConfig].
 * Responsible for removing old or excessive notifications based on the configured cleanup mode.
 */
internal class NotificationCleanupManager(
    private val storage: PushStorage,
    private val config: NotificationCleanupConfig,
    private val logger: Logger
) {
    /**
     * Run the cleanup operation based on the configured cleanup mode.
     * If the cleanup mode is NONE, no cleanup will be performed.
     *
     * @param credentialId Optional ID of a specific credential to cleanup notifications for.
     * @return The number of notifications removed during cleanup.
     */
    suspend fun runCleanup(credentialId: String? = null): Int = withContext(Dispatchers.IO) {
        try {
            val count = when (config.cleanupMode) {
                NotificationCleanupConfig.CleanupMode.NONE -> {
                    logger.d("Notification cleanup skipped (mode: NONE)")
                    0
                }
                NotificationCleanupConfig.CleanupMode.COUNT_BASED -> {
                    cleanupByCount(credentialId)
                }
                NotificationCleanupConfig.CleanupMode.AGE_BASED -> {
                    cleanupByAge(credentialId)
                }
                NotificationCleanupConfig.CleanupMode.HYBRID -> {
                    // Run both cleanups, age-based first
                    val ageRemoved = cleanupByAge(credentialId)
                    val countRemoved = cleanupByCount(credentialId)
                    ageRemoved + countRemoved
                }
            }
            return@withContext count
        } catch (e: Exception) {
            logger.e("Failed to run notification cleanup: ${e.message}", e)
            return@withContext 0
        }
    }

    /**
     * Cleanup notifications by count - keeps only the most recent notifications up to the configured limit.
     *
     * @param credentialId Optional ID of a specific credential to cleanup notifications for.
     * @return The number of notifications removed during cleanup.
     */
    private suspend fun cleanupByCount(credentialId: String?): Int {
        val maxCount = config.maxStoredNotifications
        if (maxCount <= 0) {
            logger.w("Invalid maxStoredNotifications value: $maxCount. Skipping count-based cleanup.")
            return 0
        }

        try {
            val removed = storage.purgePushNotificationsByCount(maxCount, credentialId)
            if (removed > 0) {
                logger.d("Count-based cleanup: removed $removed notifications to maintain max count of $maxCount")
            } else {
                logger.d("Count-based cleanup: no notifications needed to be removed")
            }
            return removed
        } catch (e: Exception) {
            logger.e("Failed to perform count-based notification cleanup: ${e.message}", e)
            return 0
        }
    }

    /**
     * Cleanup notifications by age - removes notifications older than the configured maximum age.
     *
     * @param credentialId Optional ID of a specific credential to cleanup notifications for.
     * @return The number of notifications removed during cleanup.
     */
    private suspend fun cleanupByAge(credentialId: String?): Int {
        val maxAgeDays = config.maxNotificationAgeDays
        if (maxAgeDays <= 0) {
            logger.w("Invalid maxNotificationAgeDays value: $maxAgeDays. Skipping age-based cleanup.")
            return 0
        }

        try {
            val removed = storage.purgePushNotificationsByAge(maxAgeDays, credentialId)
            if (removed > 0) {
                logger.d("Age-based cleanup: removed $removed notifications older than $maxAgeDays days")
            } else {
                logger.d("Age-based cleanup: no notifications older than $maxAgeDays days")
            }
            return removed
        } catch (e: Exception) {
            logger.e("Failed to perform age-based notification cleanup: ${e.message}", e)
            return 0
        }
    }
}
