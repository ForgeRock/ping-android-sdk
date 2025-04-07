/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.Submittable
import kotlinx.serialization.json.JsonObject

/**
 * A collector for device registration.
 */
class DeviceRegistrationCollector : FieldCollector<String?>(), Submittable {

    override fun eventType(): String = "submit"

    // The list of devices available for registration.
    lateinit var devices: List<Device>
        private set

    var value: Device? = null

   override fun init(input: JsonObject) {
        super.init(input)
        devices = Device.devices(input)
    }

    override fun payload(): String? {
        value?.let {
            return it.type
        } ?: return null
    }

}
