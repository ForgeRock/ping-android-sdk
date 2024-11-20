/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.storage.encrypt

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import java.security.KeyStore.PrivateKeyEntry
import java.security.KeyStore.SecretKeyEntry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@SmallTest
class SecretKeyEncryptorTest {

    private val alias = "keystore-key"

    private val testDispatcher = StandardTestDispatcher()
    private val testCoroutineScope = TestScope(testDispatcher + Job())

    @AfterTest
    fun tearDown() {
        SecretKeyEncryptor.keyStore.deleteEntry(alias)
    }

    @Test
    fun testEncryptWithAsymmetricKey() {
        testCoroutineScope.runTest {
            val encryptor = SecretKeyEncryptor {
                keyAlias = alias
                enforceAsymmetricKey = true
            }

            val encrypted = encryptor.encrypt("test".toByteArray())
            val decrypted = encryptor.decrypt(encrypted)
            assertEquals("test", decrypted.toString(Charsets.UTF_8))

            //Make sure the key is stored in the keystore as a private key
            assertTrue(SecretKeyEncryptor.keyStore.getEntry(alias, null) is PrivateKeyEntry)
        }
    }

    @Test
    fun testEncryptWithSymmetricKey() {
        testCoroutineScope.runTest {
            val encryptor = SecretKeyEncryptor {
                keyAlias = alias
            }

            val encrypted = encryptor.encrypt("test".toByteArray())
            val decrypted = encryptor.decrypt(encrypted)
            assertEquals("test", decrypted.toString(Charsets.UTF_8))

            //Make sure the secret key is not generated
            assertTrue(SecretKeyEncryptor.keyStore.getEntry(alias, null) is SecretKeyEntry)
        }
    }

    @Test
    fun testEncryptWithSymmetricKeyThenAsymmetric() {
        testCoroutineScope.runTest {
            val encryptor = SecretKeyEncryptor {
                keyAlias = alias
            }

            val encrypted = encryptor.encrypt("test".toByteArray())
            val decrypted = encryptor.decrypt(encrypted)
            assertEquals("test", decrypted.toString(Charsets.UTF_8))

            //using the same alias, now switch to asymmetric key
            val encryptor2 = SecretKeyEncryptor {
                keyAlias = alias
                enforceAsymmetricKey = true
            }

            //Since it was encrypted with a symmetric key, keep using the symmetric key to decrypt
            val decrypted2 = encryptor2.decrypt(encrypted)
            assertEquals("test", decrypted2.toString(Charsets.UTF_8))

            encryptor2.encrypt("test".toByteArray())
            //The key is now stored as a private key after encrypt
            assertTrue(SecretKeyEncryptor.keyStore.getEntry(alias, null) is PrivateKeyEntry)
        }
    }
}