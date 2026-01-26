/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect.journey

import com.pingidentity.protect.Protect
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

/**
 * A callback class for initializing the PingOne Protect SDK.
 * This class extends [AbstractProtectCallback] and provides functionality to configure
 * the Protect SDK with various parameters such as environment ID, behavioral data collection,
 * console logging, and device attributes to ignore.
 *
 * @property envId The environment ID for the Protect SDK.
 * @property behavioralDataCollection Indicates whether behavioral data collection is enabled.
 * @property customHost The custom host for the Protect SDK.
 */
class PingOneProtectInitializeCallback : AbstractProtectCallback() {

    var envId: String = ""
        private set
    var behavioralDataCollection: Boolean = false
        private set
    var customHost: String = ""
        private set
    var agentIdentification: Boolean = false
        private set
    var universalDeviceIdentification: Boolean = false
        private set

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "envId" -> envId = value.jsonPrimitive.content
            "behavioralDataCollection" -> behavioralDataCollection = value.jsonPrimitive.boolean
            "customHost" -> customHost = value.jsonPrimitive.content
            "agentIdentification" -> agentIdentification = value.jsonPrimitive.boolean
            "universalDeviceIdentification" -> universalDeviceIdentification = value.jsonPrimitive.boolean
            else -> {}
        }
    }

    /**
     * Start the PingOne Protect SDK.
     * @return A Result containing either success or failure.
     */
    suspend fun start(): Result<Unit> {
        try {
            Protect.config {
                envId = this@PingOneProtectInitializeCallback.envId.nullIfEmpty()
                isBehavioralDataCollection = behavioralDataCollection
                agentIdentification = this@PingOneProtectInitializeCallback.agentIdentification
                customHost = this@PingOneProtectInitializeCallback.customHost.nullIfEmpty()
                universalDeviceIdentification = this@PingOneProtectInitializeCallback.universalDeviceIdentification
            }
            if (behavioralDataCollection) {
                Protect.resumeBehavioralData()
            } else {
                Protect.pauseBehavioralData()
            }
            Protect.initialize()
            return Result.success(Unit)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            error(e.message ?: CLIENT_ERROR)
            return Result.failure(e)
        }
    }
}

private fun String.nullIfEmpty(): String? {
    return this.takeIf { it.isNotEmpty() }
}

