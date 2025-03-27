/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.Collector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Class representing a LABEL type.
 *
 * This class inherits from the [Collector] class. It is used to display a label on the form.
 *
 * @constructor Creates a new LabelCollector.
 */
class LabelCollector : Collector<Nothing> {

    var content = ""
        private set


    override fun init(input: JsonObject) {
        content = input["content"]?.jsonPrimitive?.content ?: ""
    }
}