/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.logger.Logger
import com.pingidentity.mfa.commons.MfaConfiguration

/**
 * Configuration class specific for Push MFA functionality.
 * Extends the base [MfaConfiguration] with Push-specific settings.
 *
 * @property customPushHandlers Map of custom PushHandlers that will be used along with default handlers.
 * @property notificationCleanupConfig Configuration for automatic cleanup of push notifications.
 */
class PushConfiguration(
    encryptionEnabled: Boolean = true,
    timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    enableCredentialCache: Boolean = false,
    logger: Logger = Logger.logger,
    val customPushHandlers: Map<String, PushHandler> = emptyMap(),
    val notificationCleanupConfig: NotificationCleanupConfig = NotificationCleanupConfig.default()
) : MfaConfiguration(
    encryptionEnabled = encryptionEnabled,
    timeoutMs = timeoutMs,
    enableCredentialCache = enableCredentialCache,
    logger = logger
) {
    /**
     * Builder-style DSL constructor for PushConfiguration.
     */
    constructor(block: Builder.() -> Unit) : this(
        Builder().apply(block)
    )

    /**
     * Internal constructor to support creation from Builder.
     */
    private constructor(builder: Builder) : this(
        encryptionEnabled = builder.encryptionEnabled,
        timeoutMs = builder.timeoutMs,
        enableCredentialCache = builder.enableCredentialCache,
        logger = builder.logger,
        customPushHandlers = builder.customPushHandlers,
        notificationCleanupConfig = builder.notificationCleanupConfig
    )

    /**
     * Builder class for [PushConfiguration].
     */
    class Builder : MfaConfiguration.Builder() {

        /**
         * Push handlers that will be used along with default handlers.
         * The keys are handler names, and the values are the PushHandler instances.
         */
        var customPushHandlers: Map<String, PushHandler> = emptyMap()

        /**
         * The storage implementation for push notifications.
         * This is used to store and retrieve push credentials and notifications.
         * If not set, the default storage will be used.
         */
        var storage: PushStorage? = null

        /**
         * Configuration for automatic cleanup of push notifications.
         * This defines how push notifications are cleaned up based on the configured strategy.
         */
        var notificationCleanupConfig: NotificationCleanupConfig = NotificationCleanupConfig.default()
    }

    companion object {
        /**
         * Create a PushConfiguration with default settings.
         *
         * @return A default PushConfiguration instance.
         */
        fun default(): PushConfiguration {
            return PushConfiguration {}
        }
    }
}
