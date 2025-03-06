/*
 * Copyright (c) 2024 - 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.browser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.pingidentity.browser.BrowserLauncher.logger

/**
 * This activity is responsible for launching the CustomTabsIntent with the provided URL.
 */

internal class CustomTabActivity : ComponentActivity() {

    companion object {
        const val URL = "url"
        const val ERROR = "ERROR"
        const val ERROR_MESSAGE = "ERROR_MESSAGE"
        const val ERROR_CANCELED = 1
        const val ERROR_ACTIVITY_NOT_FOUND = 2
        const val ERROR_OTHER = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            // Already launched the browser, don't do it again.
            intent.removeExtra(URL)
        }
    }

    /**
     * Called when the activity will start interacting with the user.
     * This onResume will be invoked when existing the CustomTab
     * e.g closing the CustomTab, or redirect to CustomTab happened.
     * After receiving the exist action from CustomTab, use [setResult] to notify the caller
     * who started this activity.
     */
    override fun onResume() {
        super.onResume()
        //Retrieve the URL from the intent and launch the CustomTabsIntent.
        val url = intent.extras?.getString(URL)
        if (intent.data != null) {
            logger.d("Redirect Uri received: ${intent.data}")
            setResult(RESULT_OK, Intent().setData(intent.data))
            finish()
        } else if (url == null) {
            logger.d("Resuming without URL")
            setResult(RESULT_CANCELED, Intent().putExtra(ERROR, ERROR_CANCELED))
            finish()
        } else {
            // Remove the URL from the intent to avoid launching the browser again.
            try {
                intent.removeExtra(URL)

                // Launch the CustomTabsIntent with the provided URL.
                val builder = CustomTabsIntent.Builder()
                BrowserLauncher.customTabsCustomizer(builder)
                val customTabsIntent = builder.build()
                BrowserLauncher.intentCustomizer(customTabsIntent.intent)
                logger.d("Intent to launch the browser: ${customTabsIntent.intent}")

                logger.d("Launch Browser with URL: $url")
                customTabsIntent.launchUrl(this, Uri.parse(url))
            } catch (e: ActivityNotFoundException) {
                logger.e("Not activity to launch the browser", e)
                setResult(
                    RESULT_CANCELED,
                    Intent()
                        .putExtra(ERROR, ERROR_ACTIVITY_NOT_FOUND)
                        .putExtra(ERROR_MESSAGE, e.message)
                )
                finish()
            } catch (e: Exception) {
                logger.e("Exception launching the browser", e)
                setResult(
                    RESULT_CANCELED,
                    Intent()
                        .putExtra(ERROR, ERROR_OTHER)
                        .putExtra(ERROR_MESSAGE, e.message)
                )
                finish()
            }
        }
    }

    /**
     * This is called when a redirect happens from the CustomTab.
     * CustomTab -> Server -> Server response with redirect -> CustomTab
     * -> CustomTabActivity.onNewIntent -> CustomTabActivity.onResume
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}