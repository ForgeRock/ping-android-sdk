/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.binding

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.AlgorithmParameterSpec
import kotlin.io.encoding.Base64.Default.UrlSafe
import kotlin.io.encoding.Base64.PaddingOption

/**
 * Manages cryptographic key operations using the Android KeyStore system.
 *
 * This class provides a secure abstraction for creating, storing, and accessing
 * RSA key pairs within the Android KeyStore. It handles key generation with
 * configurable security parameters, automatic key aliasing through hashing,
 * and secure key lifecycle management.
 *
 * Key features:
 * - Automatic key alias generation using SHA-256 hashing of the key ID
 * - RSA 2048-bit key pairs with signing, encryption, and decryption capabilities
 * - Integration with Android KeyStore for hardware-backed security
 * - Support for multiple digest algorithms (SHA-256, SHA-384, SHA-512)
 * - Certificate chain access for attestation purposes
 * - Lazy initialization for performance optimization
 *
 * The class is designed to work with Android's hardware security features
 * when available, providing enhanced protection for cryptographic keys.
 *
 * @param keyId The unique identifier used to generate the key alias through hashing
 *
 * @see android.security.keystore.KeyGenParameterSpec
 * @see java.security.KeyStore
 */
class CryptoKey(private var keyId: String) {

    /**
     * The hashing algorithm used to generate key aliases from key IDs.
     * Uses SHA-256 for consistent and secure alias generation.
     */
    private val hashingAlgorithm = "SHA-256"

    /**
     * The RSA key size in bits used for key pair generation.
     * Set to 2048 bits for a balance of security and performance.
     */
    internal val keySize = 2048

    /**
     * The timeout value in seconds for cryptographic operations.
     * Used for operations that may require user authentication.
     */
    val timeout = 5

    /**
     * The Android KeyStore provider name used for hardware-backed key storage.
     */
    private val androidKeyStore = "AndroidKeyStore"

    /**
     * The block mode used for encryption operations.
     * Set to ECB (Electronic Codebook) mode for RSA operations.
     */
    private val encryptionBlockMode = KeyProperties.BLOCK_MODE_ECB

    /**
     * The padding scheme used for encryption operations.
     * Uses PKCS#1 v1.5 padding for RSA encryption.
     */
    private val encryptionPadding = KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1

    /**
     * The cryptographic algorithm used for key generation.
     * Set to RSA for asymmetric cryptography operations.
     */
    private val encryptionAlgorithm = KeyProperties.KEY_ALGORITHM_RSA

    /**
     * The padding scheme used for digital signature operations.
     * Uses PKCS#1 v1.5 padding for RSA signatures.
     */
    private val signaturePadding = KeyProperties.SIGNATURE_PADDING_RSA_PKCS1

    /**
     * The intended purposes for the generated keys.
     * Combines signing, encryption, and decryption capabilities for maximum flexibility.
     */
    private val purpose =
        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT

    /**
     * The unique key alias generated from the key ID using SHA-256 hashing.
     *
     * This property generates a consistent, URL-safe Base64 encoded alias from the
     * provided key ID. The hashing ensures that:
     * - Key IDs are transformed into valid KeyStore aliases
     * - The same key ID always produces the same alias
     * - Sensitive key ID information is not directly exposed in the KeyStore
     * - The alias is URL-safe and doesn't contain padding characters
     *
     * The alias is computed lazily and cached for performance.
     */
    val keyAlias: String by lazy {
        val digest: MessageDigest = MessageDigest.getInstance(hashingAlgorithm)
        val hash: ByteArray = digest.digest(keyId.toByteArray())
        UrlSafe.withPadding(PaddingOption.ABSENT).encode(hash)
        //Base64.encodeToString(hash, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
    }

    /**
     * The Android KeyStore instance used for secure key storage and retrieval.
     *
     * This property provides access to the hardware-backed KeyStore when available,
     * or falls back to software-based storage. The KeyStore is initialized lazily
     * and cached for performance. It's configured to use the AndroidKeyStore provider
     * which integrates with the device's security features.
     */
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(androidKeyStore).apply {
            load(null)
        }
    }

    /**
     * Creates a KeyGenParameterSpec.Builder with the configured cryptographic parameters.
     *
     * This method constructs a builder for key generation parameters with the following
     * security configuration:
     * - RSA key pair with the specified key size (2048 bits)
     * - Support for multiple digest algorithms (SHA-256, SHA-384, SHA-512)
     * - PKCS#1 v1.5 padding for both signatures and encryption
     * - ECB block mode for encryption operations
     * - Combined purposes: signing, encryption, and decryption
     *
     * The builder can be further customized before being used to generate keys,
     * such as adding attestation challenges or user authentication requirements.
     *
     * @return A configured KeyGenParameterSpec.Builder ready for customization
     * @see KeyGenParameterSpec.Builder
     */
    internal fun keyBuilder(): KeyGenParameterSpec.Builder {
        return KeyGenParameterSpec.Builder(keyAlias, purpose)
            .setDigests(
                KeyProperties.DIGEST_SHA512,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA256
            ).setKeySize(keySize)
            .setSignaturePaddings(signaturePadding).setBlockModes(encryptionBlockMode)
            .setEncryptionPaddings(encryptionPadding)
    }

    /**
     * Creates a new RSA key pair using the provided algorithm parameter specification.
     *
     * This method generates a cryptographic key pair using either the Android KeyStore
     * (for hardware-backed security) or the standard Java cryptography provider
     * (for software-based keys). The choice depends on the useAndroidKeyStore parameter.
     *
     * When using the Android KeyStore:
     * - Keys are stored securely in hardware (if available) or TEE
     * - Private keys cannot be extracted from the device
     * - Keys can require user authentication for access
     * - Attestation certificates can be generated
     *
     * When not using Android KeyStore:
     * - Keys are generated in software only
     * - Primarily used for Application Pin scenarios
     * - No hardware security
     *
     * @param spec The algorithm parameter specification defining key generation parameters
     * @param useAndroidKeyStore Whether to use Android KeyStore (true) or standard provider (false)
     * @return A KeyPair containing the generated RSA public and private keys
     * @throws java.security.GeneralSecurityException if key generation fails
     */
    internal fun create(spec: AlgorithmParameterSpec, useAndroidKeyStore: Boolean = true): KeyPair {

        val keyPairGenerator = if (useAndroidKeyStore) {
            KeyPairGenerator.getInstance(encryptionAlgorithm, androidKeyStore)
        } else {
            KeyPairGenerator.getInstance(encryptionAlgorithm)
        }

        keyPairGenerator.initialize(spec)
        val keyPair = keyPairGenerator.generateKeyPair();

        return KeyPair(keyPair.public as RSAPublicKey, keyPair.private)
    }

    /**
     * The private key associated with this CryptoKey instance, retrieved from the Android KeyStore.
     *
     * This property lazily retrieves the private key from the KeyStore using the generated
     * key alias. The private key is used for cryptographic operations such as:
     * - Digital signature generation
     * - Decryption of data encrypted with the corresponding public key
     * - Authentication and verification operations
     *
     * The private key remains within the Android KeyStore and cannot be extracted
     * when hardware-backed security is used. Access to the key may require user
     * authentication depending on the key generation parameters.
     *
     * @return The PrivateKey if it exists in the KeyStore, null if no key is found
     */
    internal val privateKey: PrivateKey? by lazy {
        keyStore.getKey(keyAlias, null) as? PrivateKey
    }

    /**
     * The certificate chain associated with this key pair in the Android KeyStore.
     *
     * This property retrieves the complete certificate chain for the key pair,
     * which may include:
     * - The leaf certificate containing the public key
     * - Intermediate certificates for the certificate authority chain
     * - Root certificates (when attestation is enabled)
     *
     * The certificate chain is particularly important for key attestation scenarios
     * where the authenticity and properties of the key need to be verified.
     * For hardware-backed keys, the chain provides cryptographic proof that
     * the key was generated in secure hardware.
     *
     * @return Array of Certificate objects representing the complete certificate chain
     */
    internal val certificateChains: Array<Certificate> by lazy {
        keyStore.getCertificateChain(keyAlias)
    }

    /**
     * Deletes the key pair and associated certificates from the Android KeyStore.
     *
     * This method permanently removes all cryptographic material associated with
     * this key alias from the KeyStore, including:
     * - The private key
     * - The public key (as part of the certificate)
     * - The complete certificate chain
     * - Any attestation certificates
     *
     * **Warning**: This operation is irreversible. Once deleted, the key pair
     * cannot be recovered and any data encrypted with the public key will become
     * permanently inaccessible unless the private key was backed up externally
     * (which is not possible with hardware-backed keys).
     *
     * The method should be called when:
     * - Cleaning up after failed operations
     * - Removing keys during user logout or app uninstallation
     * - Rotating keys for security purposes
     * - Clearing device binding registrations
     *
     * @throws java.security.KeyStoreException if the deletion operation fails
     */
    internal fun delete() {
        keyStore.deleteEntry(keyAlias)
    }
}

/**
 * Interface for classes that manage or use CryptoKey instances.
 *
 * This interface provides a standardized way for classes to declare their dependency
 * on CryptoKey functionality. It's typically used with dependency injection frameworks
 * or initialization patterns to ensure that classes have access to the cryptographic
 * key management capabilities they need.
 *
 * Implementing classes should ensure that the cryptoKey property is properly
 * initialized before attempting to use cryptographic operations. The property
 * is marked as `var` to allow for runtime injection or assignment.
 *
 * @see CryptoKey
 */
interface CryptoKeyAware {
    /**
     * The CryptoKey instance used for cryptographic operations.
     *
     * This property provides access to key generation, storage, and management
     * capabilities. It should be initialized before any cryptographic operations
     * are attempted.
     *
     * @see CryptoKey
     */
    var cryptoKey: CryptoKey
}