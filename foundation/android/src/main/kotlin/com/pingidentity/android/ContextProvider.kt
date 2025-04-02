/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context

/**
 * Singleton object that provides application context and current activity.
 *
 * This object is used to provide a global reference to the application context and the current activity.
 * It is initialized with the application context.
 *
 * @property context The application context.
 * @property currentActivity The current activity.
 */
@SuppressLint("StaticFieldLeak")
object ContextProvider {

    lateinit var context: Context
        internal set

    lateinit var currentActivity: Activity
        internal set

    /**
     * Initializes the ContextProvider with the application context.
     *
     * This method is used to initialize the ContextProvider with the application context.
     * It should be called once, when the application is created.
     *
     * @param context The application context.
     */
    fun init(context: Context) {
        this.context = context.applicationContext
    }
}