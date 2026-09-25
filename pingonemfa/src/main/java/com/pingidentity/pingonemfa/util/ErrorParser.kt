/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.util

import com.pingidentity.pingidsdkv2.PingOneSDKError
import com.pingidentity.pingonemfa.commons.Error

/*
 * Converts native [PingOneSDKError] objects from the `pingidsdkv2` AAR into the
 * wrapper [Error] type, so callers never need a direct dependency on the native SDK.
 */
internal object ErrorParser {

    /**
     * Converts a single [PingOneSDKError] into a one-element [List] of [Error].
     *
     * Returned as a list to keep the API consistent with [fromPingOneSDKErrors].
     * [PingOneSDKError.getUserInfo] may be null from the native SDK; it is normalized
     * to an empty map so callers never receive a null collection.
     */
    fun fromPingOneSDKError(error: PingOneSDKError?): List<Error>? {
        error ?: return null
        val errorList = mutableListOf<Error>()
        errorList.add(
            Error(
                code = error.code,
                message = error.message,
                userInfo = error.userInfo ?: emptyMap()
            )
        )
        return errorList
    }

    /**
     * Converts an array of [PingOneSDKError] objects into a flat [List] of [Error].
     *
     * Delegates to [fromPingOneSDKError] for each element so the conversion logic is
     * defined in one place.
     */
    fun fromPingOneSDKErrors(errors: Array<PingOneSDKError>?): List<Error>? {
        errors ?: return null
        val errorList = mutableListOf<Error>()
        errors.forEach { error ->
            fromPingOneSDKError(error)?.let { errorList.addAll(it) }
        }
        return errorList
    }
}