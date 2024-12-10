/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Calendar

/**
 * Data class representing an OIDC token.
 *
 * @property accessToken The access token.
 * @property tokenType The type of the token.
 * @property scope The scope of the token.
 * @property expiresIn The duration in seconds for which the token is valid.
 * @property refreshToken The refresh token.
 * @property idToken The ID token.
 * @property expireAt The timestamp when the token expires.
 */
@Serializable
data class Token(
    @SerialName("access_token")
    val accessToken: String = "",
    @SerialName("token_type")
    val tokenType: String? = null,
    @SerialName("scope")
    val scope: String? = null,
    @SerialName("expires_in")
    val expiresIn: Long = 0,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("id_token")
    val idToken: String? = null,
    @SerialName("expireAt")
    internal val expireAt: Long =  now() + expiresIn,
) {
    /**
     * Checks if the token is expired.
     */
    val isExpired: Boolean
        get() = now() >= expireAt

    /**
     * Checks if the token is expired considering a threshold.
     *
     * @param threshold The threshold in seconds.
     * @return True if the token is expired considering the threshold, false otherwise.
     */
    fun isExpired(threshold: Long): Boolean = now() >= expireAt - threshold

    companion object {
        /**
         * Gets the current time in seconds.
         *
         * @return The current time in seconds.
         */
        internal fun now() = Calendar.getInstance().time.time / 1000
    }
}