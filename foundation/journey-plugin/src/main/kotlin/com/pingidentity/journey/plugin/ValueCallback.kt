/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.plugin

/**
 * An interface representing a value callback.
 * It contains an ID and a value.
 */
interface ValueCallback: Callback {
    val id: String
    var value: String
}