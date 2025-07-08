/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect

import com.pingidentity.orchestrate.Module
import com.pingidentity.utils.PingDsl

/**
 * Module for managing the lifecycle of the Protect SDK.
 * This module initializes the Protect SDK and manages the pause/resume behavior
 * of behavioral data collection based on the authentication lifecycle.
 */
val ProtectLifecycle =
    Module.of(::ProtectLifecycleConfig) {
        init {
            Protect.config {
                envId = config.envId
                deviceAttributesToIgnore = config.deviceAttributesToIgnore
                customHost = config.customHost
                isConsoleLogEnabled = config.isConsoleLogEnabled
                isLazyMetadata = config.isLazyMetadata
                isBehavioralDataCollection = config.isBehavioralDataCollection
            }
            Protect.initialize()
        }

        start {
            if (config.resumeBehavioralDataOnStart) {
                Protect.resumeBehavioralData()
            }
            it
        }

        success {
            if (config.pauseBehavioralDataOnSuccess) {
                Protect.pauseBehavioralData()
            }
            it
        }
    }

/**
 * Configuration for the Protect Lifecycle Module.
 * This module allows you to pause and resume behavioral data collection
 * based on the lifecycle of the authentication process.
 */
@PingDsl
class ProtectLifecycleConfig : ProtectConfig() {
    /**
     * Whether to pause behavioral data collection on successful authentication.
     * Default is false, meaning behavioral data will continue to be collected.
     */
    var pauseBehavioralDataOnSuccess: Boolean = false
    /**
     * Whether to resume behavioral data collection when the module starts.
     * Default is false, meaning behavioral data will not be resumed automatically.
     */
    var resumeBehavioralDataOnStart: Boolean = false
}