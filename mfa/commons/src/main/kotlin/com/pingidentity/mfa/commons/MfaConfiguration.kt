/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons

import android.content.Context
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger

/**
 * Configuration class for MFA module.
 * This class holds all the necessary configuration for the MFA module.
 *
 * @property encryptionEnabled Whether data encryption is enabled for storage.
 * @property timeoutMs The timeout for network operations in milliseconds.
 * @property enableCredentialCache Whether to enable in-memory caching of credentials. By default, this is disabled
 *           for security reasons, as an attacker could potentially access cached credentials from memory dumps.
 * @property logger The logger instance used for logging messages. Defaults to a global logger instance.
 */
class MfaConfiguration private constructor(
    val encryptionEnabled: Boolean = true,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val enableCredentialCache: Boolean = false,
    val logger: Logger = Logger.logger
) {
    /**
     * The application context is retrieved from the ContextProvider.
     */
    val context: Context
        get() = ContextProvider.context

    companion object {
        // Timeout for the HTTP client, default is 15 seconds
        const val DEFAULT_TIMEOUT_MS = 15000L
    }

    /**
     * Builder class for [MfaConfiguration].
     */
    class Builder() {
        private var encryptionEnabled: Boolean = true
        private var timeoutMs: Long = DEFAULT_TIMEOUT_MS
        private var enableCredentialCache: Boolean = false
        private var logger: Logger = Logger.logger
        
        /**
         * Sets whether credential caching is enabled in memory.
         * By default this is disabled for security reasons.
         *
         * @param enabled True to enable credential caching, false otherwise.
         * @return The builder instance.
         */
        fun enableCredentialCache(enabled: Boolean): Builder {
            this.enableCredentialCache = enabled
            return this
        }

        /**
         * Sets whether data encryption is enabled.
         *
         * @param enabled True if encryption is enabled, false otherwise.
         * @return The builder instance.
         */
        fun encryptionEnabled(enabled: Boolean): Builder {
            this.encryptionEnabled = enabled
            return this
        }

        /**
         * Sets the timeout for network operations.
         *
         * @param timeoutMs The timeout in milliseconds.
         * @return The builder instance.
         */
        fun timeoutMs(timeoutMs: Long): Builder {
            this.timeoutMs = timeoutMs
            return this
        }

        /**
         * Sets the logger instance for logging messages.
         *
         * @param logger The logger instance to use.
         * @return The builder instance.
         */
        fun logger(logger: Logger): Builder {
            this.logger = logger
            return this
        }

        /**
         * Builds a new [MfaConfiguration] instance with the configured values.
         *
         * @return A new [MfaConfiguration] instance.
         */
        fun build(): MfaConfiguration {
            return MfaConfiguration(
                encryptionEnabled = encryptionEnabled,
                timeoutMs = timeoutMs,
                enableCredentialCache = enableCredentialCache,
                logger = logger
            )
        }
    }
}