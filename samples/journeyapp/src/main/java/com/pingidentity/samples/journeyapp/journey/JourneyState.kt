/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.journeyapp.journey

import com.pingidentity.orchestrate.Node

data class JourneyState(val node: Node? = null,  var counter: Int = 0) {
    init {
        counter++
    }
}