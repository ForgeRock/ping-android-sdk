/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.journey

import com.facebook.login.LoginManager
import com.pingidentity.idp.facebook.FacebookLoginManager

class FacebookHandler : IdpHandler {
    override var tokenType: String = "access_token"

    override suspend fun authorize(client: IdpClient): IdpResult {
        LoginManager.getInstance().logOut()

        val result = FacebookLoginManager.performFacebookLogin(client.scopes)
        return IdpResult(result.accessToken.token)

    }
}