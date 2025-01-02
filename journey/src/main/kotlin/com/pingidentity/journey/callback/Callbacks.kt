/*
 * Copyright (c) 2024 - 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.Callbacks
import com.pingidentity.journey.plugin.RequestInterceptor
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.Request

internal fun Callbacks.request(context: FlowContext, request: Request): Request {
    var result = request
    forEach { callback ->
        if (callback is RequestInterceptor) {
            result = callback.intercept(context, result)
        }
    }
    return result
}

