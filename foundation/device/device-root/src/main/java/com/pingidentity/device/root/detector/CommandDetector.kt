/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Check command exists
 */
abstract class CommandDetector: RootDetector {
    private fun exists(command: String): Boolean {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf("which", command))
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            return !bufferedReader.readLine().isNullOrEmpty()
        } catch (exception: Exception) {
            return false
        } finally {
            process?.destroy()
        }
    }

    override suspend fun isRooted(context: Context): Double {
        for (command in getCommands()) {
            if (exists(command)) {
                return 1.0
            }
        }
        return 0.0
    }

    abstract fun getCommands(): Array<String>
}