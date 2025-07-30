/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import java.util.Scanner

/**
 * Check system property with expected value.
 */
abstract class SystemPropertyDetector : RootDetector {

    override suspend fun isRooted(context: Context): Double {
        exists(getProperties())
        return if (exists(getProperties())) {
            1.0
        } else {
            0.0
        }
    }

    private fun exists(properties: Map<String, String>): Boolean {
        for (line in propsReader()) {
            for (entry in properties.entries.toSet()) {
                return (line.contains(entry.key) &&
                    line.contains("[${entry.value}]"))
            }
        }
        return false
    }

    private fun propsReader(): List<String> {
        try {
            val inputStream = Runtime.getRuntime().exec("getprop").inputStream
            if (inputStream == null) return emptyList()
            return Scanner(inputStream)
                .useDelimiter("\\A").next().split("\n")
        } catch (exception: Exception) {
            return emptyList()
        }
    }

    internal abstract fun getProperties(): Map<String, String>
}