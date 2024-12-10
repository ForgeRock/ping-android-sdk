package com.pingidentity.logger

import android.util.Log

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