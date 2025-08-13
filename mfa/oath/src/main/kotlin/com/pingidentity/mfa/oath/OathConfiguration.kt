/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import com.pingidentity.logger.Logger
import com.pingidentity.mfa.commons.MfaConfiguration

/**
 * Configuration class specific for OATH MFA functionality.
 * Extends the base MfaConfiguration with OATH-specific settings.
 */
class OathConfiguration(
    encryptionEnabled: Boolean = true,
    timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    enableCredentialCache: Boolean = false,
    logger: Logger = Logger.logger
) : MfaConfiguration(
    encryptionEnabled = encryptionEnabled,
    timeoutMs = timeoutMs,
    enableCredentialCache = enableCredentialCache,
    logger = logger
) {
    /**
     * Builder-style DSL constructor for OathConfiguration.
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

    /**
     * Builder class for [OathConfiguration].
     */
    class Builder : MfaConfiguration.Builder() {
        var storage: OathStorage? = null
    }

    companion object {
        /**
         * Create an OathConfiguration with default settings.
         *
         * @return A default OathConfiguration instance.
         */
        fun default(): OathConfiguration {
            return OathConfiguration {}
        }
    }
}
