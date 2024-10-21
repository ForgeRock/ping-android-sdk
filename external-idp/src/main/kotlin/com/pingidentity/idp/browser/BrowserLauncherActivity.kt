/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
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

class BrowserLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val state: MutableStateFlow<ActivityResult?> = MutableStateFlow(null)
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
         * Starts the authorization process.
         * @return The continue token
         */
        suspend fun authorize(url: URL): Result<Uri> {
            val pending = launchIfNotPending()
            return BrowserLauncher.authorize(url, pending)
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

        var customTabsCustomizer: CustomTabsIntent.Builder.() -> Unit = {}
    }
}
