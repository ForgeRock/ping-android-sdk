/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Class representing DROPDOWN, RADIO type with SINGLE_SELECT inputType.
 *
 * This class inherits from the FieldCollector class and implements the Collector interface.
 * It is used to collect multiple values from a list of options.
 *
 * @constructor Creates a new SingleSelectCollector with the given input.
 */
class SingleSelectCollector : ValidatedCollector() {
    lateinit var options: List<Option>
        private set

    override fun init(input: JsonObject) {
        super.init(input)
        options = input(input)
    }
}
