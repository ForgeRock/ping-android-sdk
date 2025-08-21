/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

/**
 * Configuration class for push notification cleanup settings.
 * This class defines how push notifications are cleaned up automatically.
 *
 * @property cleanupMode The notification cleanup strategy to use. Default is COUNT_BASED.
 *                      Options are:
 *                      - NONE: No automatic cleanup.
 *                      - COUNT_BASED: Remove oldest notifications when count exceeds maximum.
 *                      - AGE_BASED: Remove notifications older than the maximum age.
 *                      - HYBRID: Apply both count and age limits.
 * @property maxStoredNotifications Maximum number of notifications to retain when using COUNT_BASED or HYBRID mode.
 * @property maxNotificationAgeDays Maximum age in days for notifications when using AGE_BASED or HYBRID mode.
 */
class NotificationCleanupConfig {
    var cleanupMode: CleanupMode = CleanupMode.COUNT_BASED
    var maxStoredNotifications: Int = DEFAULT_MAX_NOTIFICATIONS
    var maxNotificationAgeDays: Int = DEFAULT_MAX_AGE_DAYS

    /**
     * Cleanup mode for push notifications.
     */
    enum class CleanupMode {
        /**
         * No automatic cleanup.
         */
        NONE,

        /**
         * Remove oldest notifications when count exceeds maximum.
         */
        COUNT_BASED,

        /**
         * Remove notifications older than the maximum age.
         */
        AGE_BASED,

        /**
         * Apply both count and age limits.
         */
        HYBRID
    }

    companion object {
        /**
         * Default maximum number of notifications to retain.
         */
        const val DEFAULT_MAX_NOTIFICATIONS = 100

        /**
         * Default maximum age in days for notifications.
         */
        const val DEFAULT_MAX_AGE_DAYS = 30

        /**
         * Create a NotificationCleanupConfig instance with the specified configuration block.
         *
         * Example usage:
         * ```
         * val config = NotificationCleanupConfig {
         *    cleanupMode = CleanupMode.AGE_BASED
         *    maxNotificationAgeDays = 60
         * }
         * ```
         * @param block Configuration block to apply to the NotificationCleanupConfig.
         * @return A new NotificationCleanupConfig instance with the applied settings.
         */
        operator fun invoke(block: NotificationCleanupConfig.() -> Unit = {}) =
            NotificationCleanupConfig().apply(block)

        /**
         * Create a NotificationCleanupConfig with default settings (no cleanup).
         *
         * @return A default NotificationCleanupConfig instance.
         */
        fun default(): NotificationCleanupConfig {
            return NotificationCleanupConfig()
        }
    }
}
