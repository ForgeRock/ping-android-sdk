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
        
    /**
     * Builder-style DSL constructor for MfaConfiguration.
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
        logger = builder.logger
    )

    companion object {
        // Timeout for the HTTP client, default is 15 seconds
        const val DEFAULT_TIMEOUT_MS = 15000L
    }

    /**
     * Builder class for [MfaConfiguration].
     */
    class Builder {
        var encryptionEnabled: Boolean = true
        var timeoutMs: Long = DEFAULT_TIMEOUT_MS
        var enableCredentialCache: Boolean = false
        var logger: Logger = Logger.logger
    }
}