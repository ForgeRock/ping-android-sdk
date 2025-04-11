/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Class representing a single value collector.
 *
 * This class extends from the [FieldCollector] class and implements the Collector interface.
 * It is used to collect a single value.
 *
 * @constructor Creates a new SingleValueCollector with the given input.
 * @property value The value to collect.
 *
 */
open class SingleValueCollector : FieldCollector() {
    var value: String = ""

    /**
     * Function to initialize the single value collector.
     * @param input The input JSON Element to parse.
     */
    override fun init(input: JsonElement) {
        super.init(input)
        value = input.jsonPrimitive.content
    }
}
