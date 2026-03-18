/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push.exception

import com.pingidentity.mfa.commons.exception.MfaException

/**
 * Exception thrown when attempting to register a push credential without setting a device token.
 * 
 * This exception is thrown during credential registration when the device token has not been
 * set via [com.pingidentity.mfa.push.PushService.setDeviceToken]. The device token is required for the server to send
 * push notifications to the device.
 * 
 * To resolve this error, call [com.pingidentity.mfa.push.PushService.setDeviceToken] with a valid FCM or APNs token
 * before attempting to register a credential.
 * 
 * @param message The error message.
 * @param cause The underlying cause of the exception.
 */
class DeviceTokenMissingException(
    message: String = "Device token not set. Call setDeviceToken() before registering credentials.",
    cause: Throwable? = null
) : MfaException(message, cause)
