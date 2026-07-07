/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data class representing the RFC 8628 device authorization response.
 *
 * @property deviceCode The device verification code.
 * @property userCode The end-user verification code.
 * @property verificationUri The end-user verification URI.
 * @property verificationUriComplete The end-user verification URI that includes the user code (optional).
 * @property expiresIn The lifetime in seconds of the device code and user code.
 * @property interval The minimum amount of time in seconds that the client should wait between polling requests.
 */
@Serializable
data class DeviceAuthorizationResponse(
    @SerialName("device_code")
    val deviceCode: String,
    @SerialName("user_code")
    val userCode: String,
    @SerialName("verification_uri")
    val verificationUri: String,
    @SerialName("verification_uri_complete")
    val verificationUriComplete: String? = null,
    @SerialName("expires_in")
    val expiresIn: Int,
    @SerialName("interval")
    val interval: Int = 5,
)
