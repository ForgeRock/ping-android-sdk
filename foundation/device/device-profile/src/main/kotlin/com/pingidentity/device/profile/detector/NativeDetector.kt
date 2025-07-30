package com.pingidentity.device.profile.detector

import android.content.Context
import android.util.Log

/**
 * Check su command natively using NDK
 */
class NativeDetector : FileDetector() {

    override val key: String
        get() = NativeDetector::class.java.simpleName

    override fun getFilenames(): List<String> = listOf("su")

    external fun exists(pathArray: Array<Any>): Int

    override fun isRooted(context: Context): Double {
        if (!libraryLoaded) return 0.0

        val pathList = PATHS.flatMap { path ->
            getFilenames().map { filename ->
                path + filename
            }
        }.toTypedArray()

        // 2. Create an Array<Any> from the list, which matches the function's requirement
        val pathsAsAny = Array<Any>(pathList.size) { i -> pathList[i] }

        try {
            if (exists(pathsAsAny) > 0) {
                return 1.0
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Unable to link to tool-file library", e)
            return 0.0
        }
        return 0.0
    }

    companion object {
        private val TAG = NativeDetector::class.java.simpleName
        private var libraryLoaded = false

        init {
            try {
                System.loadLibrary("tool-file")
                libraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Unable to link to tool-file library", e)
            }
        }
    }
}