/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding.authenticator

import android.content.Context
import androidx.biometric.BiometricPrompt
import com.pingidentity.device.binding.CryptoKey
import com.pingidentity.device.binding.CryptoKeyAware
import com.pingidentity.device.binding.authenticator.exception.DeviceNotRegisteredException
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * A device authenticator implementation that requires no user authentication.
 *
 * This authenticator provides the most convenient but least secure authentication method.
 * Keys are accessible without any user verification, making it suitable for scenarios
 * where convenience is prioritized over security, such as development environments
 * or applications with minimal security requirements.
 *
 * The authenticator manages cryptographic keys through the Android KeyStore but does
 * not require biometric authentication, PIN, or any other user verification to access them.
 */
open class None : CryptoKeyAware, DeviceAuthenticator {

    /**
     * The cryptographic key instance used for key generation and management.
     * This property is injected and manages the underlying Android KeyStore operations.
     */
    override lateinit var cryptoKey: CryptoKey

    /**
     * The authentication type for this authenticator, which is NONE indicating
     * no user authentication is required to access cryptographic keys.
     */
    override val type = DeviceBindingAuthenticationType.NONE

    /**
     * Registers a new key pair for device binding without requiring user authentication.
     *
     * Creates a new RSA key pair in the Android KeyStore with the specified attestation.
     * The key pair can be accessed immediately without any authentication challenges.
     *
     * @param context The Android context for cryptographic operations
     * @param attestation The attestation configuration to apply to the key generation
     * @return A Result containing the generated KeyPair on success, or an error on failure
     */
    override suspend fun register(context: Context, attestation: Attestation): Result<KeyPair> = runCatching {
        val builder = cryptoKey.keyBuilder()
        builder.setAttestationChallenge(attestation.challenge)
        val key = cryptoKey.create(builder.build())
        KeyPair(key.public as RSAPublicKey, key.private, cryptoKey.keyAlias)
    }

    /**
     * Authenticates and retrieves the private key without requiring user verification.
     *
     * Since this is a "None" authenticator, no actual authentication is performed.
     * The method simply checks if a private key exists and returns it immediately.
     * No CryptoObject is returned since no authentication UI is involved.
     *
     * @param context The Android context (unused in this implementation)
     * @return A Result containing a Pair of the PrivateKey and null CryptoObject on success,
     *         or a DeviceNotRegisteredException if no key is found
     */
    override suspend fun authenticate(context: Context): Result<Pair<PrivateKey, BiometricPrompt.CryptoObject?>> {
        return cryptoKey.privateKey?.let {
            Result.success(Pair(it, null))
        } ?: Result.failure(DeviceNotRegisteredException("Device is not registered"))
    }

    /**
     * Deletes all cryptographic keys associated with this authenticator.
     *
     * Removes the key pair from the Android KeyStore. This operation is irreversible
     * and will require re-registration to create new keys.
     */
    override suspend fun deleteKeys() {
        cryptoKey.delete()
    }

}