/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback.device.binding

import com.pingidentity.device.binding.Prompt
import com.pingidentity.device.binding.UserKeysStorage
import com.pingidentity.device.binding.journey.DeviceBindingCallback
import com.pingidentity.journey.BaseJourneyTest
import com.pingidentity.journey.callback.ChoiceCallback
import com.pingidentity.journey.callback.KbaCreateCallback
import com.pingidentity.journey.callback.NameCallback
import com.pingidentity.journey.callback.PasswordCallback
import com.pingidentity.journey.callback.StringAttributeInputCallback
import com.pingidentity.journey.callback.TermsAndConditionsCallback
import com.pingidentity.journey.module.session
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.journey.start
import com.pingidentity.orchestrate.ContinueNode
import junit.framework.TestCase.assertTrue

/**
 * Base test class for device binding functionality.
 * Provides common setup and helper methods for device binding test scenarios.
 */
open class BaseDeviceBindingTest : BaseJourneyTest() {
    /**
     * Registers a new random user through the registration journey.
     * Creates a user with randomly generated credentials and completes all required registration steps
     * including username, password, attributes, KBA questions, and terms acceptance.
     *
     * @return RandomUser containing the generated username and password
     */
    internal suspend fun registerRandomUser(): RandomUser {
        val randomUser = "user" + System.currentTimeMillis()
        var node = defaultJourney.start("TEST_USER_REGISTRATION") as ContinueNode

        val userNameCallback = node.callbacks.first() as NameCallback
        userNameCallback.name = randomUser
        node = node.next() as ContinueNode

        val passwordCallback = node.callbacks.first() as PasswordCallback
        passwordCallback.password = randomUser
        node = node.next() as ContinueNode

        val attributeCallbacks = node.callbacks
        val nameAttributeCallback = attributeCallbacks[0] as StringAttributeInputCallback
        nameAttributeCallback.value = randomUser
        val snAttributeCallback = attributeCallbacks[1] as StringAttributeInputCallback
        snAttributeCallback.value = randomUser
        val emailAttributeCallback = attributeCallbacks[2] as StringAttributeInputCallback
        emailAttributeCallback.value = "$randomUser@example.com"
        node = node.next() as ContinueNode

        val firstCallback = node.callbacks.first() as KbaCreateCallback
        firstCallback.selectedQuestion = firstCallback.predefinedQuestions.first()
        firstCallback.selectedAnswer = "Test"

        if (node.callbacks.size > 1) {
            val secondQuestion = node.callbacks[1] as KbaCreateCallback
            secondQuestion.selectedQuestion = secondQuestion.predefinedQuestions[1]
            secondQuestion.selectedAnswer = "Test"
        }
        node = node.next() as ContinueNode

        val termsAndConditionsCallback = node.callbacks.first() as TermsAndConditionsCallback
        termsAndConditionsCallback.accepted = true
        node.next()

        return RandomUser(randomUser, randomUser)
    }

    /**
     * Binds a device using the specified authentication configuration.
     * Handles the complete binding flow including login, choice selection,
     * and device binding with either biometric or PIN authentication.
     *
     * @param configType The type of binding configuration (BIND or BIND_PIN)
     * @param storage The user key storage for device binding
     */
    internal suspend fun bindDevice(
        configType: ConfigType,
        storage: UserKeysStorage,
    ) {
        // Logout if not already done so.
        if (defaultJourney.session() != null) {
            defaultJourney.signOff()
        }

        var node = defaultJourney.start(tree) as ContinueNode

        val userNameFlowChoiceCallback = node.callbacks.first() as ChoiceCallback
        userNameFlowChoiceCallback.selectedIndex = userNameFlowChoiceCallback.choices.indexOf("collectusername")
        node = node.next() as ContinueNode
        node.handleLoginCallbacks()

        val nameCallback = node.callbacks.first() as NameCallback
        nameCallback.name = USERNAME
        node = node.next() as ContinueNode

        val choiceCallback = node.callbacks.first() as ChoiceCallback
        choiceCallback.selectedIndex = choiceCallback.choices.indexOf(configType.value)
        node = node.next() as ContinueNode
        when (configType) {
            ConfigType.BIND -> {
                val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
                deviceBindingCallback.bind {
                    userKeyStorage {
                        storage
                    }
                }.onSuccess { token ->
                    assertTrue(token.isNotEmpty())
                }.onFailure { error ->
                    assertTrue("Device binding failed with error: $error", false)
                }
            }
            ConfigType.BIND_PIN -> {
                val deviceBindingCallback = node.callbacks.first() as DeviceBindingCallback
                deviceBindingCallback.bind {
                    userKeyStorage {
                        storage
                    }
                    appPinConfig {
                        pinRetry = 3
                        pinCollector {
                            "1234".toCharArray()
                        }
                        prompt = Prompt("App Pin", "Enter your app pin", "App pin is required")
                    }
                }.onSuccess { token ->
                    assertTrue(token.isNotEmpty())
                }.onFailure { error ->
                    assertTrue("Device binding failed with error: $error", false)
                }
            }
        }
    }

    /**
     * Enum defining device binding configuration types.
     *
     * @property value The configuration identifier used in the journey
     */
    enum class ConfigType(val value: String) {
        BIND("bind"),
        BIND_PIN("bind-pin")
    }

    /**
     * Data class representing a randomly generated test user.
     *
     * @property username The generated username
     * @property password The generated password
     */
    data class RandomUser(
        val username: String,
        val password: String,
    )
}