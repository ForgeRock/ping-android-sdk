/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push.exception

import com.pingidentity.mfa.commons.exception.MfaException

/**
 * Exception thrown when attempting to operate on a push notification that does not exist.
 * 
 * This exception is thrown when a notification cannot be found in storage, which may occur if:
 * - The notification was already processed and removed
 * - The notification was cleaned up due to expiration
 * - The notification ID is invalid or corrupted
 * 
 * In async scenarios where multiple operations may act on the same notification,
 * this exception should be handled gracefully by refreshing the notification list
 * or showing a "notification no longer available" message to the user.
 * 
 * @param notificationId The ID of the notification that was not found.
 * @param cause The underlying cause of the exception.
 */
class NotificationNotFoundException(
    val notificationId: String,
    cause: Throwable? = null
) : MfaException(
    message = "Notification not found: $notificationId",
    cause = cause
)
