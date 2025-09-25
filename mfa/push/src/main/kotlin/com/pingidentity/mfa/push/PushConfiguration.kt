/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import com.pingidentity.mfa.commons.MfaConfiguration
import com.pingidentity.mfa.commons.policy.MfaPolicyEvaluator
import com.pingidentity.mfa.push.storage.PushStorage

/**
 * Configuration class specific for Push MFA functionality.
 * Extends the base [MfaConfiguration] with Push-specific settings.
 *
 * @property storage The storage implementation to use for Push credentials.
 * @property customPushHandlers Map of custom PushHandlers that will be used along with default handlers.
 * @property notificationCleanupConfig Configuration for automatic cleanup of push notifications.
 * @property policyEvaluator The policy evaluator for credential policy validation.
 */
class PushConfiguration : MfaConfiguration() {
    var storage: PushStorage? = null
    var customPushHandlers: Map<String, PushHandler> = emptyMap()
    var notificationCleanupConfig: NotificationCleanupConfig = NotificationCleanupConfig.default()
    var policyEvaluator: MfaPolicyEvaluator? = null

    companion object {

        /**
         * Creates a new instance of [PushConfiguration] with the provided configuration block.
         *
         * Example usage:
         * ```
         * val config = PushConfiguration {
         *     storage = MyPushStorage()
         *     customPushHandlers = mapOf("customHandler" to MyCustomPushHandler())
         * }
         * ```
         */
        operator fun invoke(block: PushConfiguration.() -> Unit = {}): PushConfiguration {
            val config = PushConfiguration()
            config.apply(block) // apply the configuration block
            return config
        }
    }
}
