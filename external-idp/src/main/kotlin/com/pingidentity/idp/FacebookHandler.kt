/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp

import com.facebook.login.LoginManager
import com.pingidentity.idp.facebook.FacebookLoginManager

internal class FacebookHandler : IdpHandler {
    override var tokenType: String = "access_token"

    override suspend fun authorize(idpClient: IdpClient): IdpResult {
        try {
            Class.forName("com.facebook.login.LoginManager")
        } catch (e: ClassNotFoundException) {
            throw UnsupportedIdPException("Facebook SDK is not available.")
        }

        LoginManager.getInstance().logOut()

        val result = FacebookLoginManager.performFacebookLogin(idpClient.scopes)
        return IdpResult(result.accessToken.token)

    }
}