/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.Submittable

/**
 * Class representing a FLOW_BUTTON, FLOW_LINK Type.
 *
 * This class inherits from the FieldCollector class and implements the Collector interface.
 * It is used to collect data in a flow.
 *
 * @constructor Creates a new FlowCollector with the given input.
 */
class FlowCollector : SingleValueCollector(), Submittable {
    override fun eventType() = "action"
}