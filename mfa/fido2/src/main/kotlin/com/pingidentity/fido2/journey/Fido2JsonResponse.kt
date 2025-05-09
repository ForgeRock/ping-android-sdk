/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.journey

import kotlinx.serialization.Serializable

@Serializable
data class Fido2JsonResponse(val authenticatorAttachment: String, val legacyData: String)
