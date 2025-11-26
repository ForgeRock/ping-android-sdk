/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.sqlite.passphrase

/**
 * A simple PassphraseProvider that uses a fixed passphrase.
 * This is useful for development, testing, or when a specific passphrase is required.
 *
 * Note: This should not be used in production unless there's a specific requirement.
 */
class FixedPassphraseProvider(private val fixedPassphrase: String) : PassphraseProvider {

    /**
     * Constructs a new FixedPassphraseProvider with a randomly generated passphrase.
     * This is useful when you want a consistent passphrase but don't care what it is.
     */
    constructor() : this(PassphraseProvider.generateRandomPassphrase())

    override suspend fun getPassphrase(): String = fixedPassphrase
}
