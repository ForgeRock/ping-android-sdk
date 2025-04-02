/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.journeyapp.userprofile

import com.pingidentity.oidc.OidcError
import kotlinx.serialization.json.JsonObject

data class UserProfileState(
    var user: JsonObject? = null,
    var error: OidcError? = null,
)
