/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.InvalidLength
import com.pingidentity.davinci.collector.MaxRepeat
import com.pingidentity.davinci.collector.MinCharacters
import com.pingidentity.davinci.collector.PasswordCollector
import com.pingidentity.davinci.collector.UniqueCharacter
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasswordCollectorTest {

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher
    lateinit var continueNode: ContinueNode

    @BeforeTest
    fun setUp() {
        continueNode = mockk()
    }

    @TestRailCase(21259)
    @Test
    fun `close should clear password when clearPassword is true`() {
        val passwordCollector = PasswordCollector()
        passwordCollector.value = "password"
        passwordCollector.clearPassword = true

        passwordCollector.close()

        assertEquals("", passwordCollector.value)
    }

    @TestRailCase(22559)
    @Test
    fun `close should not clear password when clearPassword is false`() {
        val passwordCollector = PasswordCollector()
        passwordCollector.value = "password"
        passwordCollector.clearPassword = false

        passwordCollector.close()

        assertEquals("password", passwordCollector.value)
    }

    @Test
    fun validatesSuccessfullyWhenNoErrors() {
        val input = inputWithPasswordPolicy(buildJsonObject {
            put("length", buildJsonObject {
                put("min", 8)
                put("max", 20)
            })
            put("minUniqueCharacters", 3)
            put("maxRepeatedCharacters", 2)
            put("minCharacters", buildJsonObject {
                put("0123456789", 1)
                put("!@#$%^&*()", 1)
            })
        })
        val collector = PasswordCollector()
        collector.continueNode = continueNode
        coEvery { continueNode.input }.returns(input)
        collector.value = "Valid1@Password"
        assertEquals(emptyList(), collector.validate())
    }

    @Test
    fun addsInvalidLengthErrorWhenValueTooShort() {
        val input = inputWithPasswordPolicy(buildJsonObject {
            put("length", buildJsonObject {
                put("min", 8)
                put("max", 20)
            })
        })
        val collector = PasswordCollector()
        collector.continueNode = continueNode
        coEvery { continueNode.input }.returns(input)
        collector.value = "Short1@"
        assertEquals(listOf(InvalidLength(8, 20)), collector.validate())
    }

    @Test
    fun addsUniqueCharacterErrorWhenNotEnoughUniqueCharacters() {
        val input = inputWithPasswordPolicy(buildJsonObject {
            put("minUniqueCharacters", 5)
        })
        val collector = PasswordCollector()
        collector.continueNode = continueNode
        coEvery { continueNode.input }.returns(input)
        collector.value = "aaa111@@@"
        assertEquals(listOf(UniqueCharacter(5)), collector.validate())
    }

    @Test
    fun addsMaxRepeatErrorWhenTooManyRepeatedCharacters() {
        val input = inputWithPasswordPolicy(buildJsonObject {
                put("maxRepeatedCharacters", 2)
            }
        )
        val collector = PasswordCollector()
        collector.continueNode = continueNode
        coEvery { continueNode.input }.returns(input)
        collector.value = "aaabbbccc"
        assertEquals(listOf(MaxRepeat(2)), collector.validate())
    }

    @Test
    fun addsMinCharactersErrorWhenNotEnoughDigits() {
        val input = inputWithPasswordPolicy(buildJsonObject {
            put("minCharacters", buildJsonObject {
                put("0123456789", 2)
            })
        })
        val collector = PasswordCollector()
        collector.continueNode = continueNode
        coEvery { continueNode.input }.returns(input)
        collector.value = "Password@1"
        assertEquals(listOf(MinCharacters("0123456789", 2)), collector.validate())
    }

    @Test
    fun validatesSuccessfullyWhenEnoughSpecialCharacters() {
        val input = inputWithPasswordPolicy(buildJsonObject {
            put("minCharacters", buildJsonObject {
                put("!@#$%^&*()", 2)
            })
        })
        val collector = PasswordCollector()
        collector.continueNode = continueNode
        coEvery { continueNode.input }.returns(input)
        collector.value = "Password1!&"
        assertTrue { collector.validate().isEmpty() }
    }

    @Test
    fun `should initialize default value`() {
        val input = JsonPrimitive("test")
        val collector = PasswordCollector()
        collector.init(input)
        assertEquals("test", collector.value)
    }

    @Test
    fun `passwordPolicy is read from PASSWORD_VERIFY field when form contains multiple field types`() {
        val input = buildJsonObject {
            put("form", buildJsonObject {
                put("components", buildJsonObject {
                    put("fields", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "LABEL")
                            put("content", "Simple Registration Form")
                            put("key", "rich-text")
                        })
                        add(buildJsonObject {
                            put("type", "TEXT")
                            put("key", "user.username")
                            put("label", "Username")
                        })
                        add(buildJsonObject {
                            put("type", "TEXT")
                            put("key", "user.email")
                            put("label", "Email Address")
                        })
                        add(buildJsonObject {
                            put("type", "PASSWORD_VERIFY")
                            put("key", "user.password")
                            put("label", "Password")
                            put("passwordPolicy", buildJsonObject {
                                put("length", buildJsonObject {
                                    put("min", 8)
                                    put("max", 255)
                                })
                                put("minUniqueCharacters", 5)
                                put("maxRepeatedCharacters", 2)
                                put("minCharacters", buildJsonObject {
                                    put("0123456789", 1)
                                    put("ABCDEFGHIJKLMNOPQRSTUVWXYZ", 1)
                                    put("abcdefghijklmnopqrstuvwxyz", 1)
                                    put("~!@#\$%^&*()-_=+[]{}|;:,.<>/?", 1)
                                })
                            })
                        })
                        add(buildJsonObject {
                            put("type", "SUBMIT_BUTTON")
                            put("label", "Submit")
                            put("key", "submit")
                        })
                    })
                })
            })
        }
        val collector = PasswordCollector()
        collector.continueNode = continueNode
        coEvery { continueNode.input }.returns(input)
        // A valid password satisfying all constraints should produce no errors
        collector.value = "Valid1@Pass"
        assertEquals(emptyList(), collector.validate())
        // A password that is too short should produce an InvalidLength error
        collector.value = "Sh0rt!"
        assertEquals(listOf(InvalidLength(8, 255)), collector.validate())
    }

    // Regression: when no PASSWORD_VERIFY field is present, no policy is applied and no errors are raised
    @Test
    fun `no validation errors when form has no PASSWORD_VERIFY field`() {
        val input = buildJsonObject {
            put("form", buildJsonObject {
                put("components", buildJsonObject {
                    put("fields", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "TEXT")
                            put("key", "user.username")
                            put("label", "Username")
                        })
                        add(buildJsonObject {
                            put("type", "SUBMIT_BUTTON")
                            put("label", "Submit")
                            put("key", "submit")
                        })
                    })
                })
            })
        }
        val collector = PasswordCollector()
        collector.continueNode = continueNode
        coEvery { continueNode.input }.returns(input)
        collector.value = "any"
        assertEquals(emptyList(), collector.validate())
    }

    private fun inputWithPasswordPolicy(policy: JsonObject) = buildJsonObject {
        put("form", buildJsonObject {
            put("components", buildJsonObject {
                put("fields", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "PASSWORD_VERIFY")
                        put("passwordPolicy", policy)
                    })
                })
            })
        })
    }
}