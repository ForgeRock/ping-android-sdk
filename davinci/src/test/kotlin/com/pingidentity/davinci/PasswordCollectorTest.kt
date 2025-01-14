/*
 * Copyright (c) 2024 - 2025 Ping Identity. All rights reserved.
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
import kotlinx.serialization.json.JsonPrimitive
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
        val input = buildJsonObject {
            put("passwordPolicy", buildJsonObject {
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
        }
        val collector = PasswordCollector()
        collector.continueNode = continueNode
        coEvery { continueNode.input }.returns(input)
        collector.value = "Valid1@Password"
        assertEquals(emptyList(), collector.validate())
    }

    @Test
    fun addsInvalidLengthErrorWhenValueTooShort() {
        val input = buildJsonObject {
            put("passwordPolicy", buildJsonObject {
                put("length", buildJsonObject {
                    put("min", 8)
                    put("max", 20)
                })
            })
        }
        val collector = PasswordCollector()
        collector.continueNode = continueNode
        coEvery { continueNode.input }.returns(input)
        collector.value = "Short1@"
        assertEquals(listOf(InvalidLength(8, 20)), collector.validate())
    }

    @Test
    fun addsUniqueCharacterErrorWhenNotEnoughUniqueCharacters() {
        val input = buildJsonObject {
            put("passwordPolicy", buildJsonObject {
                put("minUniqueCharacters", 5)
            })
        }
        val collector = PasswordCollector()
        collector.continueNode = continueNode
        coEvery { continueNode.input }.returns(input)
        collector.value = "aaa111@@@"
        assertEquals(listOf(UniqueCharacter(5)), collector.validate())
    }

    @Test
    fun addsMaxRepeatErrorWhenTooManyRepeatedCharacters() {
        val input = buildJsonObject {
            put("passwordPolicy", buildJsonObject {
                put("maxRepeatedCharacters", 2)
            })
        }
        val collector = PasswordCollector()
        collector.continueNode = continueNode
        coEvery { continueNode.input }.returns(input)
        collector.value = "aaabbbccc"
        assertEquals(listOf(MaxRepeat(2)), collector.validate())
    }

    @Test
    fun addsMinCharactersErrorWhenNotEnoughDigits() {
        val input = buildJsonObject {
            put("passwordPolicy", buildJsonObject {
                put("minCharacters", buildJsonObject {
                    put("0123456789", 2)
                })
            })
        }
        val collector = PasswordCollector()
        collector.continueNode = continueNode
        coEvery { continueNode.input }.returns(input)
        collector.value = "Password@1"
        assertEquals(listOf(MinCharacters("0123456789", 2)), collector.validate())
    }

    @Test
    fun addsMinCharactersErrorWhenEnoughSpecialCharacters() {
        val input = buildJsonObject {
            put("passwordPolicy", buildJsonObject {
                put("minCharacters", buildJsonObject {
                    put("!@#$%^&*()", 2)
                })
            })
        }
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

}