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
class NotificationCleanupConfig(
    val cleanupMode: CleanupMode = CleanupMode.COUNT_BASED,
    val maxStoredNotifications: Int = DEFAULT_MAX_NOTIFICATIONS,
    val maxNotificationAgeDays: Int = DEFAULT_MAX_AGE_DAYS
) {
    /**
     * Builder-style DSL constructor for NotificationCleanupConfig.
     *
     * Example usage:
     * ```
     * val config = NotificationCleanupConfig {
     *     cleanupMode = CleanupMode.COUNT_BASED
     *     maxStoredNotifications = 50
     * }
     * ```
     */
    constructor(block: Builder.() -> Unit) : this(
        Builder().apply(block)
    )

    /**
     * Internal constructor to support creation from Builder.
     */
    private constructor(builder: Builder) : this(
        cleanupMode = builder.cleanupMode,
        maxStoredNotifications = builder.maxStoredNotifications,
        maxNotificationAgeDays = builder.maxNotificationAgeDays
    )

    /**
     * Builder class for [NotificationCleanupConfig].
     *
     * Properties can be directly assigned:
     * - cleanupMode: The cleanup strategy to use (NONE, COUNT_BASED, AGE_BASED, HYBRID)
     * - maxStoredNotifications: Maximum number of notifications to retain
     * - maxNotificationAgeDays: Maximum age in days for notifications to retain
     */
    class Builder {
        /**
         * The cleanup mode to use.
         * Default: COUNT_BASED (keeps a maximum number of notifications)
         */
        var cleanupMode: CleanupMode = CleanupMode.COUNT_BASED

        /**
         * Maximum number of notifications to retain when using COUNT_BASED or HYBRID mode.
         * Default: 100
         */
        var maxStoredNotifications: Int = DEFAULT_MAX_NOTIFICATIONS

        /**
         * Maximum age in days for notifications when using AGE_BASED or HYBRID mode.
         * Default: 30 days
         */
        var maxNotificationAgeDays: Int = DEFAULT_MAX_AGE_DAYS
    }

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
         * Create a NotificationCleanupConfig with default settings (no cleanup).
         *
         * @return A default NotificationCleanupConfig instance.
         */
        fun default(): NotificationCleanupConfig {
            return NotificationCleanupConfig()
        }
    }
}
