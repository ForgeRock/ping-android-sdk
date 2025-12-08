/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback.device.binding

import com.pingidentity.device.binding.UserKey
import com.pingidentity.device.binding.UserKeysStorage
import com.pingidentity.device.binding.journey.DeviceSigningVerifierCallback
import com.pingidentity.journey.callback.ChoiceCallback
import com.pingidentity.journey.callback.NameCallback
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.start
import com.pingidentity.journey.utils.DeviceSkipRule
import com.pingidentity.journey.utils.RequiresDevice
import com.pingidentity.orchestrate.ContinueNode
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Test suite for device binding list and unbind operations.
 * Tests various device binding configurations including biometric and PIN-based authentication.
 */
class DeviceBindingListAndUnbindTest : BaseDeviceBindingTest() {
    @get:Rule
    val deviceSkipRule = DeviceSkipRule()

    private val userStorage = UserKeysStorage()
    /**
     * Initializes the journey tree and configures the test server before each test.
     */
    @Before
    fun setupTree() {
        tree = "device-verifier"
    }



    /**
     * Tests the complete device binding flow with PIN authentication.
     * Verifies that a device can be successfully bound using app PIN authentication.
     */
    @Test
    @RequiresDevice
    fun testBindDeviceFlow() = runTest {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(30000) {
                bindDevice(
                    configType = ConfigType.BIND_PIN,
                    storage = userStorage,
                )
                bindDevice(
                    configType = ConfigType.BIND,
                    storage = userStorage,
                )

                assertTrue(userStorage.findAll().isNotEmpty())
                assertEquals(1, userStorage.findAll().size)

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
                deviceSigningVerifierCallback.sign()
                    .onSuccess { node = node.next() as ContinueNode }
                    .onFailure { fail("testBindDeviceFlow failed with ${it.message}") }

                val userKeyList = userStorage.findAll()
                val userKeyNone: UserKey? = userKeyList.find { it.authType.name == "NONE" }
                assertNotNull(userKeyNone)

                userStorage.delete(userKeyNone!!)
                assertEquals(0, userStorage.findAll().size)
            }
        }
    }
}