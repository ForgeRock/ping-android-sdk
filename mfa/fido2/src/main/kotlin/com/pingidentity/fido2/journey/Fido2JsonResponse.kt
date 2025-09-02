/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2.journey

import kotlinx.serialization.Serializable

/**
 * Data class representing a FIDO2 response in JSON format for Journey workflows.
 *
 * This class encapsulates the response data from FIDO2 operations when the server
 * supports JSON-formatted responses. It provides both modern structured data and
 * legacy compatibility through the legacyData field.
 *
 * @property authenticatorAttachment The type of authenticator used (e.g., "platform", "cross-platform")
 * @property legacyData The legacy string-formatted data for backward compatibility with older servers
 */
@Serializable
data class Fido2JsonResponse(val authenticatorAttachment: String, val legacyData: String)
