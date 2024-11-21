/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.exception

import kotlin.coroutines.cancellation.CancellationException

/**
 * Executes the given block and returns its result, or null if an exception occurs.
 * If a CancellationException is thrown, it is rethrown.
 *
 * @param R The type of the result.
 * @param block The block of code to execute.
 * @return The result of the block, or null if an exception occurs.
 * @throws CancellationException if the block throws a CancellationException.
 */
inline fun <reified R> catchOrNull(block: () -> R): R? {
    return try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }
}
