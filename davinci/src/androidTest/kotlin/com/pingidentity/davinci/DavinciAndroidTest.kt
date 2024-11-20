/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import androidx.test.filters.SmallTest
import com.pingidentity.utils.Result
import com.pingidentity.davinci.collector.FlowCollector
import com.pingidentity.davinci.collector.PasswordCollector
import com.pingidentity.davinci.collector.SubmitCollector
import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.module.description
import com.pingidentity.davinci.module.name
import com.pingidentity.davinci.plugin.collectors
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SmallTest
class DavinciAndroidTest {
    private var daVinci = DaVinci {
        logger = Logger.STANDARD

        module(Oidc) {
            clientId = "021b83ce-a9b1-4ad4-8c1d-79e576eeab76"
            discoveryEndpoint = "https://auth.pingone.ca/02fb4743-189a-4bc7-9d6c-a919edfe6447/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = "org.forgerock.demo://oauth2redirect"
            //storage = dataStore
        }
    }

    private lateinit var userFname: String
    private lateinit var userLname: String
    private lateinit var usernamePrefix: String
    private lateinit var username: String
    private lateinit var password: String
    private lateinit var verificationCode: String

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @BeforeTest
    fun setUp() = runTest {
        userFname = "E2E"
        userLname = "User"
        usernamePrefix = "e2e"

        // This user must exist in PingOne...
        username = "e2euser@example.com"
        password = "Demo1234#1"
        verificationCode = "1234" // This is hardcoded value in the DaVinci flow

        //Start with a clean session
        daVinci.user()?.logout()
    }

    @TestRailCase(21274)
    @Test
    fun loginSuccess() = runTest {
        var node = daVinci.start() // Return first Node
        assertTrue(node is ContinueNode)

        // Login form validation...
        assertTrue(node.collectors.size == 5 )

        assertTrue(node.collectors[0] is TextCollector)
        assertTrue(node.collectors[1] is PasswordCollector)
        assertTrue(node.collectors[2] is SubmitCollector)
        assertTrue(node.collectors[3] is FlowCollector)
        assertTrue(node.collectors[4] is FlowCollector)

        assertEquals("E2E Login Form", node.name)
        assertEquals("Enter your username and password", node.description)
        assertEquals("Username", (node.collectors[0] as TextCollector).label)
        assertEquals("Password", (node.collectors[1] as PasswordCollector).label)
        assertEquals("Sign On", (node.collectors[2] as SubmitCollector).label)
        assertEquals("No account? Register now!", (node.collectors[3] as FlowCollector).label)
        assertEquals("Having trouble signing on?", (node.collectors[4] as FlowCollector).label)

        // Fill in the login form with valid credentials and submit...
        (node.collectors[0] as? TextCollector)?.value = username
        (node.collectors[1] as? PasswordCollector)?.value = password
        (node.collectors[2] as? SubmitCollector)?.value = "Sign On"

        node = node.next()
        node = node as ContinueNode

        assertTrue(node.collectors.size == 3)

        assertTrue(node.collectors[0] is SubmitCollector)
        assertTrue(node.collectors[1] is FlowCollector)
        assertTrue(node.collectors[2] is FlowCollector)

        assertEquals("Successful login", node.name)
        assertEquals("Successfully logged in to DaVinci", node.description)
        assertEquals("Continue", (node.collectors[0] as SubmitCollector).label)
        assertEquals("Reset password...", (node.collectors[1] as FlowCollector).label)
        assertEquals("Delete user...", (node.collectors[2] as FlowCollector).label)
        // Click continue
        (node.collectors[0] as? SubmitCollector)?.value = "Continue"

        node = node.next()
        assertTrue(node is SuccessNode)

        // Make sure the user is not null
        val user = node.user
        assertNotNull((user.token() as Result.Success).value.accessToken)

        val u = daVinci.user()
        u?.logout() ?: throw Exception("User is null")

        //After logout make sure the user is null
        assertNull(daVinci.user())
    }

    @TestRailCase(21275)
    @Test
    fun loginFailure() = runTest {
        var node = daVinci.start() // Return first Node
        assertTrue(node is ContinueNode)

        // Fill in the login form with invalid credentials and submit...
        (node.collectors[0] as? TextCollector)?.value = username
        (node.collectors[1] as? PasswordCollector)?.value = "invalid"
        (node.collectors[2] as? SubmitCollector)?.value = "Sign On"

        node = node.next()
        assertTrue(node is ErrorNode)
        assertNotNull(node.input)
        assertEquals("Invalid username and/or password", node.message.trim())

        assertNull(daVinci.user())
    }

    @TestRailCase(21276)
    @Test
    fun checkActiveSession() = runTest {
        var node = daVinci.start() // Return first Node
        assertTrue(node is ContinueNode)

        // Fill in the login form with valid credentials and submit...
        (node.collectors[0] as? TextCollector)?.value = username
        (node.collectors[1] as? PasswordCollector)?.value = password
        (node.collectors[2] as? SubmitCollector)?.value = "Sign On"

        // Click on the "Continue" button to finish the login process
        node = node.next()
        node = node as ContinueNode
        (node.collectors[0] as? SubmitCollector)?.value = "Continue"

        node = node.next()
        assertTrue(node is SuccessNode)

        // Make sure the user is not null
        val user = node.user
        assertNotNull((user.token() as Result.Success).value.accessToken)

        // Launch the login form again (active session exists...)
        // Should go directly to success...
        val node1 = daVinci.start()
        assertTrue(node1 is SuccessNode)

        // Logout the user
        val u = daVinci.user()
        u?.logout() ?: throw Exception("User is null")

        // After logout make sure the user is null
        assertNull(daVinci.user())
    }

    @TestRailCase(21253)
    @Test
    fun userRegistrationSuccess() = runBlocking {
        var node = daVinci.start()
        assertTrue(node is ContinueNode)

        // Make sure that we are at the login form
        assertEquals("E2E Login Form", node.name)

        // Click on the registration link
        (node.collectors[3] as? FlowCollector)?.value = "register"
        node = node.next()

        assertTrue(node is ContinueNode)

        // Validate the registration form
        assertEquals(6, node.collectors.size )
        assertEquals("Registration Form", node.name)
        assertEquals("Collect Name, Email, Password", node.description)
        assertEquals("Email", (node.collectors[0] as TextCollector).label)
        assertEquals("Password", (node.collectors[1] as PasswordCollector).label)
        assertEquals("Given Name", (node.collectors[2] as TextCollector).label)
        assertEquals("Family Name", (node.collectors[3] as TextCollector).label)
        assertEquals("Continue", (node.collectors[4] as SubmitCollector).label)
        assertEquals("Already have an account? Sign On", (node.collectors[5] as FlowCollector).label)

        // Fill in the registration form
        val newUser = "e2e" + System.currentTimeMillis() + "@example.com"
        (node.collectors[0] as? TextCollector)?.value = newUser
        (node.collectors[1] as? PasswordCollector)?.value = password
        (node.collectors[2] as? TextCollector)?.value = userFname
        (node.collectors[3] as? TextCollector)?.value = userLname
        (node.collectors[4] as? SubmitCollector)?.value = "Save"

        node = node.next() as ContinueNode

        // User should be navigated to the verification code screen
        assertTrue(node.collectors.size == 3 )

        assertTrue(node.collectors[0] is TextCollector)
        assertTrue(node.collectors[1] is SubmitCollector)
        assertTrue(node.collectors[2] is FlowCollector)

        assertEquals("Enter verification code", node.name)
        assertEquals("Hint: The verification code is 1234", node.description)
        assertEquals("Verification Code", (node.collectors[0] as TextCollector).label)
        assertEquals("Verify", (node.collectors[1] as SubmitCollector).label)
        assertEquals("Resend Verification Code", (node.collectors[2] as FlowCollector).label)

        // Fill in the verification code and submit
        (node.collectors[0] as? TextCollector)?.value = verificationCode
        (node.collectors[1] as? SubmitCollector)?.value = "Verify"
        node = node.next()
        assertTrue(node is ContinueNode)

        // User should be navigated to the "Successful user creation" screen...
        assertTrue(node.collectors.size == 1 )

        assertTrue(node.collectors[0] is SubmitCollector)
        assertEquals("Registration Complete", node.name)
        assertEquals("Notify User Account Is Successfully Created", node.description)
        assertEquals("Continue", (node.collectors[0] as SubmitCollector).label)

        // Click "Continue" to finish the registration process
        (node.collectors[0] as? SubmitCollector)?.value = "Continue"
        node = node.next()
        assertTrue(node is SuccessNode)

        // Make sure the user is not null
        val user = node.user
        assertNotNull((user.token() as Result.Success).value.accessToken)

        val u = daVinci.user()
        u?.logout() ?: throw Exception("User is null")

        //After logout make sure the user is null
        assertNull(daVinci.user())

        // Delete the user from PingOne
        deleteUser(newUser, password)
    }

    @TestRailCase(21269)
    @Test
    fun userRegistrationFailureUserAlreadyExists() = runTest {
        var node = daVinci.start()
        node = (node as ContinueNode)

        // Make sure that we are at the login form
        assertEquals("E2E Login Form", node.name)

        // Click on the registration link
        (node.collectors[3] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Make sure that we are at the registration form
        assertEquals("Registration Form", node.name)

        // Fill in the registration form with user that already exists
        (node.collectors[0] as? TextCollector)?.value = username
        (node.collectors[1] as? PasswordCollector)?.value = password
        (node.collectors[2] as? TextCollector)?.value = userFname
        (node.collectors[3] as? TextCollector)?.value = userLname
        (node.collectors[4] as? SubmitCollector)?.value = "Save"

        val errorNode = node.next()
        assertTrue(errorNode is ErrorNode)

        assertEquals("\"uniquenessViolation\"", errorNode.input["code"].toString())
        assertEquals("400", errorNode.input["httpResponseCode"].toString())
        assertEquals("An account with that email address already exists.", errorNode.message.trim())

        // Make sure that we are still at the registration form
        assertEquals("Registration Form", node.name)
        assertNull(daVinci.user())
    }

    @TestRailCase(21270)
    @Test
    fun userRegistrationFailureInvalidEmail() = runTest {
        var node = daVinci.start()
        node = (node as ContinueNode)

        // Make sure that we are at the login form
        assertEquals("E2E Login Form", node.name)

        // Click on the registration link
        (node.collectors[3] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Make sure that we are at the registration form
        assertEquals("Registration Form", node.name)

        // Fill in the registration form with empty username (email)
        (node.collectors[0] as? TextCollector)?.value = ""
        (node.collectors[1] as? PasswordCollector)?.value = password
        (node.collectors[2] as? TextCollector)?.value = userFname
        (node.collectors[3] as? TextCollector)?.value = userLname
        (node.collectors[4] as? SubmitCollector)?.value = "Save"

        var errorNode = node.next()
        assertTrue(errorNode is ErrorNode)

        assertEquals("400", errorNode.input["httpResponseCode"].toString())
        assertEquals("Enter a valid email address", errorNode.message.trim())

        // Make sure that we are still at the registration form
        assertEquals("Registration Form", node.name)

        // Fill in the registration form with an invalid username (email)
        (node.collectors[0] as? TextCollector)?.value = "invalid-email"
        (node.collectors[1] as? PasswordCollector)?.value = password
        (node.collectors[2] as? TextCollector)?.value = userFname
        (node.collectors[3] as? TextCollector)?.value = userLname
        (node.collectors[4] as? SubmitCollector)?.value = "Save"

        errorNode = node.next()
        assertTrue(errorNode is ErrorNode)

        assertEquals("400", errorNode.input["httpResponseCode"].toString())
        assertEquals("email: must be a well-formed email address", errorNode.message.trim())

        assertNull(daVinci.user())
    }

    @TestRailCase(21272)
    @Test
    fun userRegistrationFailureInvalidPassword() = runTest {
        var node = daVinci.start()
        node = (node as ContinueNode)

        // Make sure that we are at the login form
        assertEquals("E2E Login Form", node.name)

        // Click on the registration link
        (node.collectors[3] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Make sure that we are at the registration form
        assertEquals("Registration Form", node.name)

        // Fill in the registration form with user that already exists
        (node.collectors[0] as? TextCollector)?.value = userFname + System.currentTimeMillis() + "@example.com"
        // Note: The password rules for the E2E Tests population require at least one number
        (node.collectors[1] as? PasswordCollector)?.value = "invalid"
        (node.collectors[2] as? TextCollector)?.value = userFname
        (node.collectors[3] as? TextCollector)?.value = userLname
        (node.collectors[4] as? SubmitCollector)?.value = "Save"

        val errorNode = node.next()
        assertTrue(errorNode is ErrorNode)

        assertEquals("\"invalidValue\"", errorNode.input["code"].toString())
        assertEquals("400", errorNode.input["httpResponseCode"].toString())
        assertEquals("password: User password did not satisfy password policy requirements", errorNode.message.trim())

        // Make sure that we are still at the registration form
        assertEquals("Registration Form", node.name)

        assertNull(daVinci.user())
    }

    @TestRailCase(21273)
    @Test
    fun userRegistrationFailureInvalidVerificationCode() = runBlocking {
        var node = daVinci.start()
        node = node as ContinueNode

        // Make sure that we are at the login form
        assertEquals("E2E Login Form", node.name)

        // Click on the registration link
        (node.collectors[3] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Make sure that we are at the registration form
        assertEquals("Registration Form", node.name)

        // Fill in the registration form
        val newUser = userFname + System.currentTimeMillis() + "@example.com"
        (node.collectors[0] as? TextCollector)?.value = newUser
        (node.collectors[1] as? PasswordCollector)?.value = password
        (node.collectors[2] as? TextCollector)?.value = userFname
        (node.collectors[3] as? TextCollector)?.value = userLname
        (node.collectors[4] as? SubmitCollector)?.value = "Save"

        node = node.next() as ContinueNode

        // Make sure that we are at the "Verification Code" screen
        assertEquals("Enter verification code", node.name)

        // Enter invalid verification code and submit
        (node.collectors[0] as? TextCollector)?.value = "invalid"
        (node.collectors[1] as? SubmitCollector)?.value = "Verify"
        val errorNode = node.next()
        assertTrue(errorNode is ErrorNode)

        assertEquals("400", errorNode.input["code"].toString())
        assertEquals("Invalid verification code", errorNode.message.trim())

        // Make sure that we are still at verification code page
        assertEquals("Enter verification code", node.name)

        // Resend the verification code
        (node.collectors[2] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Make sure that we are still at verification code page
        assertEquals("Enter verification code", node.name)

        assertNull(daVinci.user())
        deleteUser(newUser, password)
    }

    @TestRailCase(21277)
    @Test
    fun passwordRecovery() = runBlocking {
        // Register a test user...
        val newUser = userFname + System.currentTimeMillis() + "@example.com"
        registerUser(newUser, password)

        /// Login again...
        var node = daVinci.start()
        node = node as ContinueNode

        // Click on the "Having trouble..." link
        (node.collectors[4] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        // At the "User Identifier Form" screen...
        assertTrue(node.collectors.size == 3)

        assertTrue(node.collectors[0] is TextCollector)     // Username
        assertTrue(node.collectors[1] is SubmitCollector)   // Continue button
        assertTrue(node.collectors[2] is FlowCollector)     // Back link

        assertEquals("User Identifier Form", node.name)
        assertEquals("Prompt For Email To Send Instructions To Reset Password", node.description)
        assertEquals("Username", (node.collectors[0] as TextCollector).label)
        assertEquals("Continue", (node.collectors[1] as SubmitCollector).label)
        assertEquals("Back", (node.collectors[2] as FlowCollector).label)

        // Fill in the username and submit
        (node.collectors[0] as? TextCollector)?.value = newUser
        (node.collectors[1] as? SubmitCollector)?.value = "Submit"
        node = node.next()
        assertTrue(node is ContinueNode)

        // At the "Password Recovery Form" screen...
        assertTrue(node.collectors.size == 6)

        assertEquals("Password Recovery Form", node.name)
        assertEquals("Enter The Recovery Code and Set New Password (Hint: Recovery code is 1234)", node.description)
        assertTrue(node.collectors[0] is TextCollector)     // Recovery Code
        assertTrue(node.collectors[1] is PasswordCollector) // New Password
        assertTrue(node.collectors[2] is PasswordCollector) // Verify New Password
        assertTrue(node.collectors[3] is SubmitCollector)   // Continue button
        assertTrue(node.collectors[4] is FlowCollector)     // Resend recovery code
        assertTrue(node.collectors[5] is FlowCollector)     // Cancel link

        assertEquals("Recovery Code", (node.collectors[0] as TextCollector).label)
        assertEquals("New Password", (node.collectors[1] as PasswordCollector).label)
        assertEquals("Verify New Password", (node.collectors[2] as PasswordCollector).label)
        assertEquals("Continue", (node.collectors[3] as SubmitCollector).label)
        assertEquals("Resend recovery code", (node.collectors[4] as FlowCollector).label)
        assertEquals("Cancel", (node.collectors[5] as FlowCollector).label)

        // Fill in the recovery code and password and submit
        (node.collectors[0] as? TextCollector)?.value = verificationCode
        (node.collectors[1] as? PasswordCollector)?.value = "New$password"
        (node.collectors[2] as? PasswordCollector)?.value = "New$password"
        (node.collectors[3] as? SubmitCollector)?.value = "Submit"
        node = node.next()
        assertTrue(node is ContinueNode)

        // At the "Successful password reset" screen...
        assertTrue(node.collectors.size == 1)
        assertEquals("Password Reset Success", node.name)
        assertEquals("Success Message With Animated Checkmark", node.description)
        assertEquals("Continue", (node.collectors[0] as SubmitCollector).label)

        // Click "Continue" to finish the password reset process
        (node.collectors[0] as? SubmitCollector)?.value = "Continue"
        node = node.next()
        assertTrue(node is SuccessNode)

        // Make sure the user is not null
        val user = node.user
        assertNotNull((user.token() as Result.Success).value.accessToken)

        val u = daVinci.user()
        u?.logout() ?: throw Exception("User is null")

        // After logout make sure the user is null
        assertNull(daVinci.user())

        // Delete the user from PingOne
        deleteUser(newUser, "New$password")
    }

    @TestRailCase(21278)
    @Test
    fun passwordReset() = runBlocking {
        // Register a test user...
        val newUser = userFname + System.currentTimeMillis() + "@example.com"
        registerUser(newUser, password)

        // Login...
        var node = daVinci.start()
        node = node as ContinueNode

        // Make suer that we are at the Login form...
        assertEquals("E2E Login Form", node.name)

        // Fill in the login form with valid credentials and submit...
        (node.collectors[0] as? TextCollector)?.value = newUser
        (node.collectors[1] as? PasswordCollector)?.value = password
        (node.collectors[2] as? SubmitCollector)?.value = "Sign On"

        node = node.next() as ContinueNode

        // Make sure that we are at the "Successful login" screen...
        assertEquals("Successful login", node.name)

        // Click reset password link
        (node.collectors[1] as? FlowCollector)?.value = "Reset Password"
        node = node.next() as ContinueNode

        // At the "Change Password Form" screen...
        assertTrue(node.collectors.size == 5)

        assertEquals("Change Password Form", node.name)
        assertEquals("Prompt for existing and new password", node.description)
        assertTrue(node.collectors[0] is PasswordCollector)     // Current Password
        assertTrue(node.collectors[1] is PasswordCollector)     // New Password
        assertTrue(node.collectors[2] is PasswordCollector)     // Verify New Password
        assertTrue(node.collectors[3] is SubmitCollector)       // Continue button
        assertTrue(node.collectors[4] is FlowCollector)         // Cancel link

        assertEquals("Current Password", (node.collectors[0] as PasswordCollector).label)
        assertEquals("New Password", (node.collectors[1] as PasswordCollector).label)
        assertEquals("Verify New Password", (node.collectors[2] as PasswordCollector).label)
        assertEquals("Continue", (node.collectors[3] as SubmitCollector).label)
        assertEquals("Cancel", (node.collectors[4] as FlowCollector).label)

        // Fill in the reset password form and submit
        (node.collectors[0] as? PasswordCollector)?.value = password
        (node.collectors[1] as? PasswordCollector)?.value = "New$password"
        (node.collectors[2] as? PasswordCollector)?.value = "New$password"
        (node.collectors[3] as? SubmitCollector)?.value = "Continue"
        node = node.next() as ContinueNode

        // At the "Password Reset Success" screen...
        assertTrue(node.collectors.size == 1)
        assertEquals("Password Reset Success", node.name)
        assertEquals("Success Message With Animated Checkmark", node.description)
        assertEquals("Continue", (node.collectors[0] as SubmitCollector).label)

        // Click "Continue" to finish the password reset process
        (node.collectors[0] as? SubmitCollector)?.value = "Continue"
        node = node.next()
        assertTrue(node is SuccessNode)

        // Make sure the user is not null
        val user = node.user
        assertNotNull((user.token() as Result.Success).value.accessToken)

        val u = daVinci.user()
        u?.logout() ?: throw Exception("User is null")

        // After logout make sure the user is null
        assertNull(daVinci.user())

        // Delete the user from PingOne
        deleteUser(newUser, "New$password")
    }

    @TestRailCase(24629)
    @Test(timeout = 20000)
    fun accountLocked() = runBlocking {
        // Register a test user...
        val newUser = userFname + System.currentTimeMillis() + "@example.com"
        registerUser(newUser, password)

        // Login again...
        var node = daVinci.start()
        node = node as ContinueNode

        // Make sure that we are at the Login form...
        assertEquals("E2E Login Form", node.name)

        // Fill in the login form with invalid credentials and submit...
        // The following rules apply for the E2E test population:
        // Account Lockout Rules:
        // The user's account will be locked out after 2 distinct failed password attempts;
        // repeated attempts of the same password are not counted.
        // Automatically unlock accounts that were locked by failed password attempts after 1 seconds
        (node.collectors[0] as? TextCollector)?.value = newUser
        (node.collectors[1] as? PasswordCollector)?.value = "wrong1"
        (node.collectors[2] as? SubmitCollector)?.value = "Sign On"
        val errorNode = node.next()

        assertTrue(errorNode is ErrorNode)
        assertEquals("Invalid username and/or password", errorNode.message.trim())

        // Make sure that we are still at the Login form...
        assertEquals("E2E Login Form", node.name)

        (node.collectors[0] as? TextCollector)?.value = newUser
        (node.collectors[1] as? PasswordCollector)?.value = "wrong2"
        (node.collectors[2] as? SubmitCollector)?.value = "Sign On"
        node = node.next() as ContinueNode

        // Make sure that we are at the "Account Locked" screen...
        assertEquals("Account Locked Message", node.name)
        assertEquals("Notify when account will unlock", node.description)

        assertTrue(node.collectors.size == 1)

        assertTrue(node.collectors[0] is FlowCollector) // Back to sign on
        assertEquals("Back to sign on", (node.collectors[0] as FlowCollector).label)
        // Click on the back link
        (node.collectors[0] as? FlowCollector)?.value = "Back"

        node = node.next()
        assertTrue(node is ContinueNode)

        // Make sure that we are back at the Login form
        assertEquals("E2E Login Form", node.name)
        assertNull(daVinci.user())

        // Wait for a second, so that the account unlocks automatically and enter valid username and password
        Thread.sleep(1500)
        (node.collectors[0] as? TextCollector)?.value = newUser
        (node.collectors[1] as? PasswordCollector)?.value = password
        (node.collectors[2] as? SubmitCollector)?.value = "Sign On"
        node = node.next() as ContinueNode

        assertEquals("Successful login", node.name)
        assertEquals("Successfully logged in to DaVinci", node.description)
        // Click continue
        (node.collectors[0] as? SubmitCollector)?.value = "Continue"

        node = node.next()
        assertTrue(node is SuccessNode)

        // Make sure the user is not null
        val user = node.user
        assertNotNull((user.token() as Result.Success).value.accessToken)

        val u = daVinci.user()
        u?.logout() ?: throw Exception("User is null")

        //After logout make sure the user is null
        assertNull(daVinci.user())

        // Delete the user from PingOne
        deleteUser(newUser, password)
    }

    // Helper function to register a user
    private suspend fun registerUser(username: String, password: String){
        var node = daVinci.start()
        node = node as ContinueNode

        // Make sure that we are at the login form
        assertEquals("E2E Login Form", node.name)

        // Click on the registration link
        (node.collectors[3] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Make sure that we are at the registration form
        assertEquals("Registration Form", node.name)

        // Fill in the registration form
        (node.collectors[0] as? TextCollector)?.value = username
        (node.collectors[1] as? PasswordCollector)?.value = password
        (node.collectors[2] as? TextCollector)?.value = userFname
        (node.collectors[3] as? TextCollector)?.value = userLname
        (node.collectors[4] as? SubmitCollector)?.value = "Save"

        node = node.next() as ContinueNode

        // Make sure that we are at the "Verification Code" screen
        assertEquals("Enter verification code", node.name)

        // Enter verification code and submit
        (node.collectors[0] as? TextCollector)?.value = verificationCode
        (node.collectors[1] as? SubmitCollector)?.value = "Verify"
        node = node.next()
        assertTrue(node is ContinueNode)

        assertTrue(node.collectors[0] is SubmitCollector)
        assertEquals("Registration Complete", node.name)
        assertEquals("Notify User Account Is Successfully Created", node.description)
        assertEquals("Continue", (node.collectors[0] as SubmitCollector).label)

        // Click "Continue" to finish the registration process
        (node.collectors[0] as? SubmitCollector)?.value = "Continue"
        node = node.next()
        assertTrue(node is SuccessNode)

        // Make sure the user is not null
        var user = node.user
        assertNotNull((user.token() as Result.Success).value.accessToken)

        // Logout the user
        var u = daVinci.user()
        u?.logout() ?: throw Exception("User is null")
    }

    // Helper function to delete a user
    private suspend fun deleteUser(username: String, password: String) {
        var node = daVinci.start()
        node = (node as ContinueNode)

        // Make sure that we are at the Login form...
        assertEquals("E2E Login Form", node.name)

        // Fill in the login form with valid credentials and submit...
        (node.collectors[0] as? TextCollector)?.value = username
        (node.collectors[1] as? PasswordCollector)?.value = password
        (node.collectors[2] as? SubmitCollector)?.value = "Sign On"

        node = node.next() as ContinueNode

        // Make sure that we are at the "Successful login" screen...
        assertEquals("Successful login", node.name)

        // Click delete user button
        (node.collectors[2] as? FlowCollector)?.value = "Delete User"
        node = node.next() as ContinueNode

        // Validate success user deletion screen
        assertEquals("Success", node.name)
        assertEquals("User has been successfully deleted", node.description)
    }
}