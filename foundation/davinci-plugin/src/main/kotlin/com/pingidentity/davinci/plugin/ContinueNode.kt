/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.plugin

import com.pingidentity.orchestrate.ContinueNode

/**
 * Extension property for Connector class to get a list of collectors.
 *
 * @return A list of Collector instances.
 */
val ContinueNode.collectors: List<Collector<*>>
    get() = this.actions.filterIsInstance<Collector<*>>()