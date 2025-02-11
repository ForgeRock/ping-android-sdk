/*
 * Copyright (c) 2024 - 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.plugin

import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.Request

/**
 * An interface that should be implemented by classes that need to be transformed itself to a Request.
 */
interface RequestInterceptor {
    /**
     * A function that transform to a Request within a given FlowContext.
     */
    var intercept: FlowContext.(Request) -> Request
}