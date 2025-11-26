/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.davinci.plugin.CollectorFactory
import com.pingidentity.davinci.plugin.Collectors
import com.pingidentity.davinci.plugin.DaVinci
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private const val FORM = "form"
private const val COMPONENTS = "components"
private const val FIELDS = "fields"
private const val VALUE = "value"
private const val FORM_DATA = "formData"

/**
 * Singleton object that handles the parsing and JSON representation of collectors.
 *
 * This object provides functions to parse a JSON object into a list of collectors and to represent a list of collectors as a JSON object.
 */
internal object Form {

    /**
     * Parses a JSON object into a list of collectors.
     *
     * This function takes a JSON object and extracts the "form" field. It then iterates over the "fields" array in the "components" object,
     * parsing each field into a collector and adding it to a list.
     *
     * @param daVinci The DaVinci instance to be injected.
     * @param json The JSON object to parse.
     * @return A list of collectors parsed from the JSON object.
     */
    fun parse(
        daVinci: DaVinci,
        json: JsonObject,
    ): Collectors {
        val collectors = mutableListOf<Collector<*>>()
        json[FORM]?.jsonObject?.get(COMPONENTS)?.jsonObject?.get(FIELDS)?.jsonArray?.let { array ->
            collectors.addAll(CollectorFactory.collector(daVinci, array))
        }

        //Populate values for collectors
        json[FORM_DATA]?.jsonObject?.get(VALUE)?.jsonObject?.let { value ->
            collectors.forEach { collector ->
                value[collector.id()]?.let(collector::init)
            }
        }
        return collectors
    }

}