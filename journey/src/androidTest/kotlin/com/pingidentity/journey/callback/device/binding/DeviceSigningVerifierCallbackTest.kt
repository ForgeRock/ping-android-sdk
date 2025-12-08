/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback.device.binding

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nimbusds.jwt.JWTParser
import com.pingidentity.device.binding.authenticator.exception.InvalidClaimException
import com.pingidentity.device.binding.journey.DeviceBindingCallback
import com.pingidentity.device.binding.journey.DeviceSigningVerifierCallback
import com.pingidentity.journey.callback.ChoiceCallback
import com.pingidentity.journey.callback.NameCallback
import com.pingidentity.journey.callback.TextOutputCallback
import com.pingidentity.journey.module.session
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.start
import com.pingidentity.journey.utils.DeviceSkipRule
import com.pingidentity.journey.utils.RequiresDevice
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Calendar

/**
 * Test suite for DeviceSigningVerifierCallback functionality.
 * Tests device verification flows including JWT signing, custom claims,
 * error scenarios, and usernameless authentication.
 */
@RunWith(AndroidJUnit4::class)
class DeviceSigningVerifierCallbackTest : BaseDeviceBindingTest() {
    @get:Rule
    val deviceSkipRule = DeviceSkipRule()
    /**
     * Initializes the journey tree and sets up device binding before tests.
     * Binds a device once for all tests to avoid redundant setup.
     */
    @Before
    fun setupTree() = runTest {
        tree = "device-verifier"
        if (!setupBindingDevice) {
            println("Setting up a device binding")
            bindDevice()
            setupBindingDevice = true
        } else {
            println("Device binding already set up")
        }
    }

    /**
     * Verifies that the journey triggers a "Failure" outcome when an unknown user
     * attempts device verification (SDKS-2935).
     */
    @Test
    fun testDeviceSigningVerifierUnknownUserError() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val nameCallback = node.callbacks.first() as NameCallback
        nameCallback.name = "UNKNOWN-USER"
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("default")
        node = node.next() as ContinueNode

        val textOutputCallback =  node.callbacks.first() as TextOutputCallback
        assertEquals("Failure", textOutputCallback.message)
    }

    /**
     * Tests that DeviceSigningVerifierCallback returns default values
     * for title, subtitle, description, and timeout when no custom configuration is provided.
     */
    @Test
    fun testDeviceSigningVerifierCallbackDefaults() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val nameCallback = node.callbacks.first() as NameCallback
        nameCallback.name = USERNAME
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("default")
        node = node.next() as ContinueNode

        val deviceSigningVerifierCallback = node.callbacks.first() as DeviceSigningVerifierCallback
        assertNotNull(deviceSigningVerifierCallback.userId)
        assertNotNull(deviceSigningVerifierCallback.challenge)
        assertEquals("Authentication required", deviceSigningVerifierCallback.title)
        assertEquals("Cryptography device binding", deviceSigningVerifierCallback.subtitle)
        assertEquals("Please complete with biometric to proceed", deviceSigningVerifierCallback.description)
        assertEquals(60, deviceSigningVerifierCallback.timeout)

        // Set "Abort" outcome (without signing the challenge), so that the journey finishes...
        deviceSigningVerifierCallback.clientError = "Abort"
        node = node.next() as ContinueNode

        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        assertEquals("Abort", textOutputCallback.message)
    }

    /**
     * Tests that DeviceSigningVerifierCallback correctly uses custom values
     * for challenge, title, subtitle, description, and timeout.
     */
    @Test
    fun testDeviceSigningVerifierCallbackCustom() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val nameCallback = node.callbacks.first() as NameCallback
        nameCallback.name = USERNAME
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("custom")
        node = node.next() as ContinueNode

        val deviceSigningVerifierCallback = node.callbacks.first() as DeviceSigningVerifierCallback
        assertNotNull(deviceSigningVerifierCallback.userId)
        assertNotNull(deviceSigningVerifierCallback.challenge)

        assertEquals("my-hardcoded-challenge", deviceSigningVerifierCallback.challenge)
        assertEquals("Custom Title", deviceSigningVerifierCallback.title)
        assertEquals("Custom Subtitle", deviceSigningVerifierCallback.subtitle)
        assertEquals("Custom Description", deviceSigningVerifierCallback.description)
        assertEquals(0, deviceSigningVerifierCallback.timeout)

        deviceSigningVerifierCallback.clientError = "Custom"
        node = node.next() as ContinueNode

        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        assertEquals("Custom", textOutputCallback.message)
    }

    /**
     * Tests successful device verification with default JWT configuration.
     * Verifies that the generated JWT contains correct claims including kid, exp, iat, nbf,
     * challenge, and subject matching the userId.
     */
    @Test
    @RequiresDevice
    fun testDeviceVerificationSuccess() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val nameCallback = node.callbacks.first() as NameCallback
        nameCallback.name = USERNAME
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("default")
        node = node.next() as ContinueNode

        val deviceSigningVerifierCallback = node.callbacks.first() as DeviceSigningVerifierCallback
        assertNotNull(deviceSigningVerifierCallback.userId)
        assertNotNull(deviceSigningVerifierCallback.challenge)

        deviceSigningVerifierCallback.sign()
            .onSuccess { token ->
                // Verify the JWT attributes
                val nowMinus5 = Calendar.getInstance()
                val nowPlus5 = Calendar.getInstance()
                val expMin = Calendar.getInstance()
                val expMax = Calendar.getInstance()
                nowMinus5.add(Calendar.SECOND, -5)
                nowPlus5.add(Calendar.SECOND, 5)
                expMin.add(Calendar.SECOND, 55)
                expMax.add(Calendar.SECOND, 60)

                val jwtToken = JWTParser.parse(token)
                val jwtKid = jwtToken.header.toJSONObject()["kid"].toString()
                val jwtExpiry = jwtToken.jwtClaimsSet.expirationTime
                val jwtIat = jwtToken.jwtClaimsSet.issueTime
                val jwtNbf = jwtToken.jwtClaimsSet.notBeforeTime
                val jwtChallenge = jwtToken.jwtClaimsSet.getClaim("challenge")
                val jwtSub = jwtToken.jwtClaimsSet.subject

                assertEquals("kid not found", kid, jwtKid)
                assertEquals("User ID not equal", userId, jwtSub)
                assertTrue(jwtExpiry.after(expMin.time) && jwtExpiry.before(expMax.time))
                assertTrue(jwtIat.after(nowMinus5.time) && jwtIat.before(nowPlus5.time))
                assertTrue(jwtNbf.after(nowMinus5.time) && jwtNbf.before(nowPlus5.time))
                assertEquals(deviceSigningVerifierCallback.challenge, jwtChallenge)
            }.onFailure { error ->
                fail("testDeviceVerificationSuccess failed with ${error.message}")
            }
    }

    /**
     * Tests successful usernameless device verification flow.
     * Verifies that authentication works without providing a username,
     * using the device's kid as the subject in the JWT.
     */
    @Test
    @RequiresDevice
    @Ignore
    fun testDeviceVerificationUsernamelessSuccess() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        val choiceCallback = node.callbacks.first() as ChoiceCallback
        println(choiceCallback.choices)
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("usernameless")
        node = node.next() as ContinueNode

        if (node.callbacks.first() is TextOutputCallback) {
            node = node.next() as ContinueNode
            fail("usernameless should not show TextOutputCallback before DeviceSigningVerifierCallback")
        }

        val deviceSigningVerifierCallback = node.callbacks.first() as DeviceSigningVerifierCallback
        // In usernameless userId in the callback is empty...
        assertTrue(deviceSigningVerifierCallback.userId.isNullOrEmpty())
        assertNotNull(deviceSigningVerifierCallback.challenge)

        deviceSigningVerifierCallback.sign()
            .onSuccess { token ->
                // Verify the JWT attributes
                val nowMinus5 = Calendar.getInstance()
                val nowPlus5 = Calendar.getInstance()
                val expMin = Calendar.getInstance()
                val expMax = Calendar.getInstance()
                nowMinus5.add(Calendar.SECOND, -5)
                nowPlus5.add(Calendar.SECOND, 5)
                expMin.add(Calendar.SECOND, 55)
                expMax.add(Calendar.SECOND, 60)

                val jwtToken = JWTParser.parse(token)
                val jwtKid = jwtToken.header.toJSONObject()["kid"].toString()
                val jwtExpiry = jwtToken.jwtClaimsSet.expirationTime
                val jwtIat = jwtToken.jwtClaimsSet.issueTime
                val jwtNbf = jwtToken.jwtClaimsSet.notBeforeTime
                val jwtChallenge = jwtToken.jwtClaimsSet.getClaim("challenge")
                val jwtSub = jwtToken.jwtClaimsSet.subject

                assertEquals("kid not found", kid, jwtKid)
                assertEquals("User ID not equal", userId, jwtSub)
                assertTrue(jwtExpiry.after(expMin.time) && jwtExpiry.before(expMax.time))
                assertTrue(jwtIat.after(nowMinus5.time) && jwtIat.before(nowPlus5.time))
                assertTrue(jwtNbf.after(nowMinus5.time) && jwtNbf.before(nowPlus5.time))
                assertEquals(deviceSigningVerifierCallback.challenge, jwtChallenge)
            }.onFailure {
                fail("testDeviceVerificationUsernamelessSuccess failed with ${it.message}")
            }
    }

    /**
     * Tests device verification with a custom JWT expiration time.
     * Verifies that the exp claim in the JWT matches the custom expiration time provided.
     */
    @Test
    @RequiresDevice
    fun testDeviceVerificationSuccessCustomExp() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val nameCallback = node.callbacks.first() as NameCallback
        nameCallback.name = USERNAME
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("default")
        node = node.next() as ContinueNode

        val customDeviceSigningVerifierCallback = node.callbacks.first() as DeviceSigningVerifierCallback
        val seconds = Instant.now().plus(90, ChronoUnit.SECONDS)
        customDeviceSigningVerifierCallback.sign {
            expirationTime = { seconds }
        }.onSuccess { token ->
            val expMin = Calendar.getInstance()
            val expMax = Calendar.getInstance()
            expMin.add(Calendar.SECOND, 85)
            expMax.add(Calendar.SECOND, 90)

            val jwtToken = JWTParser.parse(token)
            val jwtKid = jwtToken.header.toJSONObject()["kid"].toString()
            val jwtExpiry = jwtToken.jwtClaimsSet.expirationTime
            val jwtChallenge = jwtToken.jwtClaimsSet.getClaim("challenge")
            val jwtSub = jwtToken.jwtClaimsSet.subject

            assertEquals("kid not found", kid, jwtKid)
            assertEquals("User ID not equal", userId, jwtSub)
            assertEquals("Challenge not matched", customDeviceSigningVerifierCallback.challenge, jwtChallenge)
            assertTrue("Expiration time not matched", jwtExpiry.after(expMin.time) && jwtExpiry.before(expMax.time))
        }.onFailure { error ->
            fail("testDeviceVerificationSuccessCustomExp failed with ${error.message}")
        }
    }

    /**
     * Tests that device verification fails when the JWT has already expired.
     * Creates a JWT with an expiration time in the past and verifies the error response.
     */
    @Test
    fun testDeviceVerificationFailureExpiredJwt() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val nameCallback = node.callbacks.first() as NameCallback
        nameCallback.name = USERNAME
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("default")
        node = node.next() as ContinueNode

        val deviceSigningVerifierCallback = node.callbacks.first() as DeviceSigningVerifierCallback
        deviceSigningVerifierCallback.sign {
            // Make the exp value to 2 minute in the past
            expirationTime = { Instant.now().minus(120, ChronoUnit.SECONDS) }
        }.onSuccess { token ->
            assertNotNull(token)
            node = node.next() as ContinueNode

            val textOutputCallback = node.callbacks.first() as TextOutputCallback
            assertEquals("Failure", textOutputCallback.message)
        }.onFailure { error ->
            fail("testDeviceVerificationSuccessCustomExp failed with ${error.message}")
        }
    }

    /**
     * Tests that device verification fails when the app ID doesn't match.
     * Verifies that the server returns a 401 Unauthorized error for mismatched app IDs.
     */
    @Test
    fun testDeviceSigningVerifierNonMatchingAppIdError() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val nameCallback = node.callbacks.first() as NameCallback
        nameCallback.name = USERNAME
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("wrong-app-id")

        if (node.next() is ErrorNode) {
            val errorNode = node.next() as ErrorNode
            assertNotNull(errorNode.message)
            assertEquals("Login failure", errorNode.message)
            assertEquals("401", errorNode.input["code"].toString())
            assertEquals("\"Unauthorized\"", errorNode.input["reason"].toString())
            assertEquals("\"Login failure\"", errorNode.input["message"].toString())
        } else {
            node = node.next() as ContinueNode
            val deviceSigningVerifierCallback = node.callbacks.first() as DeviceSigningVerifierCallback
            deviceSigningVerifierCallback.sign()
                .onSuccess {
                    val errorNode = node.next() as ErrorNode
                    assertNotNull(errorNode.message)
                    assertEquals("Login failure", errorNode.message)
                    assertEquals("401", errorNode.input["code"].toString())
                    assertEquals("\"Unauthorized\"", errorNode.input["reason"].toString())
                    assertEquals("\"Login failure\"", errorNode.input["message"].toString())
                }.onFailure { error ->
                    fail("testDeviceSigningVerifierNonMatchingAppIdError failed with ${error.message}")
                }
        }
    }

    /**
     * Tests adding custom claims to the JWT during device verification.
     * Verifies that custom claims are correctly added and can be retrieved from the JWT.
     */
    @Test
    fun testDeviceVerificationCustomClaims() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val nameCallback = node.callbacks.first() as NameCallback
        nameCallback.name = USERNAME
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("custom-claims")
        node = node.next() as ContinueNode

        val deviceSigningVerifierCallback = node.callbacks.first() as DeviceSigningVerifierCallback
        deviceSigningVerifierCallback.sign {
            claims {
                put("foo", "bar")
                put("num", 5)
                put("isGood", true)
            }
        }.onSuccess { token ->
            val jwtToken = JWTParser.parse(token)
            val foo = jwtToken.jwtClaimsSet.getStringClaim("foo")
            assertEquals("bar", foo)
            val num = jwtToken.jwtClaimsSet.getIntegerClaim("num")
            assertEquals(5, num)
            val isGood = jwtToken.jwtClaimsSet.getBooleanClaim("isGood")
            assertTrue(isGood)
        }.onFailure {
            fail("testDeviceVerificationCustomClaims failed with ${it.message}")
        }
    }

    /**
     * Tests that using reserved claim names in custom claims throws InvalidClaimException.
     * Reserved names include: sub, exp, iat, nbf, iss, challenge.
     */
    @Test
    fun testDeviceVerificationInvalidCustomClaims() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val nameCallback = node.callbacks.first() as NameCallback
        nameCallback.name = USERNAME
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("custom-claims")
        node = node.next() as ContinueNode

        val deviceSigningVerifierCallback = node.callbacks.first() as DeviceSigningVerifierCallback
        deviceSigningVerifierCallback.sign {
            claims {
                put("iss", "foo")
            }
        }.onSuccess {
            fail("testDeviceVerificationInvalidCustomClaims should have thrown an exception")
        }.onFailure { exception ->
            val invalidClaimException = exception as? InvalidClaimException
            assertTrue(exception is InvalidClaimException)
            assertEquals("Custom claims contains reserved names: [sub, exp, iat, nbf, iss, challenge]", invalidClaimException?.message)
        }
    }

    /**
     * Tests that device verification fails for inactive users.
     * Verifies that the server returns a 401 Unauthorized error.
     */
    @Test
    fun testDeviceVerificationInactiveUser() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val nameCallback = node.callbacks.first() as NameCallback
        nameCallback.name = USERNAME
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("inactive-user")
        val errorNode = node.next() as ErrorNode
        assertNotNull(errorNode.message)
        assertEquals("Login failure", errorNode.message)
        assertEquals("401", errorNode.input["code"].toString())
        assertEquals("\"Unauthorized\"", errorNode.input["reason"].toString())
        assertEquals("\"Login failure\"", errorNode.input["message"].toString())
    }

    /**
     * Binds a device to the user and store the KID.
     */
    private suspend fun bindDevice() {
        val user  = registerRandomUser()
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks(username = user.username, password = user.password)
        node = node.next() as ContinueNode

        val nameCallback = node.callbacks.first() as NameCallback
        nameCallback.name = user.username
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("bind")
        node = node.next() as ContinueNode

        val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
        deviceBindingCallback.bind()
            .onSuccess { token ->
                val jwtToken = JWTParser.parse(token)
                kid = jwtToken.header.toJSONObject()["kid"].toString()
                userId = deviceBindingCallback.userId

                defaultJourney.session()?.let {
                    defaultJourney.signOff()
                }
            }.onFailure { error ->
                assertTrue("bindDevice failed with ${error.message}", false)
            }
    }


    companion object {
        private var setupBindingDevice = false

        /** The key identifier (KID) from the bound device */
        private var kid: String = ""
        /** The user identifier associated with the bound device */
        private var userId: String = ""
    }
}