/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect.journey

import com.pingidentity.protect.Protect
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.Collections
import kotlin.coroutines.coroutineContext

/**
 * A callback class for initializing the PingOne Protect SDK.
 * This class extends [AbstractProtectCallback] and provides functionality to configure
 * the Protect SDK with various parameters such as environment ID, behavioral data collection,
 * console logging, and device attributes to ignore.
 *
 * @property envId The environment ID for the Protect SDK.
 * @property behavioralDataCollection Indicates whether behavioral data collection is enabled.
 * @property consoleLogEnabled Indicates whether console logging is enabled.
 * @property lazyMetadata Indicates whether lazy metadata loading is enabled.
 * @property customHost The custom host for the Protect SDK.
 * @property deviceAttributesToIgnore A list of device attributes to ignore.
 */
class PingOneProtectInitializeCallback : AbstractProtectCallback() {

    var envId: String = ""
        private set
    var behavioralDataCollection: Boolean = false
        private set

    var consoleLogEnabled: Boolean = false
        private set

    var lazyMetadata: Boolean = false
        private set

    var customHost: String = ""
        private set
    var deviceAttributesToIgnore: List<String> = emptyList()
        private set


    override fun init(name: String, value: JsonElement) {
        when (name) {
            "envId" -> envId = value.jsonPrimitive.content
            "behavioralDataCollection" -> behavioralDataCollection = value.jsonPrimitive.boolean
            "consoleLogEnabled" -> consoleLogEnabled = value.jsonPrimitive.boolean
            "deviceAttributesToIgnore" -> deviceAttributesToIgnore =
                getDeviceAttributes(value.jsonArray)

            "customHost" -> customHost = value.jsonPrimitive.content
            "lazyMetadata" -> lazyMetadata = value.jsonPrimitive.boolean
            else -> {}
        }
    }

    /**
     * Get the getDeviceAttributes attribute
     *
     * @param array The data source
     */
    private fun getDeviceAttributes(array: JsonArray): List<String> {
        val list = mutableListOf<String>()
        array.forEach {
            list.add(it.jsonPrimitive.content)
        }
        return Collections.unmodifiableList(list)
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
                isLazyMetadata = lazyMetadata
                isConsoleLogEnabled = consoleLogEnabled
                deviceAttributesToIgnore =
                    this@PingOneProtectInitializeCallback.deviceAttributesToIgnore
                customHost = this@PingOneProtectInitializeCallback.customHost.nullIfEmpty()
            }
            if (behavioralDataCollection) {
                Protect.resumeBehavioralData()
            } else {
                Protect.pauseBehavioralData()
            }
            Protect.init()
            return Result.success(Unit)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            error(e.message ?: "clientError")
            return Result.failure(e)
        }
    }
}

private fun String.nullIfEmpty(): String? {
    return this.takeIf { it.isNotEmpty() }
}

