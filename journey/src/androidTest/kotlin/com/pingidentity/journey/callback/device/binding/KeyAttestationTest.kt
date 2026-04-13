/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback.device.binding

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nimbusds.jose.JWSAlgorithm.RS512
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import com.pingidentity.device.binding.Prompt
import com.pingidentity.device.binding.journey.DeviceBindingCallback
import com.pingidentity.journey.callback.ChoiceCallback
import com.pingidentity.journey.callback.TextOutputCallback
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.start
import com.pingidentity.journey.utils.DeviceSkipRule
import com.pingidentity.journey.utils.RequiresDevice
import com.pingidentity.orchestrate.ContinueNode
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test suite for validating Android Key Attestation functionality in device binding scenarios.
 *
 * This suite verifies that the SDK correctly handles key attestation with various configurations,
 * including attestation on/off states, transient state variables, and different authentication types.
 */
@RunWith(AndroidJUnit4::class)
class KeyAttestationTest : BaseDeviceBindingTest() {
    // Used for skipping tests on emulator
    @get:Rule
    val deviceSkipRule = DeviceSkipRule()

    @Before
    fun setupTree() {
        tree = "key-attestation"
    }

    /**
     * Verifies that when Key Attestation is disabled, the JWT does not include X.509 certificate chain.
     *
     * Tests the scenario where the "Key Attestation" toggle is OFF in AM, ensuring the SDK
     * generates a valid JWT with RS512 algorithm and signature key use, but without the x5c parameter.
     */
    @Test
    fun testKeyAttestationNoneAttestationOff() = runTest {
        val user = registerRandomUser()
        // Test that when "Key Attestation" is OFF in AM, the SDK does not include x5c (X.509 Certificate Chain) parameter in the JWK...
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks(username = user.username, password = user.password)
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("none-attestation-off")
        node = node.next() as ContinueNode

        val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
        deviceBindingCallback.bind()
            .onSuccess { token ->
                val jwtToken = JWTParser.parse(token)

                val signedJwtToken = jwtToken as SignedJWT
                assertEquals(RS512, signedJwtToken.header.jwk.algorithm)
                assertEquals(KeyUse.SIGNATURE, signedJwtToken.header.jwk.keyUse)
                assertTrue(signedJwtToken.header.jwk.x509CertChain.isNullOrEmpty())
                assertNotNull(signedJwtToken.header.keyID)
                assertNotNull(signedJwtToken.header.parsedBase64URL)

                assertEquals("com.pingidentity.journey.test", jwtToken.jwtClaimsSet.getClaim("iss"))
                assertEquals("android", jwtToken.jwtClaimsSet.getClaim("platform"))
                assertEquals(Build.VERSION.SDK_INT.toLong(), jwtToken.jwtClaimsSet.getClaim("android-version"))
            }
            .onFailure { error ->
                fail("testKeyAttestationNoneAttestationOff failed with ${error.message}")
            }
    }

    /**
     * Verifies that when Key Attestation is enabled, the JWT includes the X.509 certificate chain.
     *
     * Tests the scenario where the "Key Attestation" toggle is ON in AM, ensuring the SDK
     * generates a valid JWT with the x5c parameter containing attestation data. Requires a physical device.
     */
    @Test
    @RequiresDevice
    fun testKeyAttestationNoneAttestationOn() = runTest {
        val user = registerRandomUser()
        // Make sure that when "Key Attestation" is ON in AM, the SDK includes x5c (X.509 Certificate Chain) parameter...
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks(username = user.username, password = user.password)
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("none-attestation-on")
        node = node.next() as ContinueNode

        val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
        deviceBindingCallback.bind()
            .onSuccess { token ->
                val jwtToken = JWTParser.parse(token)

                val signedJwtToken = jwtToken as SignedJWT
                assertEquals(RS512, signedJwtToken.header.jwk.algorithm)
                assertEquals(KeyUse.SIGNATURE, signedJwtToken.header.jwk.keyUse)
                assertFalse(signedJwtToken.header.jwk.x509CertChain.isNullOrEmpty())
                assertNotNull(signedJwtToken.header.keyID)
                assertNotNull(signedJwtToken.header.parsedBase64URL)

                assertEquals("com.pingidentity.journey.test", jwtToken.jwtClaimsSet.getClaim("iss"))
                assertEquals("android", jwtToken.jwtClaimsSet.getClaim("platform"))
                assertEquals(Build.VERSION.SDK_INT.toLong(), jwtToken.jwtClaimsSet.getClaim("android-version"))

            }
            .onFailure { error ->
                fail("testKeyAttestationNoneAttestationOn failed with ${error.message}")
            }
    }

    /**
     * Verifies that attestation data is stored in the transient state variable when Key Attestation is enabled.
     *
     * Tests that when Key Attestation is enabled in the Device Binding node, the attestation validation
     * is performed and the extension data is stored in the transient state under DeviceBindingCallback.ATTESTATION.
     * Requires a physical device.
     */
    @Test
    @RequiresDevice
    @Ignore("This test is currently ignored due to bug in AIC - see TRIAGE-33916")
    fun testKeyAttestationTransientStateVariable() = runTest {
        val user = registerRandomUser()
        // Ensure that when Key Attestation toggle button is enabled in the Device Binding node,
        // Key Attestation Validation will be performed, and the extension data will be put into the transient state with the variable
        // DeviceBindingCallback.ATTESTATION
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks(username = user.username, password = user.password)
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("attestation-var-set")
        node = node.next() as ContinueNode

        val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
        deviceBindingCallback.bind()
            .onSuccess { token ->
                val jwtToken = JWTParser.parse(token)

                val signedJwtToken = jwtToken as SignedJWT
                assertEquals(RS512, signedJwtToken.header.jwk.algorithm)
                assertEquals(KeyUse.SIGNATURE, signedJwtToken.header.jwk.keyUse)
                assertFalse(signedJwtToken.header.jwk.x509CertChain.isNullOrEmpty())
                assertNotNull(signedJwtToken.header.keyID)
                assertNotNull(signedJwtToken.header.parsedBase64URL)

                assertEquals("com.pingidentity.journey.test", jwtToken.jwtClaimsSet.getClaim("iss"))
                assertEquals("android", jwtToken.jwtClaimsSet.getClaim("platform"))
                assertEquals(Build.VERSION.SDK_INT.toLong(), jwtToken.jwtClaimsSet.getClaim("android-version"))
                node = node.next() as ContinueNode

                val textCallback = node.callbacks.first() as TextOutputCallback
                assertEquals("Attestation var exists", textCallback.message)
            }
            .onFailure { error ->
                fail("testKeyAttestationTransientStateVariable failed with ${error.message}")
            }
    }

    /**
     * Verifies that the attestation transient state variable is null when Key Attestation is disabled.
     *
     * Tests that when Key Attestation is NOT enabled in the Device Binding node, no attestation validation
     * is performed and the transient variable DeviceBindingCallback.ATTESTATION remains null.
     */
    @Test
    fun testKeyAttestationTransientStateVariableNull() = runTest {
        val user = registerRandomUser()
        // Ensure that when Key Attestation toggle button is NOT enabled in the Device Binding node,
        // Key Attestation Validation will NOT be performed- transient variable DeviceBindingCallback.ATTESTATION should be null!
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks(username = user.username, password = user.password)
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("attestation-var-null")
        node = node.next() as ContinueNode

        val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
        deviceBindingCallback.bind()
            .onSuccess { token ->
                val jwtToken = JWTParser.parse(token)

                val signedJwtToken = jwtToken as SignedJWT
                assertEquals(RS512, signedJwtToken.header.jwk.algorithm)
                assertEquals(KeyUse.SIGNATURE, signedJwtToken.header.jwk.keyUse)
                assertTrue(signedJwtToken.header.jwk.x509CertChain.isNullOrEmpty())
                assertNotNull(signedJwtToken.header.keyID)
                assertNotNull(signedJwtToken.header.parsedBase64URL)

                node = node.next() as ContinueNode

                val textCallback = node.callbacks.first() as TextOutputCallback
                assertEquals("Attestation var DOES NOT exist", textCallback.message)
            }
            .onFailure { error ->
                fail("testKeyAttestationTransientStateVariable failed with ${error.message}")
            }
    }

    /**
     * Verifies device binding with APPLICATION_PIN authentication when Key Attestation is disabled.
     *
     * Tests that when authentication type is set to APPLICATION_PIN and Key Attestation is OFF,
     * device binding succeeds and the JWT does not include attestation data (x5c parameter).
     */
    @Test
    fun testKeyAttestationApplicationPinAttestationOff() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val user = registerRandomUser()
            // Test  that when authentication type is set to APPLICATION_PIN and Key Attestation is OFF, device binding outcome is 'success'...
            // Make sure that the SDK DOES NOT include attestation data in the JWT...
            var node = defaultJourney.start(tree) as ContinueNode
            node.handleLoginCallbacks(username = user.username, password = user.password)
            node = node.next() as ContinueNode

            val choiceCallback = node.callbacks.first() as ChoiceCallback
            choiceCallback.selectedIndex = choiceCallback.choices.indexOf("pin-attestation-off")
            node = node.next() as ContinueNode

            val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
            deviceBindingCallback.bind {
                appPinConfig {
                    pinCollector {
                        "1234".toCharArray()
                    }
                    prompt = Prompt(title = "App Pin", subtitle = "Enter your app pin", "Default Pin")
                }
            }.onSuccess { token ->
                val jwtToken = JWTParser.parse(token)

                val signedJwtToken = jwtToken as SignedJWT
                assertEquals(RS512, signedJwtToken.header.jwk.algorithm)
                assertEquals(KeyUse.SIGNATURE, signedJwtToken.header.jwk.keyUse)
                assertTrue(signedJwtToken.header.jwk.x509CertChain.isNullOrEmpty()) // When Android Key Attestation is set to NONE in AM
                assertNotNull(signedJwtToken.header.keyID)
                assertNotNull(signedJwtToken.header.parsedBase64URL)

                assertEquals("com.pingidentity.journey.test", jwtToken.jwtClaimsSet.getClaim("iss"))
                assertEquals("android", jwtToken.jwtClaimsSet.getClaim("platform"))
                assertEquals(Build.VERSION.SDK_INT.toLong(), jwtToken.jwtClaimsSet.getClaim("android-version"))
            }.onFailure { error ->
                fail("testKeyAttestationApplicationPinAttestationOff failed with ${error.message}")
            }
        }
    }

    /**
     * Verifies that APPLICATION_PIN with Key Attestation enabled fails appropriately.
     *
     * Tests that when authentication type is set to APPLICATION_PIN and Key Attestation is ON,
     * the device binding fails with an appropriate error message indicating lack of support.
     */
    @Test
    fun testKeyAttestationApplicationPinAttestationOn() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val user = registerRandomUser()

            var node = defaultJourney.start(tree) as ContinueNode
            node.handleLoginCallbacks(username = user.username, password = user.password)
            node = node.next() as ContinueNode

            val choiceCallback = node.callbacks.first() as ChoiceCallback
            choiceCallback.selectedIndex = choiceCallback.choices.indexOf("pin-attestation-on")
            node = node.next() as ContinueNode

            val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
            deviceBindingCallback.bind {
                appPinConfig {
                    pinCollector {
                        "1234".toCharArray()
                    }
                    prompt = Prompt(title = "App Pin", subtitle = "Enter your app pin", "Default Pin")
                }
            }.onSuccess {
                fail("testKeyAttestationApplicationPinAttestationOn failed")
            }.onFailure { exception ->
                assertEquals("Device does not support APPLICATION_PIN", exception.message)
            }
        }
    }
}