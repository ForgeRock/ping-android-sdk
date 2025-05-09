/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.davinci

import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.davinci.plugin.DaVinciAware
import com.pingidentity.davinci.plugin.Submittable
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.Workflow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

/**
 * An abstract class for FIDO2 collectors.
 *
 * @property key The key for the collector.
 * @property label The label for the collector.
 * @property trigger The trigger for the collector.
 * @property required Whether the collector is required or not.
 */
abstract class AbstractFido2Collector : Collector<JsonObject>, DaVinciAware, Submittable {
    /**
     * The DaVinci workflow instance.
     */
    override lateinit var davinci: DaVinci
    val logger: Logger by lazy {
        davinci.config.logger
    }

    var key = ""
        private set

    var label = ""
        private set
    var trigger = ""
        private set
    var required = false
        private set

    override fun eventType(): String = "submit"

    override fun id(): String {
        return key
    }

    override fun init(input: JsonObject): Collector<JsonObject> {
        key = input["key"]?.jsonPrimitive?.content ?: ""
        label = input["label"]?.jsonPrimitive?.content ?: ""
        trigger = input["trigger"]?.jsonPrimitive?.content ?: ""
        required = input["required"]?.jsonPrimitive?.boolean ?: false
        return this
    }
}