/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage.passphrase

import com.pingidentity.storage.sqlite.passphrase.PassphraseProvider

/**
 * A PassphraseProvider for test environments that always returns a fixed test passphrase.
 * This ensures consistent behavior in tests and makes it easier to set up test databases.
 */
class TestPassphraseProvider : PassphraseProvider {
    
    companion object {
        const val TEST_PASSPHRASE = "test_passphrase"
    }
    
    override suspend fun getPassphrase(): String = TEST_PASSPHRASE
}
