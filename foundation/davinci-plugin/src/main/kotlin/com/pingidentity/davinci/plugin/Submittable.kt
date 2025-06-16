/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.plugin

/**
 * Interface representing a self submittable [Collector].
 * This interface is used to mark [Collector] that can be submitted itself.
 */
interface Submittable {
    /**
     * Returns the event type for this collector.
     * @return The event type as a string.
     */
    fun eventType(): String
}
