/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pingidentity.android.ContextProvider
import com.pingidentity.mfa.commons.MfaConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service for handling Firebase Cloud Messaging push notifications.
 * This service receives FCM messages and processes them using the PushClient.
 */
class PushMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "PushMessagingService"
        private var pushClient: PushClient? = null
        
        /**
         * Set the PushClient instance to be used by the service.
         * This should be called when the PushClient is initialized.
         *
         * @param client The initialized PushClient instance.
         */
        @JvmStatic
        fun setPushClient(client: PushClient?) {
            pushClient = client
        }
    }
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Called when a new FCM token is generated.
     * This implementation just logs the event as FCM token is no longer stored
     * in the PushCredential class per the design requirements.
     *
     * @param token The new FCM token.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        
        Log.d(TAG, "New FCM token received - no action needed per design requirements")
    }

    /**
     * Called when a new message is received.
     * Processes the push notification message using the PushClient.
     *
     * @param remoteMessage The received remote message.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "Message received from: ${remoteMessage.from}")
        
        // Check if the message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            
            // Process the push notification
            val client = pushClient
            if (client == null) {
                Log.e(TAG, "PushClient not initialized")
                
                // Initialize a new client if needed
                try {
                    // Set up the application context
                    ContextProvider::class.java.getDeclaredField("context").apply {
                        isAccessible = true
                        set(ContextProvider, applicationContext)
                    }
                    
                    val newClient = PushClient.create(
                        MfaConfiguration.Builder()
                            .build()
                    )
                    newClient.initialize()
                    
                    val notification = newClient.processPushMessage(remoteMessage.data)
                    Log.d(TAG, "Push notification processed: ${notification != null}")
                    
                    // Clean up
                    newClient.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process push notification", e)
                }
            } else {
                // Use the existing client
                try {
                    val notification = client.processPushMessage(remoteMessage.data)
                    Log.d(TAG, "Push notification processed: ${notification != null}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process push notification", e)
                }
            }
        }
    }
}
