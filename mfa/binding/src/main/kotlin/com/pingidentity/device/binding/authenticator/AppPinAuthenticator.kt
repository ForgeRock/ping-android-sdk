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
import com.pingidentity.device.binding.authenticator.exception.AbortException
import com.pingidentity.device.binding.authenticator.exception.DeviceNotRegisteredException
import com.pingidentity.device.binding.authenticator.exception.InvalidCredentialException
import com.pingidentity.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v1CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.crypto.params.AsymmetricKeyParameter
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.math.BigInteger
import java.security.KeyStore
import java.security.PrivateKey
import java.security.UnrecoverableKeyException
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAKeyGenParameterSpec
import java.time.Instant
import java.util.Date
import java.util.Locale

/**
 * A PIN-based device authenticator that generates and manages RSA key pairs protected by user PIN.
 *
 * This authenticator creates software-based RSA key pairs and stores them in a password-protected
 * keystore file. The private keys are encrypted using the user's PIN and persisted to secure storage.
 *
 * Key features:
 * - Software-based RSA key generation with configurable key size
 * - PIN-protected keystore storage with retry mechanism
 * - Secure key persistence using encrypted storage
 * - Support for key deletion and cleanup
 *
 * Security considerations:
 * - Keys are stored in software keystore, not hardware security module
 * - Keystore is encrypted with hardware-backed encryption if available
 * - PIN protection provides encryption for stored keys
 * - Uses RSA with SHA256 for certificate signing
 *
 * Example usage:
 * ```kotlin
 * val authenticator = AppPinAuthenticator {
 *     pinRetry = 3
 *     keystoreType = "PKCS12"
 *     pinCollector = { prompt -> getUserPin(prompt) }
 * }
 *
 * // Register new device
 * val result = authenticator.register(context, Attestation.None)
 *
 * // Authenticate existing device
 * val authResult = authenticator.authenticate(context)
 * ```
 *
 * @param config Configuration object containing PIN collection strategy, storage settings,
 *               retry limits, and other authenticator-specific options
 *
 * @see DeviceAuthenticator
 * @see AppPinConfig
 * @see CryptoKeyAware
 *
 * @since 1.0.0
 */
open class AppPinAuthenticator(private val config: AppPinConfig) : CryptoKeyAware,
    DeviceAuthenticator {

    /**
     * The cryptographic key configuration for this authenticator.
     * When set, updates the storage filename to include the key alias.
     */
    private lateinit var _cryptoKey: CryptoKey

    /**
     * Secure storage instance for persisting encrypted keystore data.
     * Lazily initialized based on configuration settings.
     */
    private val storage: Storage<ByteArray> by lazy {
        config.storage()
    }

    /**
     * Gets or sets the cryptographic key configuration.
     * Setting this property automatically updates the storage filename
     * to include the key alias for proper key isolation.
     */
    override var cryptoKey: CryptoKey
        get() = _cryptoKey
        set(value) {
            _cryptoKey = value
            config.storage {
                this.fileName = "$fileName.${value.keyAlias}"
            }
        }

    /**
     * The type of device binding authentication provided by this authenticator.
     * Always returns [DeviceBindingAuthenticationType.APPLICATION_PIN].
     */
    override val type: DeviceBindingAuthenticationType =
        DeviceBindingAuthenticationType.APPLICATION_PIN

    /**
     * Cached key pair used during registration to avoid requesting PIN multiple times.
     * Set during [generateKeys] and used in [authenticate], then cleared after use.
     */
    private var keyPair: java.security.KeyPair? = null

    companion object {
        /**
         * Factory method for creating an [AppPinAuthenticator] with configuration.
         *
         * @param block Configuration block for setting up the authenticator
         * @return Configured [AppPinAuthenticator] instance
         */
        operator fun invoke(block: AppPinConfig.() -> Unit = {}): AppPinAuthenticator {
            val config = AppPinConfig().apply(block) // apply the configuration block
            return AppPinAuthenticator(config)
        }
    }

    /**
     * Registers a new device by generating a fresh RSA key pair protected by user PIN.
     *
     * This method performs the following steps:
     * 1. Prompts user for PIN through configured PIN collector
     * 2. Generates new RSA key pair with specified key size
     * 3. Creates self-signed certificate for the key pair
     * 4. Stores the key pair in password-protected keystore
     * 5. Persists encrypted keystore data to secure storage
     *
     * The generated key pair is cached to avoid requesting PIN again during
     * subsequent authentication calls in the same session.
     *
     * @param context Android context for any UI operations (not used in PIN auth)
     * @param attestation Attestation type - only [Attestation.None] is supported
     * @return [Result] containing the generated [KeyPair] on success, or failure with exception
     *
     * @throws Exception if PIN collection fails, key generation fails, or storage fails
     */
    override suspend fun register(
        context: Context,
        attestation: Attestation
    ): Result<KeyPair> = runCatching {
        config.logger.d("AppPinAuthenticator: Generating keys for alias=${cryptoKey.keyAlias}")
        keyPair = generateKeys(config)
        KeyPair(keyPair!!.public as RSAPublicKey, keyPair!!.private, cryptoKey.keyAlias)
    }

    /**
     * Authenticates the device by retrieving the stored private key.
     *
     * This method attempts to authenticate in the following order:
     * 1. If a cached key pair exists (from recent registration), uses it directly
     * 2. Otherwise, prompts for PIN and retrieves private key from storage
     * 3. Implements retry mechanism with configurable attempts
     * 4. Clears cached key pair after use for security
     *
     * The method handles various error conditions including:
     * - User cancellation during PIN entry
     * - Invalid PIN attempts with retry logic
     * - Missing or corrupted keystore data
     * - File system errors
     *
     * @param context Android context for any UI operations (not used in PIN auth)
     * @return [Result] containing [Pair] of [PrivateKey] and null [BiometricPrompt.CryptoObject]
     *         on success, or failure with appropriate exception
     *
     * @see getPrivateKey
     */
    override suspend fun authenticate(
        context: Context,
    ): Result<Pair<PrivateKey, BiometricPrompt.CryptoObject?>> {
        return try {
            config.logger.d("AppPinAuthenticator: Starting authentication")
            keyPair?.let {
                config.logger.d("AppPinAuthenticator: Using generated key pair for authentication")
                Result.success(Pair(it.private, null))
            } ?: run {
                config.logger.d("AppPinAuthenticator: Retrieving existing private key")
                Result.success(Pair(getPrivateKey(config), null))
            }
        } catch (e: Exception) {
            config.logger.w("AppPinAuthenticator: Authentication failed", e)
            Result.failure(e)
        } finally {
            keyPair = null
        }
    }

    /**
     * Checks if this authenticator supports the given attestation type.
     *
     * This PIN-based authenticator only supports [Attestation.None] since it
     * uses software-generated keys without hardware attestation capabilities.
     *
     * @param context Android context (not used)
     * @param attestation The attestation type to check
     * @return true if attestation is [Attestation.None], false otherwise
     */
    override fun isSupported(context: Context, attestation: Attestation): Boolean {
        return attestation is Attestation.None
    }

    /**
     * Retrieves the private key from storage with PIN-based authentication and retry logic.
     *
     * This method implements a retry mechanism that:
     * 1. Prompts user for PIN through configured collector
     * 2. Attempts to load and decrypt the keystore
     * 3. Retries on authentication failures up to configured limit
     * 4. Handles various error conditions with appropriate exceptions
     *
     * Error handling:
     * - [AbortException]: User cancelled PIN entry
     * - [DeviceNotRegisteredException]: Keystore file not found
     * - [InvalidCredentialException]: Wrong PIN or retry limit exceeded
     *
     * @param option Configuration containing PIN collector and retry settings
     * @return The decrypted [PrivateKey] for this device
     *
     * @throws AbortException if user cancels PIN entry
     * @throws DeviceNotRegisteredException if keystore file doesn't exist
     * @throws InvalidCredentialException if PIN is wrong or retry limit exceeded
     */
    private suspend fun getPrivateKey(option: AppPinConfig): PrivateKey {
        config.logger.d("AppPinAuthenticator: Getting private key...")
        var lastException: Exception? = null

        repeat(option.pinRetry) { attempt ->
            val pin: CharArray
            try {
                pin = option.pinCollector(option.prompt)
                config.logger.d("AppPinAuthenticator: PIN collected successfully")
            } catch (e: Exception) {
                config.logger.w("AppPinAuthenticator: PIN collection cancelled by user", e)
                throw AbortException("User cancelled the operation", e)
            }

            lastException = try {
                return getPrivateKey(pin)
                    ?: throw DeviceNotRegisteredException()
            } catch (e: FileNotFoundException) {
                config.logger.w("AppPinAuthenticator: KeyStore file not found", e)
                throw DeviceNotRegisteredException("KeyStore file not found", e)
            } catch (e: UnrecoverableKeyException) {
                config.logger.w(
                    "AppPinAuthenticator: Invalid PIN provided (UnrecoverableKeyException)",
                    e
                )
                InvalidCredentialException("Invalid Pin", e)
            } catch (e: IOException) {
                config.logger.w("AppPinAuthenticator: Invalid PIN provided (IOException)", e)
                InvalidCredentialException("Invalid Pin", e)
            }
        }

        config.logger.w("AppPinAuthenticator: PIN retry limit exceeded")
        throw lastException ?: InvalidCredentialException("PIN retry limit exceeded")
    }

    /**
     * Deletes all stored keys and associated data for this device.
     *
     * This method permanently removes:
     * - Encrypted keystore file from storage
     * - All associated cryptographic material
     * - Device registration state
     *
     * After calling this method, the device will need to be re-registered
     * before it can authenticate again.
     */
    override suspend fun deleteKeys() {
        storage.delete()
    }

    /**
     * Generates a new RSA key pair and persists it to encrypted storage.
     *
     * This method:
     * 1. Collects PIN from user through configured collector
     * 2. Creates RSA key specification with configured parameters
     * 3. Generates software-based key pair (not hardware-backed)
     * 4. Persists key pair to password-protected keystore
     *
     * The generated keys are software-based and stored in a PKCS12 keystore
     * format, encrypted with the user's PIN.
     *
     * @param option Configuration containing PIN collector and key parameters
     * @return Generated [java.security.KeyPair]
     *
     * @throws Exception if PIN collection fails or key generation fails
     */
    private suspend fun generateKeys(option: AppPinConfig): java.security.KeyPair {
        config.logger.d("AppPinAuthenticator: Collecting PIN for key generation")
        val pin = option.pinCollector(option.prompt)
        //Generate Software Key
        //Cannot use KeyGenParameterSpec.Builder to generate a software key.
        //That builder is part of the android.security.keystore package and is exclusively for
        //creating specifications for keys that will be generated and stored inside the Android Keystore.
        val spec = RSAKeyGenParameterSpec(cryptoKey.keySize, RSAKeyGenParameterSpec.F4)
        val keyPair = cryptoKey.create(spec, false);
        persist(keyPair, pin)
        return keyPair
    }

    /**
     * Persists a key pair to encrypted storage using PIN-based protection.
     *
     * This method:
     * 1. Creates a new keystore instance of configured type
     * 2. Generates a self-signed certificate for the key pair
     * 3. Stores the private key entry with certificate chain
     * 4. Encrypts the entire keystore with user's PIN
     * 5. Saves encrypted keystore data to secure storage
     *
     * The keystore is protected at multiple levels:
     * - Individual key entries are password-protected
     * - Entire keystore is encrypted with PIN
     * - Storage layer provides additional encryption
     *
     * @param keyPair The RSA key pair to persist
     * @param pin User's PIN for keystore encryption
     *
     * @throws Exception if keystore operations or storage fails
     */
    private suspend fun persist(
        keyPair: java.security.KeyPair,
        pin: CharArray
    ) {
        config.logger.d("AppPinAuthenticator: Persisting key pair to ${config.keystoreType} keystore")
        withContext(Dispatchers.IO) {
            val keyStore = KeyStore.getInstance(config.keystoreType)
            keyStore.load(null)
            val privateKeyEntry = KeyStore.PrivateKeyEntry(
                keyPair.private,
                arrayOf(generateCertificate(keyPair, cryptoKey.keyAlias))
            )
            keyStore.setEntry(cryptoKey.keyAlias, privateKeyEntry, KeyStore.PasswordProtection(pin))
            val outputStream = ByteArrayOutputStream()
            keyStore.store(outputStream, pin)
            storage.save(outputStream.toByteArray())
        }
    }

    /**
     * Generates a self-signed X.509 certificate for the given key pair.
     *
     * The certificate includes:
     * - Subject and issuer set to the key alias
     * - RSA public key from the generated pair
     * - SHA256WithRSA signature algorithm
     * - Current timestamp as validity period
     * - Self-signed using the private key
     *
     * This certificate is used to complete the keystore entry and provide
     * a valid certificate chain for the private key.
     *
     * @param keyPair The RSA key pair to create certificate for
     * @param subject The subject name for the certificate (typically key alias)
     * @return Self-signed [X509Certificate] for the key pair
     *
     * @throws Exception if certificate generation fails
     */
    private fun generateCertificate(
        keyPair: java.security.KeyPair,
        subject: String
    ): X509Certificate {
        val validityBeginDate = Date.from(Instant.now())
        val owner = X500Name("cn=$subject")
        val sigAlgId: AlgorithmIdentifier =
            DefaultSignatureAlgorithmIdentifierFinder().find("SHA256WithRSA")
        val digAlgId: AlgorithmIdentifier = DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId)
        val privateKeyAsymKeyParam: AsymmetricKeyParameter =
            PrivateKeyFactory.createKey(keyPair.private.encoded)
        val sigGen = BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(privateKeyAsymKeyParam)
        val builder = X509v1CertificateBuilder(
            owner,
            BigInteger.valueOf(System.currentTimeMillis()),
            validityBeginDate,
            validityBeginDate,
            Locale.ENGLISH,
            owner,
            SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        )
        return JcaX509CertificateConverter().getCertificate(builder.build(sigGen))
    }

    /**
     * Retrieves the private key from the keystore using the provided PIN.
     *
     * This method:
     * 1. Loads the keystore with the provided PIN
     * 2. Checks if the key alias exists in the keystore
     * 3. Retrieves the private key entry using PIN protection
     * 4. Returns the private key or null if not found
     *
     * @param pin The PIN to decrypt the keystore and key entry
     * @return The [PrivateKey] if found, null otherwise
     *
     * @throws UnrecoverableKeyException if PIN is incorrect
     * @throws IOException if keystore is corrupted or PIN is wrong
     */
    private suspend fun getPrivateKey(pin: CharArray): PrivateKey? {
        val keyStore = getKeyStore(pin)
        val entry = keyStore.takeIf { it.isKeyEntry(cryptoKey.keyAlias) }
            ?.getEntry(
                cryptoKey.keyAlias,
                KeyStore.PasswordProtection(pin)
            ) as? KeyStore.PrivateKeyEntry
        val privateKey = entry?.privateKey
        return privateKey
    }

    /**
     * Loads and decrypts the keystore from storage using the provided PIN.
     *
     * This method:
     * 1. Retrieves encrypted keystore data from storage
     * 2. Creates input stream from the data
     * 3. Loads keystore instance with PIN decryption
     * 4. Returns the loaded keystore for key operations
     *
     * The operation is performed on IO dispatcher to avoid blocking
     * the calling coroutine.
     *
     * @param pin The PIN to decrypt the keystore
     * @return Loaded and decrypted [KeyStore] instance
     *
     * @throws IOException if keystore data is corrupted or PIN is wrong
     * @throws FileNotFoundException if storage doesn't contain keystore data
     */
    private suspend fun getKeyStore(pin: CharArray): KeyStore =
        withContext(Dispatchers.IO) {
            val keystore = KeyStore.getInstance(config.keystoreType)
            val byteArray = storage.get()
            val inputStream = ByteArrayInputStream(byteArray)
            keystore.load(inputStream, pin)
            keystore
        }

}
