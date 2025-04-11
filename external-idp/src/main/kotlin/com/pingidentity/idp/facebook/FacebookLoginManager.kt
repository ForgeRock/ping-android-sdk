/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.facebook

import com.facebook.CallbackManager
import com.facebook.CallbackManager.Factory.create
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.pingidentity.idp.IdpCanceledException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * An object for managing Facebook callback registration and de-register.
 */
internal object FacebookLoginManager {
    var callbackManager: CallbackManager? = null

    /**
     * Registers the given callback manager.
     *
     * @param callbackManager The callback manager to register.
     */
    fun register(callbackManager: CallbackManager) {
        FacebookLoginManager.callbackManager = callbackManager
    }

    /**
     * Unregisters the current callback manager.
     */
    fun deregister() {
        callbackManager?.let {
            LoginManager.getInstance().unregisterCallback(it)
        }
        callbackManager = null
    }

    /**
     * Performs the Facebook login process.
     *
     * @param scopes The list of scopes required for the login.
     * @return A [LoginResult] object containing the login result.
     */
    suspend fun performFacebookLogin(scopes: List<String>): LoginResult =
        withContext(Dispatchers.Default) {
            suspendCancellableCoroutine { continuation ->
                val callbackManager = create()
                register(callbackManager)
                LoginManager.getInstance()
                    .registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
                        override fun onSuccess(result: LoginResult) {
                            continuation.resume(result) // Resume coroutine on success
                        }

                        override fun onCancel() {
                            continuation.resumeWithException(IdpCanceledException())
                        }

                        override fun onError(error: FacebookException) {
                            continuation.resumeWithException(error) // Resume with exception on error
                        }
                    })
                FacebookActivity.login(scopes)
            }
        }
}