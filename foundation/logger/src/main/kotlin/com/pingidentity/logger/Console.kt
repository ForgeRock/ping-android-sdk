/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.logger

/**
 * Console class implementing the Logger interface.
 * This class provides methods to log messages to the console.
 */
open class Console : Logger {
    /**
     * Logs a debug message to the console.
     *
     * @param message The message to log.
     */
    override fun d(message: String) {
        println(message)
    }

    /**
     * Logs an info message to the console.
     *
     * @param message The message to log.
     */
    override fun i(message: String) {
        println(message)
    }

    /**
     * Logs a warning message to the console.
     *
     * @param message The message to log.
     * @param throwable An optional Throwable to log.
     */
    override fun w(
        message: String,
        throwable: Throwable?,
    ) {
        println(message)
        throwable?.printStackTrace()
    }

    /**
     * Logs an error message to the console.
     *
     * @param message The message to log.
     * @param throwable An optional Throwable to log.
     */
    override fun e(
        message: String,
        throwable: Throwable?,
    ) {
        println(message)
        throwable?.printStackTrace()
    }
}

/**
 * A lazy-initialized Logger instance for console logging.
 */
val Logger.Companion.CONSOLE: Logger by lazy { Console() }