/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import android.net.Uri
import com.pingidentity.oidc.exception.AuthorizeException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val STATE_LENGTH_BYTES = 32
private const val ERROR = "error"
private const val ERROR_DESCRIPTION = "error_description"
private const val ERROR_URI = "error_uri"

// Cryptographically secure source for the state and PKCE verifier: the state must be
// unguessable by attackers for the CSRF check to hold (RFC 6749).
private val secureRandom = SecureRandom()

/**
 * Generates [numBytes] random bytes encoded as Base64 URL-safe without padding,
 * following the same encoding as https://tools.ietf.org/html/rfc7636#section-4.1.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun randomBase64UrlSafe(numBytes: Int): String {
    val bytes = ByteArray(numBytes)
    secureRandom.nextBytes(bytes)
    return Base64.UrlSafe.encode(bytes).trimEnd('=')
}

/**
 * Generates a random, URL-safe `state` value for an authorization request
 * (RFC 6749 CSRF protection).
 *
 * The value is 32 random bytes encoded as Base64 URL-safe without padding (43 characters),
 * following the same encoding as [Pkce].
 *
 * @return The generated state.
 */
internal fun generateState(): String = randomBase64UrlSafe(STATE_LENGTH_BYTES)

/**
 * Reads query parameter [name] from [uri]'s query string, falling back to its fragment
 * (parsed as a query string) for `response_mode=fragment` responses. The fragment is
 * parsed behind a synthetic `https://x/` authority so values containing `:` or `//`
 * (e.g. an `error_uri`) are not mistaken for a URI scheme.
 */
private fun Uri.queryOrFragmentParameter(name: String): String? =
    getQueryParameter(name) ?: encodedFragment?.let { fragment ->
        Uri.parse("https://x/?$fragment").getQueryParameter(name)
    }

/**
 * Verifies the `state` returned by the authorization server against the expected value
 * (RFC 6749 CSRF protection). The verification must run before anything else in
 * the authorization response is trusted.
 *
 * The returned state is read from the query string, falling back to the fragment
 * (parsed as a query string) for `response_mode=fragment` responses.
 *
 * A `null` [expected] means no state was recorded for the flow — since a state is now
 * always sent with web authorization requests, this indicates an SDK misuse and fails.
 *
 * @param uri The redirect URI returned by the authorization server.
 * @param expected The state that was sent with the authorization request.
 * @throws AuthorizeException if no expected state was recorded, or the state is missing
 * or does not match the expected value.
 */
internal fun validateState(uri: Uri, expected: String?) {
    if (expected == null) {
        throw AuthorizeException("No expected state was recorded for the authorization flow.")
    }
    val returned = uri.queryOrFragmentParameter(Constants.STATE)
        ?: throw AuthorizeException("The authorization response did not include the state parameter.")

    // Constant-time comparison so the check does not leak the expected value by timing.
    if (!MessageDigest.isEqual(returned.toByteArray(), expected.toByteArray())) {
        throw AuthorizeException("State mismatch in the authorization response. Possible CSRF attack.")
    }
}

/**
 * Extracts the OAuth 2.0 authorization error from an error redirect (RFC 6749):
 * the `error` code with the optional `error_description` and `error_uri`, each read from
 * the query string, falling back to the fragment.
 *
 * @param uri The redirect URI returned by the authorization server.
 * @return A human-readable message carrying the server's error code and description,
 * or `null` when the redirect is not an error response.
 */
internal fun extractOAuthError(uri: Uri): String? {
    val error = uri.queryOrFragmentParameter(ERROR) ?: return null
    val description = uri.queryOrFragmentParameter(ERROR_DESCRIPTION)
    val errorUri = uri.queryOrFragmentParameter(ERROR_URI)
    return buildString {
        append("The authorization server returned an error: ").append(error)
        description?.let { append(" — ").append(it) }
        errorUri?.let { append(" (see ").append(it).append(")") }
    }
}
