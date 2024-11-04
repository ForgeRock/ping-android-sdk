/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent

/**
 * This activity is responsible for launching the CustomTabsIntent with the provided URL.
 */
class CustomTabActivity : ComponentActivity() {

    companion object {
        /**
         * A customizer for CustomTabsIntent.Builder.
         */
        internal var customTabsCustomizer: CustomTabsIntent.Builder.() -> Unit = {}
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
            setResult(RESULT_OK, Intent().setData(intent.data))
            finish()
        } else if (url == null) {
            setResult(RESULT_CANCELED)
            finish()
        } else {
            // Remove the URL from the intent to avoid launching the browser again.
            intent.removeExtra(URL)
            // Launch the CustomTabsIntent with the provided URL.
            val builder = CustomTabsIntent.Builder()
            builder.customTabsCustomizer()
            val customTabsIntent = builder.build()
            // What if ActivityNotFound?
            customTabsIntent.launchUrl(this, Uri.parse(url))
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