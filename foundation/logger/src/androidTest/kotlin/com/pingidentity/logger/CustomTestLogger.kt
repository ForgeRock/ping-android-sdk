/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.logger

open class CustomTestLogger : Logger {
    val messages = mutableListOf<String>()

    override fun d(message: String) {
        messages.add("DEBUG: $message")
    }

    override fun i(message: String) {
        messages.add("INFO: $message")
    }

    override fun w(message: String, throwable: Throwable?) {
        messages.add("WARN: $message")
    }

    override fun e(message: String, throwable: Throwable?) {
        messages.add("ERROR: $message")
    }
}