/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.browser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.pingidentity.browser.BrowserLauncher.logger

/**
 * Activity responsible for launching and handling Chrome Custom Tabs.
 *
 * This activity serves as the entry and exit point for Custom Tabs based authentication:
 * 1. It launches the Custom Tab with the provided URL
 * 2. It receives the redirect back from the authentication server
 * 3. It processes the result and passes it back to the calling component
 *
 * The activity handles various scenarios including:
 * - Successful authentication completion
 * - User cancellation
 * - Browser not found errors
 * - Other exceptions during the browser launch
 *
 * Custom Tabs provide a better user experience than WebView by:
 * - Faster loading through pre-warming and shared cache
 * - Retaining the user's logged-in state
 * - More trusted UI for authentication
 * - Shared security features with the browser
 */
internal class CustomTabActivity : ComponentActivity() {

    companion object {
        // Intent extras and result codes
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
            // Already launched the browser, don't do it again to prevent duplicate launches
            // which could happen on configuration changes
            intent.removeExtra(URL)
        }
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * This method is triggered in two main scenarios:
     * 1. Initial launch - The activity needs to launch the Custom Tab
     * 2. Returning from Custom Tab - The activity needs to process the result
     *
     * For redirects, the onResume will be invoked when exiting the Custom Tab:
     * - When closing the Custom Tab manually (cancellation)
     * - When a redirect from the auth server brings control back to this activity
     *
     * After receiving the exit action from Custom Tab, the method uses [setResult]
     * to notify the caller who started this activity.
     */
    override fun onResume() {
        super.onResume()
        // Retrieve the URL from the intent and launch the CustomTabsIntent.
        val url = intent.extras?.getString(URL)

        if (intent.data != null) {
            // We have received a redirect URI from the browser - authentication completed
            logger.d("Redirect Uri received: ${intent.data}")
            setResult(RESULT_OK, Intent().setData(intent.data))
            finish()
        } else if (url == null) {
            // No URL to launch and no redirect data - likely returning from a cancelled session
            logger.d("Resuming without URL")
            setResult(RESULT_CANCELED, Intent().putExtra(ERROR, ERROR_CANCELED))
            finish()
        } else {
            // Initial launch with a URL - launch the browser
            try {
                // Remove the URL from the intent to avoid launching the browser again
                // if we get another onResume (e.g., due to configuration change)
                intent.removeExtra(URL)

                // Configure and launch the CustomTabsIntent with the provided URL
                val builder = CustomTabsIntent.Builder()
                BrowserLauncher.customTabsCustomizer(builder)
                val customTabsIntent = builder.build()
                BrowserLauncher.intentCustomizer(customTabsIntent.intent)
                logger.d("Intent to launch the browser: ${customTabsIntent.intent}")

                logger.d("Launch Browser with URL: $url")
                customTabsIntent.launchUrl(this, url.toUri())
            } catch (e: ActivityNotFoundException) {
                // No browser app available to handle the intent
                logger.e("No activity to launch the browser", e)
                setResult(
                    RESULT_CANCELED,
                    Intent()
                        .putExtra(ERROR, ERROR_ACTIVITY_NOT_FOUND)
                        .putExtra(ERROR_MESSAGE, e.message)
                )
                finish()
            } catch (e: Exception) {
                // Other unexpected errors
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
     * Called when a new intent is received by this activity.
     *
     * This is crucial for handling redirects from the Custom Tab back to this activity.
     * The flow typically is:
     * 1. Custom Tab launched from this activity
     * 2. User authenticates in Custom Tab
     * 3. Server redirects to the redirect URI
     * 4. Android routes this to our activity (this method is called)
     * 5. We then process the redirect in onResume
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}