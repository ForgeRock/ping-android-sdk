/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.BooleanCollectorAppearance
import com.pingidentity.davinci.collector.BooleanCollector
import com.pingidentity.davinci.collector.Required
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class BooleanCollectorTest {

    @Test
    fun initializesDefaultValuesWhenNoInputProvided() {
        val input = buildJsonObject {}
        val collector = BooleanCollector()
        collector.init(input)

        assertEquals(BooleanCollectorAppearance.CHECKBOX, collector.appearance)
        assertEquals("This field is required.", collector.errorMessage)
        assertNull(collector.richContent)
        assertFalse(collector.value)
    }

    @Test
    fun initializesAppearanceAsCheckbox() {
        val input = buildJsonObject {
            put("appearance", "CHECKBOX")
        }
        val collector = BooleanCollector()
        collector.init(input)

        assertEquals(BooleanCollectorAppearance.CHECKBOX, collector.appearance)
    }

    @Test
    fun initializesAppearanceAsSwitch() {
        val input = buildJsonObject {
            put("appearance", "SWITCH")
        }
        val collector = BooleanCollector()
        collector.init(input)

        assertEquals(BooleanCollectorAppearance.SWITCH, collector.appearance)
    }

    @Test
    fun initializesAppearanceAsCheckboxWhenUnknownValueProvided() {
        val input = buildJsonObject {
            put("appearance", "UNKNOWN")
        }
        val collector = BooleanCollector()
        collector.init(input)

        assertEquals(BooleanCollectorAppearance.CHECKBOX, collector.appearance)
    }

    @Test
    fun initializesErrorMessage() {
        val input = buildJsonObject {
            put("errorMessage", "Select the checkbox to continue.")
        }
        val collector = BooleanCollector()
        collector.init(input)

        assertEquals("Select the checkbox to continue.", collector.errorMessage)
    }

    @Test
    fun initializesRichContent() {
        val input = buildJsonObject {
            put("richContent", buildJsonObject {
                put("content", "This is a sample checkbox test.")
            })
        }
        val collector = BooleanCollector()
        collector.init(input)
        val richContent = collector.richContent
        assertNotNull(richContent)
        assertEquals("This is a sample checkbox test.", richContent.content)
    }

    @Test
    fun initializeRichContentWithLinks() {
        val input = buildJsonObject {
            put("richContent", buildJsonObject {
                put("content", "A translatable rich text to take the user to {{link1}}")
                put("replacements", buildJsonObject {
                    put("link1", buildJsonObject {
                        put("value", "Google")
                        put("href", "https://www.google.com")
                        put("type", "link")
                        put("target", "_self")
                    })
                })
            })
        }
        val collector = BooleanCollector()
        collector.init(input)
        val richContent = collector.richContent
        assertNotNull(richContent)
        assertEquals("A translatable rich text to take the user to {{link1}}", richContent.content)
        assertTrue(richContent.replacements.isNotEmpty())
        assertEquals(1, richContent.replacements.size)

        val link1 = richContent.replacements["link1"]
        assertEquals(RichContentReplacement(
            value = "Google",
            href = "https://www.google.com",
            type = "link",
            target = "_self"
        ), link1)
    }


    @Test
    fun validateReturnsNoErrorsWhenRequiredAndChecked() {
        val input = buildJsonObject {
            put("required", true)
            put("errorMessage", "Select the checkbox to continue.")
        }
        val collector = BooleanCollector()
        collector.init(input)
        collector.value = true

        val errors = collector.validate()

        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateReturnsErrorWhenRequiredAndUnchecked() {
        val input = buildJsonObject {
            put("required", true)
            put("errorMessage", "Select the checkbox to continue.")
        }
        val collector = BooleanCollector()
        collector.init(input)
        collector.value = false

        val errors = collector.validate()

        assertTrue(errors.isNotEmpty())
        assertIs<Required>(errors[0])
    }

    @Test
    fun validateReturnsNoErrorsWhenNotRequired() {
        val input = buildJsonObject {
            put("required", false)
        }
        val collector = BooleanCollector()
        collector.init(input)
        collector.value = false

        val errors = collector.validate()

        assertTrue(errors.isEmpty())
    }

    @Test
    fun payloadReturnsTrueWhenChecked() {
        val collector = BooleanCollector()
        collector.value = true

        assertTrue(collector.payload())
    }

    @Test
    fun payloadReturnsFalseWhenUnchecked() {
        val collector = BooleanCollector()
        collector.value = false

        assertFalse(collector.payload())
    }
}

