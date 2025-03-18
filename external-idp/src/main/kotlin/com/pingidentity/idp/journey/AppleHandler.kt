/*
 * Copyright (c) 2024 - 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.journey

import android.net.Uri
import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.idp.IdpClient
import com.pingidentity.idp.IdpHandler
import com.pingidentity.idp.IdpResult
import java.net.URL

internal class AppleHandler : IdpHandler {

    override var tokenType: String = "authorization_code"

    override suspend fun authorize(idpClient: IdpClient): IdpResult {
        val request = Uri.parse("https://appleid.apple.com/auth/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", idpClient.clientId)
            .appendQueryParameter("redirect_uri", idpClient.redirectUri)
            .appendQueryParameter("response_mode", "form_post")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", idpClient.scopes.joinToString(" "))
            .appendQueryParameter("nonce", idpClient.nonce)
            .build()

        val result = BrowserLauncher.launch(URL(request.toString()))
        val uri = result.getOrThrow()

        return IdpResult(
            "form_post_entry",
            uri.queryParameterNames.associateWith { uri.getQueryParameter(it)!! }
        )

    }
}