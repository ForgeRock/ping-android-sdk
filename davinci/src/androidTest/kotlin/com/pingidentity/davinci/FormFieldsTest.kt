/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import androidx.test.filters.SmallTest
import com.pingidentity.davinci.collector.FlowCollector
import com.pingidentity.davinci.collector.LabelCollector
import com.pingidentity.davinci.collector.MultiSelectCollector
import com.pingidentity.davinci.collector.PhoneNumberCollector
import com.pingidentity.davinci.collector.BooleanCollector
import com.pingidentity.davinci.collector.SingleSelectCollector
import com.pingidentity.davinci.collector.SubmitCollector
import com.pingidentity.davinci.collector.TextCollector
import com.pingidentity.davinci.module.Oidc
import com.pingidentity.davinci.module.name
import com.pingidentity.davinci.plugin.collectors
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val tokenPattern = Regex("""\{\{(\w+)\}\}""")

@SmallTest
class FormFieldsTest {

    companion object {
        const val LABEL_TEXTBLOB_INDEX = 0
        const val LABEL_TRANSLATABLE_INDEX = 1
        const val LABEL_RICH_TEXT_INDEX = 2
        const val TEXT_INPUT_INDEX = 3
        const val CHECKBOX_INDEX = 4
        const val DROPDOWN_INDEX = 5
        const val RADIO_INDEX = 6
        const val COMBOBOX_INDEX = 7
        const val PHONE_NUMBER_INDEX = 8
        const val SINGLE_CHECKBOX_INDEX = 9
        const val FLOW_BUTTON_INDEX = 10
        const val FLOW_LINK_INDEX = 11
    }

    private var daVinci = DaVinci {
        logger = Logger.STANDARD

        module(Oidc) {
            clientId = "021b83ce-a9b1-4ad4-8c1d-79e576eeab76"
            discoveryEndpoint = "https://auth.pingone.ca/02fb4743-189a-4bc7-9d6c-a919edfe6447/as/.well-known/openid-configuration"
            scopes = mutableSetOf("openid", "email", "address", "phone", "profile")
            redirectUri = "org.forgerock.demo://oauth2redirect"
            acrValues = "210f6b876da11c836ffc1c5fb38f3938"
            //storage = dataStore
        }
    }

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @BeforeTest
    fun setUp() = runTest {
    }

    @TestRailCase(26023)
    @Test
    fun labelCollectorTest() = runTest {
        // Go to the "Form Fields" form
        var node = daVinci.start() as ContinueNode
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Make sure that the first 3 collectors in the form are LabelCollectors
        // [LABEL_TEXTBLOB_INDEX]      TEXTBLOB       → HTML content, no key
        // [LABEL_TRANSLATABLE_INDEX]  SLATE_TEXTBLOB → plain translatable text, key = "translatable-rich-text-key"
        // [LABEL_RICH_TEXT_INDEX]     SLATE_TEXTBLOB → translatable link,       key = "rich-text"
        assertTrue(node.collectors[LABEL_TEXTBLOB_INDEX] is LabelCollector)
        assertTrue(node.collectors[LABEL_TRANSLATABLE_INDEX] is LabelCollector)
        assertTrue(node.collectors[LABEL_RICH_TEXT_INDEX] is LabelCollector)
        val labelCollector1 = node.collectors[LABEL_TEXTBLOB_INDEX] as LabelCollector
        val labelCollector2 = node.collectors[LABEL_TRANSLATABLE_INDEX] as LabelCollector
        val labelCollector3 = node.collectors[LABEL_RICH_TEXT_INDEX] as LabelCollector

        // TODO: Update the following assertion to be more specific when the bug in DaVinci is fixed - see: https://pingidentity.slack.com/archives/C06CCT3NSP5/p1736897937860359
        assertTrue(labelCollector1.content.contains("Rich Text fields produce LABELs"))
        assertEquals("Translatable Rich Text produce LABELs too!\n\n", labelCollector2.content)

        // SDKS-3957 Add support for key attribute in Label Collectors
        assertEquals("translatable-rich-text-key", labelCollector2.key)
        // Note that the Rich Text component has been deprecated, so the key is not set
        assertEquals("", labelCollector1.key)

        // labelCollector1: HTML content — richText falls back to the raw HTML content string
        val richContent1 = labelCollector1.richContent
        assertNotNull(richContent1)
        assertTrue(richContent1.richText.contains("Rich Text fields produce LABELs"))
        // No richContent on this label so replacements must be empty
        assertTrue(richContent1.replacements.isEmpty())

        // labelCollector2: plain translatable text — richText comes from richContent.content
        // (without the trailing newlines present in the top-level content field)
        val richContent2 = labelCollector2.richContent
        assertNotNull(richContent2)
        assertEquals("Translatable Rich Text produce LABELs too!", richContent2.richText)
        // No replacement tokens on this label
        assertTrue(richContent2.replacements.isEmpty())

        // labelCollector3: translatable link — richText contains {{token}} placeholders and replacements map contains corresponding entries
        val richContent3 = labelCollector3.richContent
        assertNotNull(richContent3)
        assertEquals("A translatable rich text to take the user to {{link1}}", richContent3.richText)
        assertTrue(richContent3.replacements.containsKey("link1"))
        val replacement = richContent3.replacements["link1"]
        assertNotNull(replacement)
        assertEquals("google.com", replacement.value)
        assertEquals("https://www.google.com", replacement.href)
    }

    @Test
    fun labelCollectorWithTranslatableLinkTest() = runTest {
        // Go to the "Form Fields" form
        var node = daVinci.start() as ContinueNode
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // Find a LabelCollector that has replacements (translatable link label)
        val linkLabel = node.collectors
            .filterIsInstance<LabelCollector>()
            .firstOrNull { it.richContent?.replacements?.isNotEmpty() ?: false }

        if (linkLabel != null) {
            // richText must contain at least one {{token}} placeholder
            assertTrue(
                tokenPattern.containsMatchIn(linkLabel.richContent?.richText ?: ""),
                "Expected richText to contain {{token}} placeholders, was: ${linkLabel.richContent?.richText}"
            )
            // Every token present in richText must have a corresponding replacement entry
            for (match in tokenPattern.findAll(linkLabel.richContent?.richText ?: "")) {
                val token = match.groupValues[1]
                val replacement = linkLabel.richContent?.replacements[token]
                assertNotNull(replacement, "Missing replacement for token '$token'")
                assertTrue(replacement.value.isNotEmpty(), "Replacement value for '$token' must not be empty")
                assertTrue(replacement.href.isNotEmpty(), "Replacement href for '$token' must not be empty")
            }
        }
        // If no such label is present in the current form, the test is a no-op (no assertion failure)
    }

    @TestRailCase(26032, 26031)
    @Test
    fun textCollectorTest() = runTest {
        // Go to the "Form Fields" form
        var node = daVinci.start() as ContinueNode
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // 4th collector in the form is a TextCollector
        assertTrue(node.collectors[TEXT_INPUT_INDEX] is TextCollector)
        val textCollector = node.collectors[TEXT_INPUT_INDEX] as TextCollector

        // Assert the properties of the TextCollector
        assertEquals("Text Input Label", textCollector.label)
        assertEquals("text-input-key", textCollector.key)
        assertEquals("default text", textCollector.value)
        assertEquals(true, textCollector.required)
        assertNull(textCollector.validation)

        // Clear the text field
        textCollector.value = ""

        // Validate should return list with 2 validation errors since the value is empty
        // and does not match the configured regex
        val validationResult = textCollector.validate()
        assertEquals(1, validationResult.size)
        assertEquals("Required", validationResult[0].toString())

        textCollector.value = "Sometext123"
        val validationResult2 = textCollector.validate() // Should return empty list this time

        // TODO: This assertion is failing due to a bug in DaVinci - see https://pingidentity.slack.com/archives/C06CCT3NSP5/p1736880115921099
        // Until this is fixed, we will comment out this assertion
        // assertTrue(validationResult2.isEmpty())
    }

    @TestRailCase(26024, 26031)
    @Test
    fun checkboxCollectorTest() = runTest {
        // Go to the "Form Fields" form
        var node = daVinci.start() as ContinueNode
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // 5th collector in the form is a Checkbox group
        assertTrue(node.collectors[CHECKBOX_INDEX] is MultiSelectCollector)
        val checkbox = node.collectors[CHECKBOX_INDEX] as MultiSelectCollector

        // Assert the properties of the checkBox
        assertEquals("CHECKBOX", checkbox.type)
        assertEquals("checkbox-field-key", checkbox.key)
        assertEquals("Checkbox List Label", checkbox.label)
        assertEquals(true, checkbox.required)
        assertEquals(2, checkbox.options.size)
        assertEquals("option1 label", checkbox.options[0].label)
        assertEquals("option2 label", checkbox.options[1].label)
        assertEquals("option1 value", checkbox.options[0].value)
        assertEquals("option2 value", checkbox.options[1].value)

        // Make sure that the correct checkbox values are set (default values)
        assertEquals(2, checkbox.value.size)

        // Remove the values from the checkbox
        checkbox.value.remove("option1 value")
        checkbox.value.remove("option2 value")

        // validate() should fail since the value is empty but required
        val validationResult = checkbox.validate()
        assertTrue(validationResult.isNotEmpty())
        assertEquals("Required", validationResult[0].toString())

        // Add one option to the value and validate again
        checkbox.value.add("value1")
        val validationResult2 = checkbox.validate() // Should return empty list
        assertTrue(validationResult2.isEmpty())
    }

    @TestRailCase(26025, 26031)
    @Test
    fun dropdownCollectorTest() = runTest {
        // Go to the "Form Fields" form
        var node = daVinci.start() as ContinueNode
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // 6th collector in the form is a Dropdown field
        assertTrue(node.collectors[DROPDOWN_INDEX] is SingleSelectCollector)
        val dropdown = node.collectors[DROPDOWN_INDEX] as SingleSelectCollector

        // Assert the properties of the Dropdown
        assertEquals("DROPDOWN", dropdown.type)
        assertEquals("dropdown-field-key", dropdown.key)
        assertEquals("Dropdown List Label", dropdown.label)
        assertEquals(true, dropdown.required)
        assertEquals(3, dropdown.options.size)
        assertEquals("dropdown-option1-label", dropdown.options[0].label)
        assertEquals("dropdown-option2-label", dropdown.options[1].label)
        assertEquals("dropdown-option3-label", dropdown.options[2].label)
        assertEquals("dropdown-option1-value", dropdown.options[0].value)
        assertEquals("dropdown-option2-value", dropdown.options[1].value)
        assertEquals("dropdown-option3-value", dropdown.options[2].value)

        // Make sure that dropdown default value is set
        assertEquals("dropdown-option2-value", dropdown.value)

        // Clear the value of the dropdown
        dropdown.value = ""

        // validate() should fail since the value is empty but required
        val validationResult = dropdown.validate()
        assertTrue(validationResult.isNotEmpty())
        assertEquals("Required", validationResult[0].toString())

        // Select an option and validate again
        dropdown.value = "dropdown-option1"
        val validationResult2 = dropdown.validate() // Should return empty list this time
        assertTrue(validationResult2.isEmpty())
    }

    @TestRailCase(26026, 26031)
    @Test
    fun radioCollectorTest() = runTest {
        // Go to the "Form Fields" form
        var node = daVinci.start() as ContinueNode
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // 7th collector in the form is a Radio Group field
        assertTrue(node.collectors[RADIO_INDEX] is SingleSelectCollector)
        val radio = node.collectors[RADIO_INDEX] as SingleSelectCollector

        // Assert the properties of the radio group
        assertEquals("RADIO", radio.type)
        assertEquals("radio-group-key", radio.key)
        assertEquals("Radio Group Label", radio.label)
        assertEquals(true, radio.required)
        assertEquals(3, radio.options.size)
        assertEquals("option1 label", radio.options[0].label)
        assertEquals("option2 label", radio.options[1].label)
        assertEquals("option3 label", radio.options[2].label)
        assertEquals("option1 value", radio.options[0].value)
        assertEquals("option2 value", radio.options[1].value)
        assertEquals("option3 value", radio.options[2].value)

        // Make sure that radio default value is set
        assertEquals("option2 value", radio.value)

        // Clear the value of the radio
        radio.value = ""

        // validate() should fail since the value is empty but required
        val validationResult = radio.validate()
        assertTrue(validationResult.isNotEmpty())
        assertEquals("Required", validationResult[0].toString())

        // Select an option and validate again
        radio.value = "option1"
        val validationResult2 = radio.validate() // Should return empty list this time
        assertTrue(validationResult2.isEmpty())
    }

    @TestRailCase(26027, 26031)
    @Test
    fun comboboxCollectorTest() = runTest {
        // Go to the "Form Fields" form
        var node = daVinci.start() as ContinueNode
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // 8th collector in the form is a combo-box field
        assertTrue(node.collectors[COMBOBOX_INDEX] is MultiSelectCollector)
        val combobox = node.collectors[COMBOBOX_INDEX] as MultiSelectCollector

        // Assert the properties of the comboBox
        assertEquals("COMBOBOX", combobox.type)
        assertEquals("combobox-field-key", combobox.key)
        assertEquals("Combobox Label", combobox.label)
        assertEquals(true, combobox.required)
        assertEquals(3, combobox.options.size)
        assertEquals("option1 label", combobox.options[0].label)
        assertEquals("option2 label", combobox.options[1].label)
        assertEquals("option3 label", combobox.options[2].label)
        assertEquals("option1 value", combobox.options[0].value)
        assertEquals("option2 value", combobox.options[1].value)
        assertEquals("option3 value", combobox.options[2].value)

        // Make sure that default values are set
        assertEquals(2, combobox.value.size)
        assertEquals("option1 value", combobox.value[0])
        assertEquals("option3 value", combobox.value[1])

        // Clear the values of the combobox
        combobox.value.clear()

        // validate() should fail since the value is empty but required
        val validationResult = combobox.validate()
        assertTrue(validationResult.isNotEmpty())
        assertEquals("Required", validationResult[0].toString())

        // Select an option and validate again
        combobox.value.add("option1 value")
        combobox.value.add("option3 value")

        val validationResult2 = combobox.validate() // Should return empty list this time
        assertTrue(validationResult2.isEmpty())
    }

    @TestRailCase(26027, 31735)
    @Test
    fun phoneNumberCollectorTest() = runTest {
        // Go to the "Form Fields" form
        var node = daVinci.start() as ContinueNode
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        assertTrue(node.collectors[PHONE_NUMBER_INDEX] is PhoneNumberCollector)
        val phone = node.collectors[PHONE_NUMBER_INDEX] as PhoneNumberCollector

        // Assert the properties of the comboBox
        assertEquals("PHONE_NUMBER", phone.type)
        assertEquals("phone-field", phone.key)
        assertEquals("Phone Collector", phone.label)
        assertEquals(true, phone.required)
        assertEquals(true, phone.validatePhoneNumber)
        assertEquals("BF", phone.defaultCountryCode) // Burkina Faso (set in the form)
        assertEquals("GB", phone.countryCode) // Great Britain (set in DV form connector)
        assertEquals("(555)555-1234", phone.phoneNumber) // Set in DV form connector

        // Clear the value of the phone number
        phone.phoneNumber = ""

        // validate() should fail since the phone number is empty but required
        var validationResult = phone.validate()
        assertTrue(validationResult.isNotEmpty())
        assertEquals("Required", validationResult[0].toString())

        // Provide a phone number without country code and validate again
        phone.countryCode = ""
        phone.phoneNumber = "7783177184"

        validationResult = phone.validate() // Should fail
        assertTrue(validationResult.isNotEmpty())
        assertEquals("Required", validationResult[0].toString())

        // Providing a phone number and country code should pass the validation
        phone.countryCode = "CA"
        phone.phoneNumber = "7783177184"
        validationResult = phone.validate() // Should pass
        assertTrue(validationResult.isEmpty())
    }

    @TestRailCase(/* Add test rail IDs */)
    @Test
    fun singleCheckboxCollectorTest() = runTest {
        // Go to the "Form Fields" form
        var node = daVinci.start() as ContinueNode
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // 10th collector in the form is a SingleCheckbox (index 9)
        assertTrue(node.collectors[SINGLE_CHECKBOX_INDEX] is BooleanCollector)
        val singleCheckbox = (node.collectors[SINGLE_CHECKBOX_INDEX] as BooleanCollector)

        // Assert the properties
        assertEquals("SINGLE_CHECKBOX", singleCheckbox.type)
        assertEquals("single-checkbox-field", singleCheckbox.key)
        assertEquals("I agree to the Terms and Conditions", singleCheckbox.label)
        val richContent = singleCheckbox.richContent
        assertNotNull(richContent)
        assertEquals("I agree to the {{link1}}", richContent.richText)
        assertEquals(1, richContent.replacements.size)
        assertTrue(richContent.replacements.containsKey("link1"))
        assertEquals(true, singleCheckbox.required)

        // Default value should be false
        assertEquals(false, singleCheckbox.value)
        val requiredErrors = singleCheckbox.validate()
        assertTrue(requiredErrors.isNotEmpty())

        // Set the value to true and validate
        singleCheckbox.value = true
        val validationResult = singleCheckbox.validate() // Should return empty list since it's valid
        assertTrue(validationResult.isEmpty())
    }

    @TestRailCase(26033)
    @Test
    fun flowButtonCollectorTest() = runTest {
        // Go to the "Form Fields" form
        var node = daVinci.start() as ContinueNode
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // 11th collector in the form is a FlowButton (index 10)
        assertTrue(node.collectors[FLOW_BUTTON_INDEX] is FlowCollector)
        val flowButton = (node.collectors[FLOW_BUTTON_INDEX] as FlowCollector)

        // Assert the properties
        assertEquals("FLOW_BUTTON", flowButton.type)
        assertEquals("flow-button-field", flowButton.key)
        assertEquals("Flow Button", flowButton.label)

        flowButton.value = "action"
        node = node.next() as ContinueNode

        // Make sure that we advanced to the next node
        assertEquals("Success", node.name)
    }

    @TestRailCase(26033)
    @Test
    fun flowLinkCollectorTest() = runTest {
        // Go to the "Form Fields" form
        var node = daVinci.start() as ContinueNode
        (node.collectors[0] as? SubmitCollector)?.value = "click"
        node = node.next() as ContinueNode

        // 12th collector in the form is a FlowLink (index 11)
        assertTrue(node.collectors[FLOW_LINK_INDEX] is FlowCollector)
        val flowLink = (node.collectors[FLOW_LINK_INDEX] as FlowCollector)

        // Assert the properties
        assertEquals("FLOW_LINK", flowLink.type)
        assertEquals("flow-link-field", flowLink.key)
        assertEquals("Flow Link", flowLink.label)

        flowLink.value = "action"
        node = node.next() as ContinueNode

        // Make sure that we advanced to the next node
        assertEquals("Success", node.name)
    }
}