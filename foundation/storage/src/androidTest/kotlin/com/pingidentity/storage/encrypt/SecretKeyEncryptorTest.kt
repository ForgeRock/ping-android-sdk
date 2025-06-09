/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
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
import java.security.KeyStore
import java.security.KeyStore.PrivateKeyEntry
import java.security.KeyStore.SecretKeyEntry
import kotlin.system.measureTimeMillis
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@RunWith(AndroidJUnit4::class)
@SmallTest
class SecretKeyEncryptorTest {

    private val alias = "keystore-key"

    private val testDispatcher = StandardTestDispatcher()
    private val testCoroutineScope = TestScope(testDispatcher + Job())

    private val keyStore: KeyStore
        get() {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            return keyStore
        }

    @AfterTest
    fun tearDown() {
        keyStore.deleteEntry(alias)
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
            assertTrue(keyStore.getEntry(alias, null) is PrivateKeyEntry)
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
            assertTrue(keyStore.getEntry(alias, null) is SecretKeyEntry)
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
            assertTrue(keyStore.getEntry(alias, null) is PrivateKeyEntry)
        }
    }

    @Test
    fun testPerformance() = runTest(timeout = 3.toDuration(DurationUnit.SECONDS)) {
        val runs = 10
        val encryptor = SecretKeyEncryptor {
            symmetricKeySize = 256
            keyAlias = alias
            strongBoxPreferred = false
        }
        val dataStr =
            "R)I~'xM&<F8^K*]!+@b{s;v#?q9tU[oG3z/Hl.D-j4yC=eP{aXwV`{uN7}gS6iBT%d}0^rW>k5E2mLf,Y;Q!jF7h<4]A?{g(0Zt9\"p\"c\"2R5O:3mN-`z6_U<b`X'I(>G)vB@qK_J?|eYd]P.CxH8yW(T=S:V_L]!w+o".toByteArray()

        var totalEncryptTime = 0L
        var totalDecryptTime = 0L

        repeat(runs) {
            var output: ByteArray
            val encryptTime = measureTimeMillis {
                output = encryptor.encrypt(dataStr)
            }
            val decryptTime = measureTimeMillis {
                output = encryptor.decrypt(output)
            }
            totalEncryptTime += encryptTime
            totalDecryptTime += decryptTime
            assertEquals(String(dataStr), String(output))
        }
    }
}