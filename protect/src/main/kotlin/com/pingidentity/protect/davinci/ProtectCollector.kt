/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect.davinci

import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.protect.Protect
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.coroutineContext

/**
 * A collector class for handling RISK Component.
 */
class ProtectCollector : Collector<String> {

    var key = ""
        private set
    var behavioralDataCollection = true
        private set
    var universalDeviceIdentification = false
        private set

    private var value: String = ""

    /**
     * Initializes the risk collector with the given input JSON object.
     *
     * @param input The JSON object containing initialization data.
     */
    override fun init(input: JsonObject): Collector<String> {
        key = input["key"]?.jsonPrimitive?.content ?: ""
        behavioralDataCollection = input["behavioralDataCollection"]?.jsonPrimitive?.boolean ?: true
        universalDeviceIdentification =
            input["universalDeviceIdentification"]?.jsonPrimitive?.boolean ?: false
        return this
    }

    override fun id() = key

    override fun payload(): String? {
        return value.ifEmpty {
            null
        }
    }

    /**
     * Collects data from the Protect SDK and returns it as a Result.
     *
     * @return Result containing the collected data or an exception if an error occurs.
     */
    suspend fun collect(): Result<String> {
        try {
            Protect.config {
                isBehavioralDataCollection = behavioralDataCollection
            }
            Protect.init()
            value = Protect.data()
            return Result.success(value)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            return Result.failure(e)
        }
    }
}