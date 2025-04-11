/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.logger

/**
 * Logger interface that provides methods for logging different levels of information.
 */
interface Logger {
    /**
     * Logs a debug message.
     * @param message The debug message to be logged.
     */
    fun d(message: String)

    /**
     * Logs an informational message.
     *
     * @param message The message to be logged.
     */
    fun i(message: String)

    /**
     * Logs a warning message.
     *
     * @param message The warning message to be logged.
     * @param throwable Optional Throwable associated with the warning. Default is null.
     */
    fun w(
        message: String,
        throwable: Throwable? = null,
    )

    /**
     * Logs an error message.
     *
     * @param message The error message to be logged.
     * @param throwable Optional Throwable associated with the error. Default is null.
     */
    fun e(
        message: String,
        throwable: Throwable? = null,
    )

    companion object {
        private var current: Logger? = null

        /**
         * Global logger instance. If no logger is set, it defaults to Logger.NONE.
         */
        var logger: Logger
            get() = current ?: Logger.NONE
            set(value) {
                current = value
            }
    }
}