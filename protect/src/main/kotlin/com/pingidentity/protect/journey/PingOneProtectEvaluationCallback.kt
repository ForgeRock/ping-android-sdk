/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect.journey

import com.pingidentity.protect.Protect
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.coroutineContext

/**
 * A callback class for evaluating Protect data collection.
 * This class extends [AbstractProtectCallback] and provides functionality to collect
 * data from the Protect SDK, with an option to pause behavioral data collection.
 *
 * @property pauseBehavioralData Indicates whether to pause behavioral data collection.
 */
class PingOneProtectEvaluationCallback : AbstractProtectCallback() {

    var pauseBehavioralData: Boolean = false
        private set

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "pauseBehavioralData" -> pauseBehavioralData = value.jsonPrimitive.boolean
            else -> {}
        }
    }

    /**
     * Collects data from the Protect SDK and returns it as a Result.
     *
     * @return Result containing the collected data or an exception if an error occurs.
     */
    suspend fun collect(): Result<String> {
        try {
            val signal = Protect.data()
            if (pauseBehavioralData) {
                Protect.pauseBehavioralData()
            }
            signal(signal, "")
            return Result.success(signal)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            signal("", e.message ?: CLIENT_ERROR)
            return Result.failure(e)
        }
    }

}

