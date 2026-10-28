/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import android.net.Uri
import com.pingidentity.oidc.exception.AuthorizeException
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import androidx.core.net.toUri

private const val STATE_LENGTH_BYTES = 32

/**
 * Generates [numBytes] random bytes encoded as Base64 URL-safe without padding,
 * following the same encoding as https://tools.ietf.org/html/rfc7636#section-4.1.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun randomBase64UrlSafe(numBytes: Int): String {
    val bytes = ByteArray(numBytes)
    Random.nextBytes(bytes)
    return Base64.UrlSafe.encode(bytes).trimEnd('=')
}

/**
 * Generates a random, URL-safe `state` value for an authorization request
 * (RFC 6749 §10.12 CSRF protection).
 *
 * The value is 32 random bytes encoded as Base64 URL-safe without padding (43 characters),
 * following the same encoding as [Pkce].
 *
 * @return The generated state.
 */
internal fun generateState(): String = randomBase64UrlSafe(STATE_LENGTH_BYTES)

/**
 * Verifies the `state` returned by the authorization server against the expected value
 * (RFC 6749 §10.12 CSRF protection). The verification must run before anything else in
 * the authorization response is trusted.
 *
 * The returned state is read from the query string, falling back to the fragment
 * (parsed as a query string) for `response_mode=fragment` responses.
 *
 * @param uri The redirect URI returned by the authorization server.
 * @param expected The state that was sent with the authorization request, or `null` when
 * no state was recorded (the verification is skipped).
 * @throws AuthorizeException if the state is missing or does not match the expected value.
 */
internal fun validateState(uri: Uri, expected: String?) {
    if (expected == null) {
        return
    }
    val returned = uri.getQueryParameter(Constants.STATE)
        ?: uri.encodedFragment?.let { fragment ->
            "?$fragment".toUri().getQueryParameter(Constants.STATE)
        }
        ?: throw AuthorizeException("The authorization response did not include the state parameter.")

    // Constant-time comparison so the check does not leak the expected value by timing.
    if (!MessageDigest.isEqual(returned.toByteArray(), expected.toByteArray())) {
        throw AuthorizeException("State mismatch in the authorization response. Possible CSRF attack.")
    }
}
