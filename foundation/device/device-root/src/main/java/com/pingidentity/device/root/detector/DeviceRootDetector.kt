/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context

interface RootDetector {
    suspend fun isRooted(context: Context): Double
}

inline fun RootDetector(
    crossinline block: suspend () -> Double
): RootDetector {
    return object : RootDetector {
        override suspend fun isRooted(context: Context): Double = block()
    }
}