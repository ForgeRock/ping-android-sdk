/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

/**
 * Sealed class representing the status of a device authorization flow (RFC 8628).
 */
sealed class DeviceFlowStatus {

    /**
     * The device authorization request succeeded and the user code has been obtained.
     *
     * @property response The device authorization response containing the user code, verification URI, etc.
     */
    data class Started(val response: DeviceAuthorizationResponse) : DeviceFlowStatus()

    /**
     * The client is polling the token endpoint waiting for the user to authorize.
     *
     * @property pollCount The number of polling attempts made so far.
     * @property pollInterval The current polling interval in seconds.
     * @property nextPollAt The wall-clock time (epoch millis) of the next scheduled poll.
     */
    data class Polling(
        val pollCount: Int,
        val pollInterval: Int,
        val nextPollAt: Long,
    ) : DeviceFlowStatus()

    /**
     * The device flow completed successfully and an access token has been obtained.
     *
     * @property user The authenticated user.
     */
    data class Success(val user: User) : DeviceFlowStatus()

    /**
     * The device code expired before the user authorized the request.
     */
    data object Expired : DeviceFlowStatus()

    /**
     * The user explicitly denied the authorization request.
     */
    data object AccessDenied : DeviceFlowStatus()

    /**
     * An unrecoverable error occurred during the device flow.
     *
     * @property exception The exception that caused the failure.
     */
    data class Failure(val exception: Exception) : DeviceFlowStatus()
}
