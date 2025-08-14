/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import com.pingidentity.mfa.commons.MfaConfiguration
import com.pingidentity.mfa.oath.storage.OathStorage

/**
 * Configuration class specific for OATH MFA functionality.
 * Extends the base MfaConfiguration with OATH-specific settings.
 *
 * @property storage The storage implementation to use for OATH credentials. If null, no storage will be used.
 */

class OathConfiguration: MfaConfiguration() {
     var storage: OathStorage? = null

    companion object {
        /**
         * Creates a new instance of [OathConfiguration] with the provided configuration block.
         *
         * Example usage:
         * ```
         * val config = OathConfiguration {
         *     storage = MyOathStorage()
         *     enableCredentialCache = false
         * }
         * ```
         */
        operator fun invoke(block: OathConfiguration.() -> Unit = {}): OathConfiguration {
            val config = OathConfiguration()
            config.apply(block) // apply the configuration block
            return config
        }
    }
}
