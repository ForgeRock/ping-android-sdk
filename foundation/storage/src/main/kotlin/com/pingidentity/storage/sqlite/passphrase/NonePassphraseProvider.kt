/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.sqlite.passphrase

/**
 * A PassphraseProvider implementation that provides an empty passphrase.
 * This provider is used when encryption is disabled, and it always returns an empty string.
 * 
 * Note: Using this provider effectively disables SQLCipher encryption, so it should only
 * be used in scenarios where encryption is not required or where external encryption is applied.
 */
class NonePassphraseProvider : PassphraseProvider {
    /**
     * Returns an empty string as the passphrase, which effectively disables encryption.
     *
     * @return An empty string.
     */
    override suspend fun getPassphrase(): String = ""
}
