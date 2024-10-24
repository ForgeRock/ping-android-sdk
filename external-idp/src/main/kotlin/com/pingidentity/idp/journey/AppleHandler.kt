/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.journey

import android.net.Uri
import com.pingidentity.idp.browser.BrowserLauncherActivity
import java.net.URL

class AppleHandler : IdpHandler {
    override var tokenType: String = "authorization_code"

    override suspend fun authorize(client: IdpClient): IdpResult {
        val request = Uri.parse("https://appleid.apple.com/auth/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", client.clientId)
            .appendQueryParameter("redirect_uri", client.redirectUri)
            .appendQueryParameter("response_mode", "form_post")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", client.scopes.joinToString(" "))
            .appendQueryParameter("nonce", client.nonce)
            .build()

        val result = BrowserLauncherActivity.launch(URL(request.toString()))
        val uri = result.getOrThrow()

        return IdpResult(
            "form_post_entry",
            uri.queryParameterNames.associateWith { uri.getQueryParameter(it)!! }
        )

    }
}