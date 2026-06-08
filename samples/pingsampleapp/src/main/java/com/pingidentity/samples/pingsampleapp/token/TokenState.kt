/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.token

import com.pingidentity.oidc.Token
import com.pingidentity.oidc.OidcError

enum class TokenType {
    JOURNEY,
    DAVINCI,
    OIDC,
    AUTH_GRANT
}

data class TokenState(
    var selectedTab: TokenType = TokenType.JOURNEY,
    var journeyToken: Token? = null,
    var journeyError: OidcError? = null,
    var daVinciToken: Token? = null,
    var daVinciError: OidcError? = null,
    var oidcToken: Token? = null,
    var oidcError: OidcError? = null,
    var authGrantToken: Token? = null,
    var authGrantError: OidcError? = null,
)
