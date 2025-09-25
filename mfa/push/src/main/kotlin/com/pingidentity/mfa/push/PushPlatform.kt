/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

/**
 * Enum representing different push notification platforms.
 * This enum is used to identify the source of a push notification.
 */
enum class PushPlatform {

    /**
     * Push notification from PingAM platform.
     */
    PING_AM,
    
    /**
     * Push notification from PingOne platform.
     */
    PING_ONE;
}
