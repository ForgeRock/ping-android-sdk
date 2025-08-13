/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

/**
 * Defines contract for managing push device enrollment and handling
 * notification messages from a specific platform.
 */
interface PushHandler {
    
    /**
     * Check if this handler can process the given message.
     * This method should inspect the message data to determine if it can be handled by this
     * handler. It should return true if the handler can process the message, and false otherwise.
     *
     * @param messageData The message data to check. Usually a map containing the raw data from the push service.
     *                    The structure of this map depends on the push service and the platform. In Firebase,
     *                    it is obtained from the `RemoteMessage` object.
     * @return True if this handler can process the message, false otherwise.
     */
    fun canHandle(messageData: Map<String, Any>): Boolean
    
    /**
     * Parse the message data received from the push service.
     * It should extract relevant information such as notification type, message content, and any
     * additional parameters. It should return a map of parsed data that maps to the expected structure
     * for the [PushNotification]. Any required fields should be included in the map. Additional fields
     * can also be included for further processing.
     *
     * @param messageData The message data to parse. Usually a map containing the raw data from the push service.
     *                    The structure of this map depends on the push service and the platform. In Firebase,
     *                    it is obtained from the `RemoteMessage` object.
     * @return A map of parsed data.
     */
    fun parseMessage(messageData: Map<String, Any>): Map<String, Any>
    
    /**
     * Send to the server an approval response for a notification that was received.
     * How the approval is sent depends on the platform and the implementation of this handler.
     * The parameters may include additional information or any other relevant data that the
     * server needs to process the approval.
     *
     * @param credential The credential to use for the response.
     * @param notification The notification to approve.
     * @param params Additional parameters.
     * @return True if the approval was sent successfully, false otherwise.
     */
    suspend fun sendApproval(
        credential: PushCredential,
        notification: PushNotification,
        params: Map<String, Any>
    ): Boolean
    
    /**
     * Send a denial response for a notification.
     * How the denial is sent depends on the platform and the implementation of this handler.
     * The parameters may include additional information or any other relevant data that the
     * server needs to process the denial.
     *
     * @param credential The credential to use for the response.
     * @param notification The notification to deny.
     * @param params Additional parameters.
     * @return True if the denial was sent successfully, false otherwise.
     */
    suspend fun sendDenial(
        credential: PushCredential,
        notification: PushNotification,
        params: Map<String, Any>
    ): Boolean

    /**
     * Register or update the device token. This is typically called when the
     * device token changes.
     *
     * @param credential The credential to register or update.
     * @param deviceToken The device token.
     * @param params Additional parameters.
     * @return True if was successful, false otherwise.
     */
    suspend fun setDeviceToken(
        credential: PushCredential,
        deviceToken: String,
        params: Map<String, Any>
    ): Boolean

    /**
     * Register a new push credential with the server, if this is required by the platform.
     * The parameters may include additional information or any other relevant data that the
     * server needs to process the registration.
     *
     * @param credential The credential to register.
     * @param params Additional parameters for registration including messageId.
     * @return True if the registration was successful, false otherwise.
     */
    suspend fun register(
        credential: PushCredential,
        params: Map<String, Any>,
    ): Boolean
}
