/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate

/**
 * Class for a SharedContext. A SharedContext represents a shared context in the application.
 * @property map The map that holds the shared context.
 */
class SharedContext(val map: MutableMap<String, Any>) : MutableMap<String, Any> by map {
    /**
     * Returns the value of a specific key from the shared context.
     * @param key The key for which the value is to be returned.
     * @return The value of the key as an object of type T.
     */
    inline fun <reified T> getValue(key: String): T? {
        val value = map[key]
        return if (value is T) value else null
    }
}