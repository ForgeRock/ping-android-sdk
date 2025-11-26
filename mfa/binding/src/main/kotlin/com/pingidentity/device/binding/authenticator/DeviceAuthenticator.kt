/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricPrompt.CryptoObject
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm.parse
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.util.Base64
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.binding.CryptoKey
import com.pingidentity.device.binding.UserKey
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Date

private const val ANDROID_VERSION = "android-version"
internal const val CHALLENGE = "challenge"
private const val PLATFORM = "platform"

/**
 * Interface for device authenticators that handle cryptographic operations for device binding.
 * Implementations provide device-specific authentication mechanisms such as biometric authentication,
 * PIN-based authentication, or hardware security modules.
 */
interface DeviceAuthenticator {

    /**
     * The type of device binding authentication this authenticator provides.
     */
    val type: DeviceBindingAuthenticationType

    /**
     * Registers a new key pair for device binding with optional attestation.
     *
     * @param context The Android context for cryptographic operations
     * @param attestation The attestation type to include in the key registration
     * @return A Result containing the generated KeyPair on success, or an error on failure
     */
    suspend fun register(
        context: Context = ContextProvider.context,
        attestation: Attestation
    ): Result<KeyPair>

    /**
     * Authenticates the user and retrieves the private key for signing operations.
     *
     * @param context The Android context for authentication operations
     * @return A Result containing a Pair of the PrivateKey and optional CryptoObject on success,
     *         or an error on failure
     */
    suspend fun authenticate(context: Context = ContextProvider.context): Result<Pair<PrivateKey, CryptoObject?>>

    /**
     * Checks if this authenticator is supported on the current device.
     *
     * @param context The Android context to check device capabilities
     * @param attestation The attestation type to check support for
     * @return true if the authenticator is supported, false otherwise
     */
    fun isSupported(context: Context, attestation: Attestation = Attestation.None) = true

    /**
     * Deletes all keys associated with this authenticator from the device.
     */
    suspend fun deleteKeys()

    /**
     * Signs a JWT using the provided signing parameters and key pair.
     * Creates a signed JWT with standard claims including platform information and challenge.
     *
     * @param params The signing parameters containing key pair, algorithm, and claims data
     * @return A serialized signed JWT string
     */
    fun sign(params: SigningParameters): String {
        val builder = RSAKey.Builder(params.keyPair.publicKey)
            .keyUse(KeyUse.SIGNATURE)
            .keyID(params.kid)
            .algorithm(parse(params.algorithm))
        if (params.attestation !is Attestation.None) {
            builder.x509CertChain(getCertificateChain(params.userId))
        }
        val jwk = builder.build()
        val signedJWT = SignedJWT(
            JWSHeader.Builder(parse(params.algorithm))
                .keyID(params.kid).jwk(jwk).build(),
            JWTClaimsSet.Builder().subject(params.userId)
                .issuer(params.context.packageName)
                .expirationTime(Date.from(params.expiration))
                .issueTime(Date.from(params.issueTime))
                .notBeforeTime(Date.from(params.notBeforeTime))
                .claim(PLATFORM, "android")
                .claim(ANDROID_VERSION, Build.VERSION.SDK_INT)
                .claim(CHALLENGE, params.challenge).build()
        )
        signedJWT.sign(params.keyPair.privateKey, params.signature)
        return signedJWT.serialize()
    }

    /**
     * Retrieves the certificate chain for the given user ID and converts it to Base64 format.
     *
     * @param userId The user ID to retrieve the certificate chain for
     * @return A list of Base64-encoded certificates
     */
    private fun getCertificateChain(userId: String): List<Base64> {
        val chain = CryptoKey(userId).certificateChains
        return chain.map {
            Base64.encode(it.encoded)
        }.toList()
    }

    /**
     * Signs a JWT using user key signing parameters with custom claims support.
     * Creates a signed JWT with the specified user key and custom claims.
     *
     * @param params The user key signing parameters containing user key, algorithm, and custom claims
     * @return A serialized signed JWT string
     */
    fun sign(params: UserKeySigningParameters): String {
        val claimsSet = JWTClaimsSet.Builder().subject(params.userKey.userId)
            .issuer(params.context.packageName)
            .claim(CHALLENGE, params.challenge)
            .issueTime(Date.from(params.issueTime))
            .notBeforeTime(Date.from(params.notBeforeTime))
            .expirationTime(Date.from(params.expiration))
        params.customClaims.forEach { (key: String, value: Any) ->
            claimsSet.claim(key, value)
        }
        val signedJWT =
            SignedJWT(
                JWSHeader.Builder(parse(params.algorithm))
                    .keyID(params.userKey.kid).build(),
                claimsSet.build()
            )
        signedJWT.sign(params.privateKey, params.signature)
        return signedJWT.serialize()
    }
}

/**
 * Extension function to sign a JWT using either a CryptoObject signature or a private key.
 * If a signature is provided, it uses the CryptoObject for signing, otherwise falls back to RSASSASigner.
 *
 * @param privateKey The private key to use for signing if no signature is provided
 * @param signature Optional signature from a CryptoObject for hardware-backed signing
 */
private fun SignedJWT.sign(privateKey: PrivateKey, signature: Signature?) {
    try {
        signature?.let {
            //Using CryptoObject
            this.sign(RSASASignatureSigner(it))
        } ?: run {
            this.sign(RSASSASigner(privateKey))
        }
    } catch (e: JOSEException) {
        e.cause?.let { throw it } ?: throw e
    }
}

/**
 * Represents a cryptographic key pair used for device binding operations.
 *
 * @property publicKey The RSA public key component
 * @property privateKey The private key component
 * @property keyAlias The alias used to identify this key pair in the Android KeyStore
 */
data class KeyPair(val publicKey: RSAPublicKey, val privateKey: PrivateKey, var keyAlias: String)

/**
 * Parameters required for signing a JWT with device binding authentication.
 *
 * @property context The Android context for cryptographic operations
 * @property algorithm The JWS algorithm to use for signing (e.g., "RS256")
 * @property keyPair The key pair to use for signing
 * @property signature Optional CryptoObject signature for hardware-backed signing
 * @property kid The key identifier to include in the JWT header
 * @property userId The user identifier to include as the JWT subject
 * @property challenge The challenge string to include in the JWT claims
 * @property issueTime The time when the JWT was issued
 * @property notBeforeTime The time before which the JWT is not valid
 * @property expiration The time when the JWT expires
 * @property attestation The attestation type to include in the key registration
 */
data class SigningParameters(
    val context: Context,
    val algorithm: String,
    val keyPair: KeyPair,
    val signature: Signature?,
    val kid: String,
    val userId: String,
    val challenge: String,
    val issueTime: Instant,
    val notBeforeTime: Instant,
    val expiration: Instant,
    val attestation: Attestation = Attestation.None
)

/**
 * Parameters required for signing a JWT using a UserKey with custom claims support.
 *
 * @property context The Android context for cryptographic operations
 * @property algorithm The JWS algorithm to use for signing (e.g., "RS256")
 * @property userKey The user key containing key information and metadata
 * @property privateKey The private key to use for signing
 * @property signature Optional CryptoObject signature for hardware-backed signing
 * @property challenge The challenge string to include in the JWT claims
 * @property issueTime The time when the JWT was issued
 * @property notBeforeTime The time before which the JWT is not valid
 * @property expiration The time when the JWT expires
 * @property customClaims Additional custom claims to include in the JWT
 */
data class UserKeySigningParameters(
    val context: Context,
    val algorithm: String,
    val userKey: UserKey,
    val privateKey: PrivateKey,
    val signature: Signature?,
    val challenge: String,
    val issueTime: Instant,
    val notBeforeTime: Instant,
    val expiration: Instant,
    val customClaims: Map<String, Any> = emptyMap()
)
