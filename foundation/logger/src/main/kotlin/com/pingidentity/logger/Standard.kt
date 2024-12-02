/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.logger

import android.util.Log

/**
 * Standard class implementing the Logger interface.
 * This class provides methods to log messages using Android's Log utility.
 */
open class Standard : Logger {
    /**
     * Logs a debug message using Android's Log utility.
     *
     * @param message The message to log.
     */
    override fun d(message: String) {
        Log.d(TAG, message)
    }

    /**
     * Logs an info message using Android's Log utility.
     *
     * @param message The message to log.
     */
    override fun i(message: String) {
        Log.i(TAG, message)
    }

    /**
     * Logs a warning message using Android's Log utility.
     *
     * @param message The message to log.
     * @param throwable An optional Throwable to log.
     */
    override fun w(
        message: String,
        throwable: Throwable?,
    ) {
        Log.w(TAG, message, throwable)
    }

    /**
     * Logs an error message using Android's Log utility.
     *
     * @param message The message to log.
     * @param throwable An optional Throwable to log.
     */
    override fun e(
        message: String,
        throwable: Throwable?,
    ) {
        Log.e(TAG, message, throwable)
    }

    companion object {
        /**
         * Tag used for logging.
         */
        private const val TAG = "Ping SDK ${BuildConfig.VERSION_NAME}"
    }
}

/**
 * A lazy-initialized Logger instance for standard logging.
 */
val Logger.Companion.STANDARD: Logger by lazy { Standard() }

/**
 * A lazy-initialized Logger instance that only logs warnings and errors.
 */
val Logger.Companion.WARN: Logger by lazy {
    object : Standard() {
        /**
         * Logs a debug message. This implementation does nothing.
         *
         * @param message The debug message to be logged.
         */
        override fun d(message: String) {
        }

        /**
         * Logs an info message. This implementation does nothing.
         *
         * @param message The informational message to be logged.
         */
        override fun i(message: String) {
        }
    }
}