/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.browser

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import com.pingidentity.android.ContextProvider
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.URL

internal const val URL = "url"

/**
 * The BrowserLauncherActivity class is responsible for managing the [ActivityResultContracts],
 * the [ActivityResultContracts] needs to be registered during the [ComponentActivity.onCreate].
 */
class BrowserLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val state: MutableStateFlow<ActivityResult?> = MutableStateFlow(null)
        //registerForActivityResult needs to be called in onCreate()
        //The activity to be launched is CustomTabActivity
        val launcher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                state.value = it
                finish()
            }

        BrowserLauncher.init(Launcher(launcher, state))
    }

    override fun onDestroy() {
        super.onDestroy()
        BrowserLauncher.reset()
    }

    companion object {

        /**
         * Launch the provided URL with CustomTab, get the response from CustomTab (exits or redirect).
         * Here is the sequence of call:
         * 1. launch BrowserLauncherActivity and register for Activity Result (CustomTabActivity)
         * 2. BrowserLauncher.launch -> Start CustomTabActivity in background and wait for state update
         * 3. CustomTabActivity -> CustomTab -> CustomTabActivity ->
         * BrowserLauncherActivity ActivityResultCallback() -> state updated, and finish()
         *
         * @param url The URL to authorize.
         * @return A Result containing the authorized Uri.
         */
        suspend fun launch(url: URL,  customizer: CustomTabsIntent.Builder.() -> Unit = {}): Result<Uri> {
            CustomTabActivity.customTabsCustomizer = customizer
            val pending = launchIfNotPending()
            return BrowserLauncher.launch(url, pending)
        }

        /**
         * Launches the activity if it is not already pending.
         * @return A boolean indicating whether the activity is pending.
         */
        private fun launchIfNotPending(): Boolean {
            // If launcher is not null, it means the Activity is resumed from backstack
            // We don't need to launch the Activity again
            return if (BrowserLauncher.launcher == null) {
                val intent = Intent()
                intent.setFlags(FLAG_ACTIVITY_NEW_TASK)
                intent.setClass(ContextProvider.context, BrowserLauncherActivity::class.java)
                ContextProvider.context.startActivity(intent)
                false
            } else {
                true
            }
        }
    }
}