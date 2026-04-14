/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push.exception

import com.pingidentity.mfa.commons.exception.MfaException

/**
 * Exception thrown when attempting to approve or deny an expired push notification.
 * 
 * This exception is thrown when a notification has exceeded its time-to-live (TTL)
 * and the user attempts to approve or deny it. The notification is considered expired
 * when the elapsed time since creation exceeds the TTL value.
 * 
 * @param notificationId The ID of the expired notification.
 * @param ttlSeconds The time-to-live in seconds that was configured for the notification.
 * @param cause The underlying cause of the exception.
 */
class NotificationExpiredException(
    val notificationId: String,
    val ttlSeconds: Int,
    cause: Throwable? = null
) : MfaException(
    message = "Notification '$notificationId' has expired (TTL: ${ttlSeconds}s)",
    cause = cause
)
