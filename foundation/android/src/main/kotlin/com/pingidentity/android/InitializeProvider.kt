/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.android

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle

/**
 * A ContentProvider class that initializes the application context and activity lifecycle callbacks,
 * to keep track of the current activity.
 *
 * It is not intended to be used as a regular content provider,
 * hence the UnsupportedOperationExceptions in the CRUD methods.
 */
class InitializeProvider : ModuleInitializer() {

    override fun initialize() {
        context?.let {
            ContextProvider.init(it)
            val app = it.applicationContext as Application
            app.registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
                /**
                 * Called when an activity is created.
                 *
                 * This method is called when an activity is being created.
                 * It updates the current activity in the [ContextProvider] and logs the activity creation.
                 */
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    ContextProvider.currentActivity = activity
                }

                /**
                 * Called when an activity is started.
                 *
                 * This method is called when an activity is being started.
                 * It updates the current activity in the [ContextProvider]
                 */
                override fun onActivityStarted(activity: Activity) {
                    ContextProvider.currentActivity = activity
                }

                /**
                 * Called when an activity is resumed.
                 *
                 * This method is called when an activity is being resumed.
                 * It updates the current activity in the [ContextProvider].
                 */
                override fun onActivityResumed(activity: Activity) {
                    ContextProvider.currentActivity = activity
                }

                override fun onActivityPaused(activity: Activity) {
                }

                override fun onActivityStopped(activity: Activity) {
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                }

                override fun onActivityDestroyed(activity: Activity) {
                }
            })
        }
    }
}