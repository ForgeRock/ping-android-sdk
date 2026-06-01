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
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
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
        val collector = PasswordCollector()
        collector.init(inputWithPasswordPolicy(buildJsonObject {
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
        }))
        collector.value = "Valid1@Password"
        assertEquals(emptyList(), collector.validate())
    }

    @Test
    fun addsInvalidLengthErrorWhenValueTooShort() {
        val collector = PasswordCollector()
        collector.init(inputWithPasswordPolicy(buildJsonObject {
            put("length", buildJsonObject {
                put("min", 8)
                put("max", 20)
            })
        }))
        collector.value = "Short1@"
        assertEquals(listOf(InvalidLength(8, 20)), collector.validate())
    }

    @Test
    fun addsUniqueCharacterErrorWhenNotEnoughUniqueCharacters() {
        val collector = PasswordCollector()
        collector.init(inputWithPasswordPolicy(buildJsonObject {
            put("minUniqueCharacters", 5)
        }))
        collector.value = "aaa111@@@"
        assertEquals(listOf(UniqueCharacter(5)), collector.validate())
    }

    @Test
    fun addsMaxRepeatErrorWhenTooManyRepeatedCharacters() {
        val collector = PasswordCollector()
        collector.init(inputWithPasswordPolicy(buildJsonObject {
            put("maxRepeatedCharacters", 2)
        }))
        collector.value = "aaabbbccc"
        assertEquals(listOf(MaxRepeat(2)), collector.validate())
    }

    @Test
    fun addsMinCharactersErrorWhenNotEnoughDigits() {
        val collector = PasswordCollector()
        collector.init(inputWithPasswordPolicy(buildJsonObject {
            put("minCharacters", buildJsonObject {
                put("0123456789", 2)
            })
        }))
        collector.value = "Password@1"
        assertEquals(listOf(MinCharacters("0123456789", 2)), collector.validate())
    }

    @Test
    fun validatesSuccessfullyWhenEnoughSpecialCharacters() {
        val collector = PasswordCollector()
        collector.init(inputWithPasswordPolicy(buildJsonObject {
            put("minCharacters", buildJsonObject {
                put("!@#$%^&*()", 2)
            })
        }))
        collector.value = "Password1!&"
        assertTrue { collector.validate().isEmpty() }
    }

    @Test
    fun `should initialize field properties from init`() {
        val input = buildJsonObject {
            put("type", "PASSWORD_VERIFY")
            put("key", "user.password")
            put("label", "Password")
            put("passwordPolicy", buildJsonObject {})
        }
        val collector = PasswordCollector()
        collector.init(input)
        assertEquals("user.password", collector.key)
        assertEquals("Password", collector.label)
        assertEquals("PASSWORD_VERIFY", collector.type)
    }

    @Test
    fun `passwordPolicy is read from PASSWORD_VERIFY field when form contains multiple field types`() {
        val collector = PasswordCollector()
        collector.init(inputWithPasswordPolicy(buildJsonObject {
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
        }))
        // A valid password satisfying all constraints should produce no errors
        collector.value = "Valid1@Pass"
        assertEquals(emptyList(), collector.validate())
        // A password that is too short should produce an InvalidLength error
        collector.value = "Sh0rt!"
        assertEquals(listOf(InvalidLength(8, 255)), collector.validate())
    }

    @Test
    fun `passwordPolicy falls back to global scope when not present in field`() {
        val globalInput = buildJsonObject {
            put("passwordPolicy", buildJsonObject {
                put("length", buildJsonObject {
                    put("min", 8)
                    put("max", 20)
                })
            })
        }
        val collector = PasswordCollector()
        // init called without a passwordPolicy in the field definition
        collector.init(buildJsonObject {
            put("type", "PASSWORD_VERIFY")
            put("key", "user.password")
        })
        collector.continueNode = continueNode
        every { continueNode.input }.returns(globalInput)
        collector.value = "Short1@"
        assertEquals(listOf(InvalidLength(8, 20)), collector.validate())
    }

    // Regression: when no passwordPolicy exists in field or global scope, no errors are raised
    @Test
    fun `no validation errors when no passwordPolicy in field or global scope`() {
        val collector = PasswordCollector()
        collector.init(buildJsonObject {
            put("type", "PASSWORD_VERIFY")
            put("key", "user.password")
        })
        collector.continueNode = continueNode
        every { continueNode.input }.returns(buildJsonObject {})
        collector.value = "any"
        assertEquals(emptyList(), collector.validate())
    }

    /**
     * Returns a PASSWORD_VERIFY field JSON object with the given [policy] embedded,
     * mirroring the shape the server sends inside `form.components.fields[*]`.
     */
    private fun inputWithPasswordPolicy(policy: JsonObject) = buildJsonObject {
        put("type", "PASSWORD_VERIFY")
        put("key", "user.password")
        put("label", "Password")
        put("passwordPolicy", policy)
    }
}