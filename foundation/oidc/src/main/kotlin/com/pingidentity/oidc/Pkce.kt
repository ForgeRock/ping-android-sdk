/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * Data class for PKCE (Proof Key for Code Exchange).
 * @property codeVerifier The code verifier for the PKCE.
 * @property codeChallenge The code challenge for the PKCE.
 * @property codeChallengeMethod The code challenge method for the PKCE.
 */
@OptIn(ExperimentalEncodingApi::class)
data class Pkce(
    val codeVerifier: String,
    val codeChallenge: String,
    val codeChallengeMethod: String,
) {
    companion object {
        /**
         * Generates a new PKCE.
         * @return A new PKCE.
         */
        fun generate(): Pkce {
            val codeVerifier = generateCodeVerifier()
            val codeChallenge = generateCodeChallenge(codeVerifier)
            return Pkce(codeVerifier, codeChallenge, "S256")
        }

        /**
         * Generates a new code verifier for the PKCE.
         * @return A new code verifier.
         */
        private fun generateCodeVerifier(): String {
            val bytes = ByteArray(64)
            Random.nextBytes(bytes)
            return Base64.UrlSafe.encode(bytes).trimEnd('=') // remove padding as per https://tools.ietf.org/html/rfc7636#section-4.1
        }

        /**
         * Generates a new code challenge for the PKCE.
         * @param codeVerifier The code verifier for the PKCE.
         * @return A new code challenge.
         */
        private fun generateCodeChallenge(codeVerifier: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray())
            return Base64.UrlSafe.encode(bytes).trimEnd('=') // remove padding as per https://tools.ietf.org/html/rfc7636#section-4.1
        }
    }
}