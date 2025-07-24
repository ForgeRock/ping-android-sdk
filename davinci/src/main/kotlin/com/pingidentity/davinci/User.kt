/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.oidc.User
import com.pingidentity.oidc.module.oidcUser
import com.pingidentity.orchestrate.module.hasCookies

/**
 * Function to retrieve the user.
 *
 * If cookies are available, it prepares a new user and returns it.
 * If no cookies are available, it returns null.
 *
 * @return The user if found, otherwise null.
 */
suspend fun DaVinci.user(): User? {
    return this.oidcUser().takeIf { hasCookies() }
}