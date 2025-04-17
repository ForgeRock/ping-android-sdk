/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray

/**
 * Data class representing a Device.
 * @property id The id of the device.
 * @property type The type of the device.
 * @property title The title of the device.
 * @property description The description of the device.
 * @property iconSrc The icon source of the device.
 * @property default Use this device as default.
 * @property value The value of the device.
 */
@Serializable
data class Device(
    val id: String? = null,
    val type: String,
    val title: String = "",
    val description: String = "",
    val iconSrc: String = "",
    val default: Boolean = false,
    val value:String? = null
) {

    companion object {

        /**
         * Function to parse the input JSON object and return a list of devices.
         * @param input The input JSON object.
         * @return A list of devices.
         */
        fun devices(input: JsonObject): List<Device> {
            return input["devices"]?.jsonArray?.map { jsonElement ->
                json.decodeFromJsonElement<Device>(jsonElement)
            } ?: emptyList()
        }
    }
}
