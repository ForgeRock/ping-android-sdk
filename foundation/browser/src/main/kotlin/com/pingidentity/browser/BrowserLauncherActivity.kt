/*
 * Copyright (c) 2024 - 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.browser

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.pingidentity.browser.BrowserLauncher.logger
import kotlinx.coroutines.flow.MutableStateFlow


/**
 * The BrowserLauncherActivity class is responsible for managing the [ActivityResultContracts],
 * the [ActivityResultContracts] needs to be registered during the [ComponentActivity.onCreate].
 */
internal class BrowserLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        val state: MutableStateFlow<ActivityResult?> = MutableStateFlow(null)
        //registerForActivityResult needs to be called in onCreate()
        //The activity to be launched is CustomTabActivity
        val launcher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                logger.d("Result from CustomTabActivity: $it")
                state.value = it
                finish()
            }


        BrowserLauncher.onLauncherCreated(IntentLauncher(launcher, state))
    }

    override fun onDestroy() {
        super.onDestroy()
        BrowserLauncher.reset()
    }

}