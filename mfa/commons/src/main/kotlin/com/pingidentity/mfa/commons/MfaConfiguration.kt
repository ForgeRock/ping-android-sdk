/*
 * Copyright (c) 2025-2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons

import android.content.Context
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Configuration class for MFA module.
 * This class holds all the necessary configuration for the MFA module.
 *
 * @property timeout The timeout for network operations in seconds.
 * @property enableCredentialCache Whether to enable in-memory caching of credentials. By default, this is disabled
 *           for security reasons, as an attacker could potentially access cached credentials from memory dumps.
 * @property logger The logger instance used for logging messages. Defaults to a global logger instance.
 */
open class MfaConfiguration {
    var timeout: Duration = DEFAULT_TIMEOUT_MS
    var enableCredentialCache: Boolean = false
    var logger: Logger = Logger.logger
    val context: Context
        get() = ContextProvider.context
        
    companion object {
        // Timeout for the HTTP client, default is 15 seconds
        val DEFAULT_TIMEOUT_MS = 15.toDuration(DurationUnit.SECONDS)
        operator fun invoke(block: MfaConfiguration.() -> Unit = {}): MfaConfiguration {
            val config = MfaConfiguration()
            config.apply(block) // apply the configuration block
            return config
        }
    }
}