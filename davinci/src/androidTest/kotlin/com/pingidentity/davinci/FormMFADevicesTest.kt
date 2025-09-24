/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import androidx.test.filters.SmallTest
import com.pingidentity.davinci.collector.DeviceAuthenticationCollector
import com.pingidentity.davinci.collector.DeviceRegistrationCollector
import com.pingidentity.utils.Result
import com.pingidentity.davinci.collector.FlowCollector
import com.pingidentity.davinci.collector.LabelCollector
import com.pingidentity.davinci.collector.PasswordCollector
import com.pingidentity.davinci.collector.PhoneNumberCollector
import com.pingidentity.davinci.collector.SingleSelectCollector
import com.pingidentity.davinci.collector.SubmitCollector
import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.module.description
import com.pingidentity.davinci.module.name
import com.pingidentity.davinci.plugin.collectors
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.oidc.module.user
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@SmallTest
class FormMFADevicesTest {
    private var daVinci = DaVinci {
        logger = Logger.STANDARD

        module(Oidc) {
            clientId = "021b83ce-a9b1-4ad4-8c1d-79e576eeab76"
            discoveryEndpoint = "https://auth.pingone.ca/02fb4743-189a-4bc7-9d6c-a919edfe6447/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = "org.forgerock.demo://oauth2redirect"
            acrValues = "1557008a3c8b6105d5f4e8e053ac7a29"
        }
    }

    private val MFA_TEXT: Int = 1
    private val MFA_VOICE: Int = 2

    private lateinit var usernamePrefix: String
    private lateinit var userFname: String
    private lateinit var userLname: String
    private lateinit var username: String
    private lateinit var password: String
    private lateinit var email1: String
    private lateinit var email2: String
    private lateinit var phoneNumber1: String
    private lateinit var phoneNumber2: String

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @BeforeTest
    fun setUp() = runTest {
        usernamePrefix = "MFA"
        password = "Demo1234#1"
        username = usernamePrefix + System.currentTimeMillis() + "@example.com"
        userFname = "GAGA"
        userLname = "User"
        email1 = usernamePrefix + System.currentTimeMillis() + "@example.com"
        email2 = usernamePrefix + System.currentTimeMillis() + "@example.net"
        phoneNumber1 = "888123456"
        phoneNumber2 = "888123457"

        // Make sure to start with a clean session
        daVinci.user()?.logout()

        // Register a test user
        registerUser(username, password)
    }

    @AfterTest
    fun tearDown() = runTest {
        // Delete the test user
        deleteUser(username, password)
    }

    @TestRailCase(29489)
    @Test
    fun verifyDeviceRegistrationForm() = runTest {
        // Login with the test user
        var node = loginUser(username, password)

        // Make sure that we are at the initial test form
        assertEquals("Select Test Form", node.name)

        // Select the "Device Registration" test form
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // There is only one collector in this node ("device-registration" collector)
        assertTrue(node.collectors.size == 1)
        assertTrue(node.collectors[0] is DeviceRegistrationCollector)

        val deviceRegistrationCollector = node.collectors[0] as DeviceRegistrationCollector

        assertEquals("DEVICE_REGISTRATION", deviceRegistrationCollector.type)
        assertEquals("device-registration", deviceRegistrationCollector.key)
        assertEquals("MFA Device Selection - Registration", deviceRegistrationCollector.label)
        assertTrue(deviceRegistrationCollector.required)

        // Assert the available options
        assertTrue(deviceRegistrationCollector.devices.size == 3)
        assertEquals("EMAIL", deviceRegistrationCollector.devices[0].type)
        assertEquals("Email", deviceRegistrationCollector.devices[0].title)
        assertEquals("Receive an authentication passcode in your email.", deviceRegistrationCollector.devices[0].description)
        assertNotNull(deviceRegistrationCollector.devices[0].iconSrc)

        assertEquals("SMS", deviceRegistrationCollector.devices[1].type)
        assertEquals("Text Message", deviceRegistrationCollector.devices[1].title)
        assertEquals("Receive an authentication passcode in a text message.", deviceRegistrationCollector.devices[1].description)
        assertNotNull(deviceRegistrationCollector.devices[1].iconSrc)

        assertEquals("VOICE", deviceRegistrationCollector.devices[2].type)
        assertEquals("Voice", deviceRegistrationCollector.devices[2].title)
        assertEquals("Receive a phone call with an authentication passcode.", deviceRegistrationCollector.devices[2].description)
        assertNotNull(deviceRegistrationCollector.devices[2].iconSrc)
    }

    @TestRailCase(29492)
    @Test
    fun verifyDeviceAuthenticationFormError() = runTest {
        // Login with the test user (no MFA devices registered yet)
        var node = loginUser(username, password)

        // Make sure that we are at the initial test form
        assertEquals("Select Test Form", node.name)

        // Select the "Device Authentication" test form
        (node.collectors[1] as? FlowCollector)?.value = "click"

        val errorNode = node.next()
        assertTrue(errorNode is ErrorNode)

        assertEquals("400", errorNode.input["httpResponseCode"].toString())
        assertEquals("There was a problem getting the MFA devices for the specified user. Check your PingOne Forms connector configuration.", errorNode.message.trim())
    }

    @TestRailCase(29493)
    @Test
    @Ignore("Flaky test - some times it takes a lot of time for registered devices to appear in PingOne")
    fun verifyDeviceAuthenticationForm() = runTest(timeout = 60.seconds) {
        // Register an email MFA device
        registerEmailMFA(email1)
        var node = loginUser(username, password)

        // Make sure that we are at the initial test form
        assertEquals("Select Test Form", node.name)

        // Select the "Device Authentication" test form
        (node.collectors[1] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        assertEquals("SDK Automation - Device Authentication", node.name)
        assertEquals("Test form for DEVICE_AUTHENTICATION collector", node.description)

        // There is only one collector in this node ("device-authentication" collector)
        assertTrue(node.collectors.size == 1)
        assertTrue(node.collectors[0] is DeviceAuthenticationCollector)

        var deviceAuthenticationCollector = node.collectors[0] as DeviceAuthenticationCollector

        assertEquals("DEVICE_AUTHENTICATION", deviceAuthenticationCollector.type)
        assertEquals("device-authentication", deviceAuthenticationCollector.key)
        assertEquals("MFA Device Selection - Authentication", deviceAuthenticationCollector.label)
        assertTrue(deviceAuthenticationCollector.required)

        // Assert the available devices (should be only one EMAIL device)
        assertTrue(deviceAuthenticationCollector.devices.size == 1)
        assertEquals("EMAIL", deviceAuthenticationCollector.devices[0].type)
        assertEquals("Email", deviceAuthenticationCollector.devices[0].title)

        val emailRegex = Regex("[a-zA-Z0-9._%+-]+@example\\.com")
        assertTrue( deviceAuthenticationCollector.devices[0].description.contains(emailRegex))
        assertNotNull(deviceAuthenticationCollector.devices[0].iconSrc)

        // Register another email MFA device and TEXT and VOICE MFA devices
        registerEmailMFA(email2)
        registerPhoneMFA(phoneNumber1, MFA_TEXT)
        registerPhoneMFA(phoneNumber2, MFA_VOICE)

        // Login with the test user (should have 4 MFA devices registered)
        // Note that registered devices may take a few seconds to appear in the form, so retry a few times...
        repeat(3) {
            node = loginUser(username, password)
            (node.collectors[1] as? FlowCollector)?.value = "click"
            node = node.next() as ContinueNode
            val collector = node.collectors[0] as DeviceAuthenticationCollector
            if (collector.devices.size == 4) return@repeat
            Thread.sleep(500)
        }

        // Repeat the steps from above and make sure that all registered MFA devices appear in the form
        node = loginUser(username, password)

        // Select the "Device Authentication" test form
        (node.collectors[1] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        assertEquals("SDK Automation - Device Authentication", node.name)
        deviceAuthenticationCollector = node.collectors[0] as DeviceAuthenticationCollector

        // Assert the available devices
        System.out.println ("deviceAuthenticationCollector.devices.size = ${deviceAuthenticationCollector.devices.size}")
        assertTrue(deviceAuthenticationCollector.devices.size == 4)
        assertEquals("EMAIL", deviceAuthenticationCollector.devices[0].type)
        assertEquals("EMAIL", deviceAuthenticationCollector.devices[1].type)
        assertEquals("SMS", deviceAuthenticationCollector.devices[2].type)
        assertEquals("VOICE", deviceAuthenticationCollector.devices[3].type)
    }

    @TestRailCase(29484)
    @Test
    fun deviceRegistrationEmail() = runTest {
        registerEmailMFA(email1)
    }

    @TestRailCase(29490)
    @Test
    fun deviceRegistrationSMS() = runTest {
        registerPhoneMFA(phoneNumber1, MFA_TEXT)
    }

    @TestRailCase(29491)
    @Test
    fun deviceRegistrationVOICE() = runTest {
        registerPhoneMFA(phoneNumber2, MFA_VOICE)
    }

    // Helper function to register a user
    private suspend fun registerUser(username: String, password: String){
        var node = daVinci.start()
        node = node as ContinueNode

        // Make sure that we are at the initial test form
        assertEquals("Select Test Form", node.name)

        // Click on the registration link
        (node.collectors[2] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Make sure that we are at the user registration form
        assertEquals("Registration Form", node.name)

        // Fill in the registration form
        (node.collectors[0] as? TextCollector)?.value = username
        (node.collectors[1] as? PasswordCollector)?.value = password
        (node.collectors[2] as? TextCollector)?.value = userFname
        (node.collectors[3] as? TextCollector)?.value = userLname
        (node.collectors[4] as? SubmitCollector)?.value = "Save"

        node = node.next() as ContinueNode

        assertTrue(node.collectors[0] is SubmitCollector)
        assertEquals("Registration Complete", node.name)
        assertEquals("User Account Successfully Created", node.description)
        assertEquals("Continue", (node.collectors[0] as SubmitCollector).label)

        // Click "Continue" to finish the registration process
        (node.collectors[0] as? SubmitCollector)?.value = "Continue"
        node = node.next()
        assertTrue(node is SuccessNode)

        // Make sure the user is not null
        val user = node.user
        assertNotNull((user.token() as Result.Success).value.accessToken)

        // Logout the user
        val u = daVinci.user()
        u?.logout() ?: throw Exception("User is null")
    }

    // Helper function to login a user
    private suspend fun loginUser(username: String, password: String) : ContinueNode {
        var node = daVinci.start()
        node = node as ContinueNode

        // Make sure that we are at the initial test form
        assertEquals("Select Test Form", node.name)

        // Click on the "User Login" button
        (node.collectors[3] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Make sure that we are at the user registration form
        assertEquals("SDK Automation - Sign On", node.name)

        // Fill in the login form with valid credentials and submit...
        (node.collectors[1] as? TextCollector)?.value = username
        (node.collectors[2] as? PasswordCollector)?.value = password
        (node.collectors[3] as? SubmitCollector)?.value = "Sign On"

        node = node.next()
        assertTrue(node is ContinueNode)

        // Upon successful login we should be at the initial screen... ("Select Test Form")
        assertEquals("Select Test Form", node.name)
        return node
    }

    // Helper function to delete a user
    private suspend fun deleteUser(username: String, password: String) {
        // Login the user
        var node = loginUser(username, password)

        // Click on the "User Delete" button
        (node.collectors[4] as? FlowCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Make sure that the user is successfully deleted
        assertEquals("Success", node.name)
        assertEquals("User has been successfully deleted", node.description)
    }

    private suspend fun registerEmailMFA(email: String) {
        // Login with the test user
        var node = loginUser(username, password)

        // Make sure that we are at the initial test form
        assertEquals("Select Test Form", node.name)

        // Select the "Device Registration" test form
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // There is only one collector in this node ("device-registration" collector)
        assertTrue(node.collectors.size == 1)
        assertTrue(node.collectors[0] is DeviceRegistrationCollector)

        // Select the "Email" option
        val deviceRegistrationCollector = node.collectors[0] as DeviceRegistrationCollector
        deviceRegistrationCollector.value = deviceRegistrationCollector.devices[0]
        node = node.next() as ContinueNode

        // Make sure that we are at the EMAIL device registration form
        assertEquals("SDK Automation - Enter Email", node.name)
        assertEquals("Enter email for registration", node.description)

        // Assert the collectors
        assertTrue(node.collectors.size == 3)
        assertTrue(node.collectors[0] is LabelCollector)
        assertTrue(node.collectors[1] is TextCollector)
        assertTrue(node.collectors[2] is SubmitCollector)

        assertEquals("Enter Email", (node.collectors[0] as LabelCollector).content)
        assertEquals("Email Address", (node.collectors[1] as TextCollector).label)

        // Enter an email address in the form
        (node.collectors[1] as? TextCollector)?.value = email
        (node.collectors[2] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Make sure that we are at the "successful registration" screen
        assertEquals("EMAIL MFA Registered", node.name)
        assertEquals("Email MFA Device Successfully Created", node.description)
    }

    private suspend fun registerPhoneMFA(phone: String, mfaType: Int) {
        // Login with the test user
        var node = loginUser(username, password)

        // Make sure that we are at the initial test form
        assertEquals("Select Test Form", node.name)

        // Select the "Device Registration" test form
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // There is only one collector in this node ("device-registration" collector)
        assertTrue(node.collectors.size == 1)
        assertTrue(node.collectors[0] is DeviceRegistrationCollector)

        // Select the "Text Message" option
        val deviceRegistrationCollector = node.collectors[0] as DeviceRegistrationCollector
        deviceRegistrationCollector.value = deviceRegistrationCollector.devices[mfaType]
        node = node.next() as ContinueNode

        // Make sure that we are at the Phone Number registration form
        assertEquals("SDK Automation - Enter Phone Number", node.name)
        assertEquals("Enter phone number", node.description)

        // Assert the collectors
        assertTrue(node.collectors.size == 4)
        assertTrue(node.collectors[0] is LabelCollector)
        assertTrue(node.collectors[1] is SingleSelectCollector)
        assertTrue(node.collectors[2] is PhoneNumberCollector)
        assertTrue(node.collectors[3] is SubmitCollector)

        // We have a label collector with the text "Phone Number Collector"
        assertEquals("Phone Number Collector", (node.collectors[0] as LabelCollector).content)

        // Followed by a Dropdown collector with country codes
        val dropdown = node.collectors[1] as SingleSelectCollector
        assertEquals("DROPDOWN", dropdown.type)
        assertEquals("countryCode", dropdown.key)
        assertEquals("Country Code", dropdown.label)
        assertEquals(true, dropdown.required)
        assertEquals(4, dropdown.options.size)
        assertEquals("United States", dropdown.options[0].label)
        assertEquals("India", dropdown.options[1].label)
        assertEquals("Brazil", dropdown.options[2].label)
        assertEquals("Canada", dropdown.options[3].label)
        assertEquals("US", dropdown.options[0].value)
        assertEquals("IN", dropdown.options[1].value)
        assertEquals("BR", dropdown.options[2].value)
        assertEquals("CA", dropdown.options[3].value)

        // Then a PhoneNumberCollector
        val phoneNumberCollector = node.collectors[2] as PhoneNumberCollector
        assertEquals("Enter Phone Number", phoneNumberCollector.label)
        assertEquals("PHONE_NUMBER", phoneNumberCollector.type)
        assertEquals("phone-input-field", phoneNumberCollector.key)
        assertTrue(phoneNumberCollector.required)
        assertFalse(phoneNumberCollector.validatePhoneNumber)
        assertEquals("IN", phoneNumberCollector.defaultCountryCode)

        // Select a country code and enter a valid phone number:...
        phoneNumberCollector.countryCode = "CA"  // Select Canada...
        phoneNumberCollector.phoneNumber = "7783177183" // Enter a valid phone number...

        // Submit the form
        (node.collectors[3] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Make sure that we are at the "successful registration" screen
        assertEquals("SMS/Voice MFA Registered", node.name)
        assertEquals("SMS/Voice MFA Device Successfully Created", node.description)

        // Click "Continue" to finish the registration process
        (node.collectors[0] as? SubmitCollector)?.value = "Continue"
        node = node.next() as ContinueNode

        // Upon successful phone registration we should be at the initial screen... ("Select Test Form")
        assertEquals("Select Test Form", node.name)
    }
}