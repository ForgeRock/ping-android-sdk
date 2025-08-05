/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.id

import android.security.keystore.KeyProperties
import java.security.MessageDigest

/**
 * The default implementation of [DeviceIdentifier] that uses the Android KeyStore
 * to generate a persistent device identifier.
 *
 * This implementation:
 * - Creates an RSA key pair in the Android KeyStore the first time it's accessed
 * - Uses the public key as the basis for a device identifier
 * - Applies SHA-256 hashing to the key data and converts it to a hex string
 * - Caches the identifier for efficient subsequent access
 *
 * Note: The identifier will change if the device is factory reset or if the key is
 * explicitly removed from the KeyStore.
 */
object DefaultDeviceIdentifier : DeviceIdentifier {

    /** The key alias used to store the device identifier key in the Android KeyStore */
    internal const val KEY_ALIAS = "com.pingidentity.device.id.DEVICE_IDENTIFIER"

    /**
     * A lazily initialized device identifier string.
     * The value is computed once on first access and cached for subsequent calls.
     *
     * @return A unique identifier string for the device
     */
    @OptIn(ExperimentalStdlibApi::class)
    override val id: suspend () -> String = {
        catch {
            MessageDigest.getInstance(KeyProperties.DIGEST_SHA256)
                .digest(KeyManager.identifierKey(KEY_ALIAS).encoded).toHexString()
        }
    }

}