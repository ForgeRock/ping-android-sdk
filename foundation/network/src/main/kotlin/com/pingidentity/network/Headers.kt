/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

/**
 * Constants for standard headers.
 */
object Headers {
    /**
     * Header indicating what type of app is making the request.
     */
    const val X_REQUESTED_WITH = "x-requested-with"
    
    /**
     * Header indicating the platform of the app making the request.
     */
    const val X_REQUESTED_PLATFORM = "x-requested-platform"
    
    /**
     * Value for [X_REQUESTED_WITH] for Android apps.
     */
    const val ANDROID_VALUE = "android"
    
    /**
     * Value for [X_REQUESTED_PLATFORM] for Android platform.
     */
    const val ANDROID_PLATFORM = "android"
}
