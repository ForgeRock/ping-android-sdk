/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.plugin

/**
 * An interface representing a failable entity.
 *
 * This interface defines a contract for entities that can fail and provide an error message.
 */
interface Failable {
    fun error(): String?
}
