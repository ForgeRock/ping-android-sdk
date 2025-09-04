/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.journey

import android.net.Uri
import androidx.core.net.toUri
import com.pingidentity.browser.BrowserLauncher
import com.pingidentity.idp.IdpClient
import com.pingidentity.idp.IdpHandler
import com.pingidentity.idp.IdpResult
import java.net.URL

internal class AppleHandler(private val redirectUri: Uri) : IdpHandler {

    override var tokenType: String = "authorization_code"

    override suspend fun authorize(idpClient: IdpClient): IdpResult {
        val request = "https://appleid.apple.com/auth/authorize".toUri()
            .buildUpon()
            .appendQueryParameter("client_id", idpClient.clientId)
            .appendQueryParameter("redirect_uri", idpClient.redirectUri)
            .appendQueryParameter("response_mode", "form_post")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", idpClient.scopes.joinToString(" "))
            .appendQueryParameter("nonce", idpClient.nonce)
            .build()

        val result = BrowserLauncher.launch(URL(request.toString()), redirectUri)
        val uri = result.getOrThrow()

        return IdpResult(
            "form_post_entry",
            uri.queryParameterNames.associateWith { uri.getQueryParameter(it)!! }
        )

    }
}