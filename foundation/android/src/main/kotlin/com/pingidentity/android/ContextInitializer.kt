/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.android

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Bundle
import androidx.startup.Initializer

/**
 * This class is responsible for initializing the ContextProvider in the application.
 * It extends the Initializer interface, which means it is part of the initialization process of the application.
 */
class ContextInitializer : Initializer<ContextProvider> {

    override fun create(context: Context): ContextProvider {
        ContextProvider.init(context)
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    ContextProvider.currentActivity = activity
                }

                override fun onActivityStarted(activity: Activity) {
                    ContextProvider.currentActivity = activity
                }

                override fun onActivityResumed(activity: Activity) {
                    ContextProvider.currentActivity = activity
                }

                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })

        return ContextProvider
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}