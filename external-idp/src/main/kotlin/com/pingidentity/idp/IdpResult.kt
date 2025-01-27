/*
 * Copyright (c) 2024 - 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp

/**
 * Data class representing the result of an IDP authorization.
 *
 * @param token The token.
 * @param additionalParameters The additional parameters.
 */
data class IdpResult(val token: String, val additionalParameters: Map<String, String> = emptyMap())
