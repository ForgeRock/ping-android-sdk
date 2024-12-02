/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.Collector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Abstract class representing a fields from the form.
 *
 * @property key The key of the field collector.
 * @property label The label of the field collector.
 * @property value The value of the field collector.
 *
 */
abstract class FieldCollector : Collector {
    var key = ""
    var label = ""
    open var value: String = ""

    /**
     * Function to initialize the field collector.
     * @param input The input JSON object to parse.
     */
    override fun init(input: JsonObject) {
        key = input["key"]?.jsonPrimitive?.content ?: ""
        label = input["label"]?.jsonPrimitive?.content ?: ""

    }
}