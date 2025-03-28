/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.logger

/**
 * The None class is an implementation of the Logger interface that performs no operations.
 * This can be used as a default or placeholder logger.
 */
open class None : Logger {
    /**
     * Logs a debug message. This implementation does nothing.
     *
     * @param message The debug message to be logged.
     */
    override fun d(message: String) {
    }

    /**
     * Logs an informational message. This implementation does nothing.
     *
     * @param message The informational message to be logged.
     */
    override fun i(message: String) {
    }

    /**
     * Logs a warning message. This implementation does nothing.
     *
     * @param message The warning message to be logged.
     * @param throwable An optional Throwable associated with the warning.
     */
    override fun w(
        message: String,
        throwable: Throwable?,
    ) {
    }

    /**
     * Logs an error message. This implementation does nothing.
     *
     * @param message The error message to be logged.
     * @param throwable An optional Throwable associated with the error.
     */
    override fun e(
        message: String,
        throwable: Throwable?,
    ) {
    }
}

/**
 * A lazy-initialized Logger instance that performs no operations.
 */
val Logger.Companion.NONE: Logger by lazy { None() }