/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.facebook

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.facebook.login.LoginManager
import com.pingidentity.android.ContextProvider

/**
 * An activity for handling Facebook login.
 */
class FacebookActivity : ComponentActivity() {

    /**
     * Called when the activity is starting.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve the scopes from the intent
        val scopes = intent.getStringArrayListExtra(EXTRA_SCOPES) ?: listOf("public_profile", "email")

        val launcher = registerForActivityResult(
            LoginManager.getInstance()
                .createLogInActivityResultContract(FacebookLoginManager.callbackManager)
        ) {
            finish()
        }

        launcher.launch(scopes)
    }

    /**
     * Unregisters the Facebook callback manager when the activity is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        FacebookLoginManager.deregister()
    }

    companion object {

        private const val EXTRA_SCOPES = "EXTRA_SCOPES"

        /**
         * Starts the Facebook login activity with the given scopes.
         *
         * @param scopes The list of scopes required for the login.
         */
        fun login(scopes: List<String>) {
            val intent = Intent(ContextProvider.context, FacebookActivity::class.java).apply {
                flags = FLAG_ACTIVITY_NEW_TASK
                putStringArrayListExtra(EXTRA_SCOPES, ArrayList(scopes))
            }
            ContextProvider.context.startActivity(intent)
        }
    }
}