/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.id

import android.security.keystore.KeyProperties
import java.security.Key
import java.security.MessageDigest

/**
 * A compatibility implementation of [DeviceIdentifier] that provides backward compatibility
 * with legacy device identification methods.
 *
 * This implementation:
 * - First attempts to retrieve a key from the KeyStore using the Android ID as the key alias
 * - If found, combines the Android ID with a SHA-1 hash of the key to create a composite identifier
 * - If no legacy key exists, falls back to the [DefaultDeviceIdentifier] implementation
 *
 * This implementation is useful for maintaining device identity across app updates when
 * transitioning from a legacy identification scheme to the new one.
 *
 * Note: The SHA-1 algorithm is used for backward compatibility, though it's generally
 * recommended to use SHA-256 for new implementations.
 */
object LegacyDeviceIdentifier : DeviceIdentifier {

    /**
     * A lazily initialized device identifier string.
     * The value is computed once on first access and cached for subsequent calls.
     *
     * @return A unique identifier string for the device
     */
    override val id: suspend () -> String = {
        catch {
            identifier() ?: DefaultDeviceIdentifier.id()
        }
    }

    /**
     * Generates the device identifier by checking for a legacy key in the KeyStore.
     * If found, combines the Android ID with a hash of the key data.
     * If not found, falls back to the default identifier.
     *
     * @return A composite identifier string or the default device identifier
     */
    internal suspend fun identifier(): String? {
        return key()?.let {
            AndroidIDDeviceIdentifier.id() + "-" +
                    MessageDigest.getInstance(KeyProperties.DIGEST_SHA1)
                        .digest(it.encoded).toHexString()
        }
    }

    /**
     * Attempts to retrieve a key from the KeyStore using the Android ID as the alias.
     *
     * @return The key if found, null otherwise
     */
    private suspend fun key(): Key? {
        val keyAlias = AndroidIDDeviceIdentifier.id()
        return if (KeyManager.containsKey(keyAlias)) {
            KeyManager.identifierKey(keyAlias)
        } else {
            null
        }
    }
}