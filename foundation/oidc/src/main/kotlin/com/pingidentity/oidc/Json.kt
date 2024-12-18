/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import kotlinx.serialization.json.Json

internal val json: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
