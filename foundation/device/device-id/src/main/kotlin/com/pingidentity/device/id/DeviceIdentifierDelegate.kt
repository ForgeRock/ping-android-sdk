/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.id

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


/**
 * A delegate that implements DeviceIdentifier and caches the result of the `id()` function.
 *
 * This class ensures that the computationally expensive or slow `id()` call is only
 * performed once. Subsequent calls will return the cached value instantly.
 *
 * It uses a [Mutex] to guarantee thread-safe access to the cached ID, preventing
 * race conditions in a concurrent environment.
 *
 * @param delegate The actual [DeviceIdentifier] implementation to which the call is delegated
 * on the first invocation.
 */
class DeviceIdentifierDelegate(internal val delegate: DeviceIdentifier) : DeviceIdentifier {

    private val lock = Mutex()

    // This property will hold the cached ID value. It's nullable because it's not set initially.
    @Volatile
    private var cachedId: String? = null

    /**
     * The suspend function that returns the device identifier.
     *
     * It first checks if the value is already cached. If so, it returns the cached value
     * immediately (the "fast path"). If not, it acquires a lock, double-checks if another
     * coroutine has already computed the value while it was waiting, and then computes
     * and caches the value if it's still null.
     */
    override val id: suspend () -> String = {
        // First, check the value without a lock. This is a quick and efficient check
        // for subsequent calls.
        val currentId = cachedId
        currentId
            ?: // If the value is null, we acquire a lock to safely perform the computation.
            lock.withLock {
                // Double-check inside the lock. Another coroutine might have
                // already calculated the ID while we were waiting for the lock.
                cachedId ?: delegate.id().also { newId ->
                    cachedId = newId
                }
            }
    }
}