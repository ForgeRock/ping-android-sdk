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
import com.pingidentity.fido2.Constants
import com.pingidentity.logger.Logger
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

/**
 * An abstract base class for FIDO2 collectors in the DaVinci workflow.
 *
 * This class provides common functionality for FIDO2-related collectors, handling
 * initialization of basic properties like key, label, trigger, and required status.
 * It implements the necessary interfaces for DaVinci workflow integration.
 *
 * @property key The unique identifier for the collector
 * @property label The display label for the collector
 * @property trigger The trigger event that activates this collector
 * @property required Whether the collector is mandatory for the workflow
 */
abstract class AbstractFido2Collector : Collector<JsonObject>, DaVinciAware, Submittable {
    /**
     * The DaVinci workflow instance that this collector is associated with.
     * This is automatically injected by the DaVinci framework.
     */
    override lateinit var davinci: DaVinci

    /**
     * Logger instance for this collector, lazily initialized from the DaVinci configuration.
     */
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

    /**
     * Returns the event type that this collector handles.
     *
     * @return The event type string, always "submit" for FIDO2 collectors
     */
    override fun eventType(): String = Constants.EVENT_TYPE_SUBMIT

    /**
     * Returns the unique identifier for this collector instance.
     *
     * @return The collector's key as its identifier
     */
    override fun id(): String {
        return key
    }

    /**
     * Initializes the collector with the provided input data.
     *
     * Extracts and sets the key, label, trigger, and required properties from the input JSON.
     *
     * @param input The JSON object containing initialization parameters
     * @return This collector instance for method chaining
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    override fun init(input: JsonObject): Collector<JsonObject> {
        key = input[Constants.FIELD_KEY]?.jsonPrimitive?.content ?: ""
        label = input[Constants.FIELD_LABEL]?.jsonPrimitive?.content ?: ""
        trigger = input[Constants.FIELD_TRIGGER]?.jsonPrimitive?.content ?: ""
        required = input[Constants.FIELD_REQUIRED]?.jsonPrimitive?.boolean ?: false
        return this
    }
}