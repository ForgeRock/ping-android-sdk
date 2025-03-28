/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.utils

import org.json.JSONArray

/**
 * Extension function for `JSONArray` to iterate over each element.
 *
 * @param action A lambda function to be executed for each element in the `JSONArray`.
 */
inline fun JSONArray.forEach(action: (Any) -> Unit) {
    for (i in 0 until this.length()) {
        action(this.get(i))
    }
}