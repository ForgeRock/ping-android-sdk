/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import androidx.test.filters.SmallTest
import com.pingidentity.davinci.collector.FlowCollector
import com.pingidentity.davinci.collector.InvalidLength
import com.pingidentity.davinci.collector.Length
import com.pingidentity.davinci.collector.MinCharacters
import com.pingidentity.davinci.collector.PasswordCollector
import com.pingidentity.davinci.collector.RegexError
import com.pingidentity.davinci.collector.Required
import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.davinci.collector.UniqueCharacter
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.plugin.collectors
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SmallTest
class FormFieldValidationTest {
    private var daVinci = DaVinci {
        logger = Logger.STANDARD

        module(Oidc) {
            clientId = "60de77d5-dd2c-41ef-8c40-f8bb2381a359"
            discoveryEndpoint = "https://auth.pingone.ca/02fb4743-189a-4bc7-9d6c-a919edfe6447/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = "org.forgerock.demo://oauth2redirect"
            //storage = dataStore
        }
    }

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @BeforeTest
    fun setUp() = runTest {
    }

    @TestRailCase(26028, 26030, 26031)
    @Test
    fun textFieldValidationTest() = runTest {
        // Go to the "Form Fields Validation" form
        var node = daVinci.start() as ContinueNode
        (node.collectors[1] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Username filed...
        assertTrue(node.collectors[1] is TextCollector)
        val username = node.collectors[1] as TextCollector

        // Assert the properties of the Username field
        assertEquals("Username", username.label)
        assertEquals("user.username", username.key)
        assertEquals("default username", username.value)
        assertEquals(true, username.required)
        assertEquals("^[a-zA-Z0-9]+$", username.validation?.regex.toString())
        assertEquals("Must be alphanumeric", username.validation?.errorMessage ?: "")

        // Change the value of the username field to empty
        username.value = ""

        // Validate should return list with 2 validation errors since the value is empty
        // and does not match the configured regex
        var usernameValidationResult = username.validate()
        assertTrue(usernameValidationResult.size == 2)
        assertEquals("Required", usernameValidationResult[0].toString())
        assertEquals("Must be alphanumeric", (usernameValidationResult[1] as RegexError).message)

        username.value = "user123"
        usernameValidationResult = username.validate() // Should return empty list this time
        assertTrue(usernameValidationResult.isEmpty())

        // Email field...
        assertTrue(node.collectors[2] is TextCollector)
        val email = node.collectors[2] as TextCollector

        // Assert the properties of the Username field
        assertEquals("Email Address", email.label)
        assertEquals("user.email", email.key)
        assertEquals("default email", email.value)
        assertEquals(true, email.required)
        assertEquals("^[^@]+@[^@]+\\.[^@]+$", email.validation?.regex.toString())
        assertEquals("Not a valid email", email.validation?.errorMessage ?: "")

        // Change the value of the email field to empty
        email.value = ""

        // Validate should return list with 2 validation errors since the value is empty
        // and does not match the configured regex
        var emailValidationResult = email.validate()
        assertTrue(emailValidationResult.size == 2)
        assertEquals("Required", emailValidationResult[0].toString())
        assertEquals("Not a valid email", (emailValidationResult[1] as RegexError).message)

        email.value = "not an email"
        emailValidationResult = email.validate() // Should return 1 validation error this time
        assertTrue(emailValidationResult.size == 1)
        assertEquals("Not a valid email", (emailValidationResult[0] as RegexError).message)

        email.value = "valid@email.com"
        emailValidationResult = email.validate() // Should return empty list this time
        assertTrue(emailValidationResult.isEmpty())
    }

    @TestRailCase(26034, 26031)
    @Test
    fun passwordValidationTest() = runTest {
        // Go to the "Form Fields Validation" form
        var node = daVinci.start() as ContinueNode
        (node.collectors[1] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Password filed...
        assertTrue(node.collectors[3] is PasswordCollector)
        val password = node.collectors[3] as PasswordCollector
        //TODO PasswordPolicy
        /*
        val passwordPolicy = password.passwordPolicy()

        // Assert the password policy
        assertTrue(passwordPolicy?.default ?: false)
        assertEquals("Standard", passwordPolicy?.name)
        assertEquals("A standard policy that incorporates industry best practices", passwordPolicy?.description)
        assertEquals(Length(min=8, max=255), passwordPolicy?.length)
        assertEquals(5, passwordPolicy?.minUniqueCharacters)
        assertTrue(passwordPolicy?.minCharacters?.containsKey("0123456789") ?: false)
        assertTrue(passwordPolicy?.minCharacters?.containsKey("ABCDEFGHIJKLMNOPQRSTUVWXYZ") ?: false)
        assertTrue(passwordPolicy?.minCharacters?.containsKey("abcdefghijklmnopqrstuvwxyz") ?: false)
        assertTrue(passwordPolicy?.minCharacters?.containsKey("~!@#$%^&*()-_=+[]{}|;:,.<>/?") ?: false)

        // Assert the properties of the Password field
        assertEquals("Password", password.label)
        assertEquals("PASSWORD_VERIFY", password.type)
        assertEquals("user.password", password.key)
        assertEquals("default password", password.value)
        assertEquals(true, password.required)

        // Clear the password field
        password.value = ""

        // Validate should return list with 2 validation errors since the value is empty
        // and does not match the configured regex
        var passwordValidationResult = password.validate()

        // The default password policy is:
        assertTrue(passwordValidationResult.size == 7)
        assertTrue(passwordValidationResult.contains(Required))
        assertTrue(passwordValidationResult.contains(InvalidLength(min=8, max=255)))
        assertTrue(passwordValidationResult.contains(UniqueCharacter(min=5)))
        assertTrue(passwordValidationResult.contains(MinCharacters(character="~!@#$%^&*()-_=+[]{}|;:,.<>/?", min=1)))
        assertTrue(passwordValidationResult.contains(MinCharacters(character="0123456789", min=1)))
        assertTrue(passwordValidationResult.contains(MinCharacters(character="ABCDEFGHIJKLMNOPQRSTUVWXYZ", min=1)))
        assertTrue(passwordValidationResult.contains(MinCharacters(character="abcdefghijklmnopqrstuvwxyz", min=1)))

        // Set password that meets some of the policy requirements
        password.value = "password123"
        passwordValidationResult = password.validate()

        assertTrue(passwordValidationResult.size == 2)
        assertFalse(passwordValidationResult.contains(Required)) // Should not contain Required error
        assertFalse(passwordValidationResult.contains(InvalidLength(min=8, max=255))) // Should not contain InvalidLength error
        assertFalse(passwordValidationResult.contains(UniqueCharacter(min=5))) // Should not contain UniqueCharacter error
        assertTrue(passwordValidationResult.contains(MinCharacters(character="~!@#$%^&*()-_=+[]{}|;:,.<>/?", min=1)))
        assertFalse(passwordValidationResult.contains(MinCharacters(character="0123456789", min=1))) // Should not contain this error
        assertTrue(passwordValidationResult.contains(MinCharacters(character="ABCDEFGHIJKLMNOPQRSTUVWXYZ", min=1)))
        assertFalse(passwordValidationResult.contains(MinCharacters(character="abcdefghijklmnopqrstuvwxyz", min=1))) // Should not contain this error

        // Set password that meets all of the policy requirements
        password.value = "Password123!"
        passwordValidationResult = password.validate()

        // Should return empty list this time
        assertTrue(passwordValidationResult.isEmpty())
         */
    }

    @TestRailCase(27507)
    @Test
    fun errorNodeTest() = runTest {
        // Go to the "Error Node" form
        var node = daVinci.start() as ContinueNode
        (node.collectors[2] as? FlowCollector)?.value = "click"
        val errorNode = node.next()
        assertTrue(errorNode is ErrorNode)

        assertEquals("400", errorNode.input["code"].toString())
        assertEquals("Error message from error node", errorNode.message.trim())
    }
}