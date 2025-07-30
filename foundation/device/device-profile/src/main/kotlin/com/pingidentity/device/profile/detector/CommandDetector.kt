package com.pingidentity.device.profile.detector

import android.content.Context
import com.pingidentity.device.root.BaseRootDetector
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Check command exists
 */
abstract class CommandDetector: RootDetector<BaseRootDetector> {
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

    override fun isRooted(context: Context): Double {
        for (command in getCommands()) {
            if (exists(command)) {
                return 1.0
            }
        }
        return 0.0
    }

    abstract fun getCommands(): Array<String>
}