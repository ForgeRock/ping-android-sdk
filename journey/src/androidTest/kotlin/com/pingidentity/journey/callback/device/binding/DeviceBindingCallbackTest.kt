/*
 * Copyright (c) 2025 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback.device.binding

import com.pingidentity.device.binding.authenticator.Attestation
import com.pingidentity.device.binding.authenticator.DeviceBindingAuthenticationType
import com.pingidentity.device.binding.journey.DeviceBindingCallback
import com.pingidentity.journey.BaseJourneyTest
import com.pingidentity.journey.callback.ChoiceCallback
import com.pingidentity.journey.callback.TextOutputCallback
import com.pingidentity.journey.module.session
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.start
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.SuccessNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class DeviceBindingCallbackTest : BaseJourneyTest() {

    /**
     * Initializes the journey tree and configures the test server before each test.
     */
    @Before
    fun setupTree() {
        tree = "device-bind"
    }

    @Test
    fun testDeviceBindingDefaults() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("default")
        node = node.next() as ContinueNode

        val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
        assertNotNull(deviceBindingCallback)
        assertNotNull(deviceBindingCallback.userId)
        assertTrue(deviceBindingCallback.userId.isNotEmpty())
        assertEquals(DeviceBindingAuthenticationType.BIOMETRIC_ALLOW_FALLBACK, deviceBindingCallback.deviceBindingAuthenticationType)
        assertNotNull(deviceBindingCallback.challenge)
        assertEquals("Authentication required", deviceBindingCallback.title)
        assertEquals("Cryptography device binding", deviceBindingCallback.subtitle)
        assertEquals("Please complete with biometric to proceed", deviceBindingCallback.description)
        assertEquals(60, deviceBindingCallback.timeout)
        assertEquals(Attestation.None, deviceBindingCallback.attestation)

        deviceBindingCallback.clientError = "Abort"
        assertTrue(node.next() is SuccessNode)
    }

    @Test
    fun testDeviceBindingCustom() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("custom")
        node = node.next() as ContinueNode

        val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
        assertNotNull(deviceBindingCallback)
        assertNotNull(deviceBindingCallback.userId)
        assertEquals(Attestation.None, deviceBindingCallback.attestation)
        assertEquals("Custom title", deviceBindingCallback.title)
        assertEquals("Custom subtitle", deviceBindingCallback.subtitle)
        assertEquals("Custom description", deviceBindingCallback.description)
        assertEquals(5, deviceBindingCallback.timeout)

        deviceBindingCallback.clientError = "Abort"
        assertTrue(node.next() is SuccessNode)
    }

    @Test
    fun testDeviceBindingBind() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("custom")
        node = node.next() as ContinueNode

        val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
        assertNotNull(deviceBindingCallback)
        deviceBindingCallback.bind {}.onSuccess {
            // Binding successful
            assertNotNull(it)
            assertFalse(it.isEmpty())
            node.next()
        }.onFailure { error ->
            // Binding failed
            assertTrue("Device binding failed with error: $error", false)
        }
    }

    @Test
    fun testDeviceBindingExceed() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        var choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("custom")
        node = node.next() as ContinueNode

        val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
        assertNotNull(deviceBindingCallback)
        deviceBindingCallback.bind {}.onSuccess {
            // Binding successful
            assertNotNull(it)
            assertFalse(it.isEmpty())
            node.next()
        }.onFailure { error ->
            // Binding failed
            assertTrue("Device binding failed with error: $error", false)
        }
        // Mandatory to logout the user
        defaultJourney.signOff()
        // Restart the journey
        node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("exceed-limit")
        node = node.next() as ContinueNode

        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        assertNotNull(textOutputCallback)
        assertEquals("Device Limit Exceeded", textOutputCallback.message)
    }

    @Test
    fun testDeviceBindingCustomOutcome() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("custom")
        node = node.next() as ContinueNode

        if (node.callbacks.find { it is DeviceBindingCallback } != null) {
            val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
            deviceBindingCallback.clientError = "Custom"
            return@runTest
        }

        val textOutputCallback = node.callbacks.first() as TextOutputCallback
        assertNotNull(textOutputCallback)
        assertEquals("Custom outcome triggered", textOutputCallback.message)

        assertNotNull(defaultJourney.session())
    }

    @Test
    fun testDeviceBindingApplicationPin() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {

            var node = defaultJourney.start(tree) as ContinueNode
            node.handleLoginCallbacks()
            node = node.next() as ContinueNode

            val choiceCallback = node.callbacks.first() as ChoiceCallback
            choiceCallback.selectedIndex = choiceCallback.choices.indexOf("pin")
            node = node.next() as ContinueNode

            val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
            assertNotNull(deviceBindingCallback)
            val result = deviceBindingCallback.bind {
                expirationTime = { Instant.now().plusSeconds(60)}
                appPinConfig {
                    pinCollector { prompt ->
                        "1234".toCharArray()
                    }

                }
            }
            // Handle the result synchronously for test purposes
            result.onSuccess { jws ->
                // Binding successful
                assertNotNull(jws)
                assertFalse(jws.isEmpty())
                node.next()
            }.onFailure { error ->
                assertTrue("Device binding failed with error: $error", false)
            }

            assertTrue(result.isSuccess)
            node.next()
        }
    }

    @Test
    fun testDeviceBindApplicationIdNotMatchingError() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("wrong-app-id")
        node = node.next() as ContinueNode

        val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
        assertNotNull(deviceBindingCallback)
        deviceBindingCallback.bind {
        }.onSuccess {
            node.next()
        }.onFailure { error ->
            assertTrue(error.message?.contains("Application ID does not match") == true)
        }

        val errorNode = node.next() as ErrorNode
        assertNotNull(errorNode)
        assertEquals(401, errorNode.input["code"]?.jsonPrimitive?.int)
        assertEquals("Unauthorized", errorNode.input["reason"]?.jsonPrimitive?.content)
        assertEquals("Login failure", errorNode.input["message"]?.jsonPrimitive?.content)
        assertEquals("Login failure", errorNode.message)
    }

    @Test
    fun testDeviceBindingDeviceDataVariable() = runTest {
        // This test is to ensure that the Device Binding node sets DeviceBinding.DEVICE variable in shared state
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks()
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("device-data-var")
        node = node.next() as ContinueNode

        val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
        assertNotNull(deviceBindingCallback)
        deviceBindingCallback.bind{ }.onSuccess { jws ->
            // Binding successful
            assertNotNull(jws)
            assertFalse(jws.isEmpty())
            node = node.next() as ContinueNode

            val textOutputCallback = node.callbacks.first() as TextOutputCallback
            assertNotNull(textOutputCallback)
            assertEquals("Device data variable exists", textOutputCallback.message)
        }.onFailure {
            assertTrue("Device binding failed with error: $it", false)
        }
    }

    @Test
    fun testDeviceBindingUnknownUser() = runTest {
        var node = defaultJourney.start(tree) as ContinueNode
        node.handleLoginCallbacks(username = "UNKNOWN_USER")
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf("default")
        val errorNode = node.next() as ErrorNode
        assertNotNull(errorNode)

        assertEquals(401, errorNode.input["code"]?.jsonPrimitive?.int)
        assertEquals("Unauthorized", errorNode.input["reason"]?.jsonPrimitive?.content)
        assertEquals("Login failure", errorNode.input["message"]?.jsonPrimitive?.content)
        assertEquals("Login failure", errorNode.message)
    }
}