/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import com.pingidentity.mfa.commons.json

/**
 * Class that contains OTP code information, including the actual code and validity details.
 *
 * @property code The generated OTP code.
 * @property timeRemaining For TOTP, the time remaining in seconds before the code expires.
 * For HOTP, this will be -1.
 * @property counter For HOTP, the current counter value after code generation.
 * For TOTP, this will be -1.
 * @property progress For TOTP, a value from 0.0 to 1.0 indicating progress through the time window.
 * For HOTP, this will be 0.0.
 * @property totalPeriod For TOTP, the total validity period in seconds.
 * For HOTP, this will be 0.
 */
@Serializable
data class OathCodeInfo(
    val code: String,
    val timeRemaining: Int, // seconds for TOTP, -1 for HOTP
    val counter: Long, // counter value for HOTP, -1 for TOTP
    val progress: Double, // 0.0 to 1.0 for TOTP, 0.0 for HOTP
    val totalPeriod: Int // total period in seconds for TOTP, 0 for HOTP
) {

    companion object {
        /**
         * Create an instance for a TOTP code.
         *
         * @param code The generated OTP code.
         * @param timeRemaining The time remaining in seconds before the code expires.
         * @param totalPeriod The total validity period in seconds.
         * @return An OathCodeInfo instance for TOTP.
         */
        @JvmStatic
        fun forTotp(code: String, timeRemaining: Int, totalPeriod: Int): OathCodeInfo {
            val progress = if (totalPeriod > 0) {
                1.0 - (timeRemaining.toDouble() / totalPeriod)
            } else {
                0.0
            }
            return OathCodeInfo(
                code = code,
                timeRemaining = timeRemaining,
                counter = -1,
                progress = progress,
                totalPeriod = totalPeriod
            )
        }

        /**
         * Create an instance for a HOTP code.
         *
         * @param code The generated OTP code.
         * @param counter The counter value after code generation.
         * @return An OathCodeInfo instance for HOTP.
         */
        @JvmStatic
        fun forHotp(code: String, counter: Long): OathCodeInfo {
            return OathCodeInfo(
                code = code,
                timeRemaining = -1,
                counter = counter,
                progress = 0.0,
                totalPeriod = 0
            )
        }
        
        /**
         * Create an OathCodeInfo from a JSON string.
         *
         * @param jsonString The JSON string.
         * @return An OathCodeInfo.
         * @throws kotlinx.serialization.SerializationException if the JSON is invalid.
         */
        @JvmStatic
        fun fromJson(jsonString: String): OathCodeInfo {
            return json.decodeFromString(jsonString)
        }
    }

    /**
     * Converts this code info to a JSON string representation.
     *
     * @return A JSON string representing this code info.
     */
    fun toJson(): String {
        return json.encodeToString(this)
    }
}
