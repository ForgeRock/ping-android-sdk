/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.impl.RSASSAProvider
import com.nimbusds.jose.util.Base64URL
import java.security.Signature

/**
 * Custom JWS signer implementation for RSA-based signatures using a pre-configured [Signature] instance.
 *
 * This class wraps an Android Keystore [Signature] object and adapts it to the Nimbus JOSE + JWT
 * [JWSSigner] interface, enabling RSA signature operations (RS256, RS384, RS512) for JWT signing
 * with private keys stored in the Android Keystore.
 *
 * @property signature The [Signature] instance configured with the private key from Android Keystore.
 *                     This signature object should be initialized with the appropriate algorithm
 *                     (e.g., "SHA256withRSA") before being passed to this signer.
 */
internal class RSASASignatureSigner(private val signature: Signature) : RSASSAProvider(),
    JWSSigner {

    /**
     * Signs the given input data using the RSA signature algorithm.
     *
     * @param header The JWS header containing algorithm and other metadata.
     * @param signingInput The data to be signed (typically the JWS signing input).
     * @return The Base64URL-encoded signature.
     */
    override fun sign(header: JWSHeader, signingInput: ByteArray): Base64URL {
        signature.update(signingInput)
        return Base64URL.encode(signature.sign())
    }
}