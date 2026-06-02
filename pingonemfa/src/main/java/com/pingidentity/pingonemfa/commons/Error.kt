/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.commons

/**
 * Structured representation of a single PingOne SDK error.
 *
 * Instances are produced by [com.pingidentity.pingonemfa.util.ErrorParser] from the native type
 * and exposed through [PingOneMFAException.internalErrorsList].
 *
 * @property code Numeric error code returned by the PingOne MFA native SDK. For details on returned error codes, see the native SDK documentation:
 * https://pingidentity.github.io/pingone-mobile-sdk-android/-ping-one%20-m-f-a%20-android%20-s-d-k/com.pingidentity.pingidsdkv2.error/-ping-one-s-d-k-error-type/index.html
 * @property message Human-readable error message returned by the native SDK.
 * @property userInfo Additional diagnostic key/value pairs returned by the server.
 * Intended for use for logging or debugging. The map may be empty if the server did not include
 * additional context.
 */
data class Error(
    val code: Int?,
    val message: String?,
    val userInfo: Map<String, String> = emptyMap()
)