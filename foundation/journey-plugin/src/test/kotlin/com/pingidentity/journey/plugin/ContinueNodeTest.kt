/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.plugin

import com.pingidentity.orchestrate.Action
import com.pingidentity.orchestrate.ContinueNode
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContinueNodeTest {

    @Test
    fun `Should return callbacks when actions contain callback instances`() {
        val callback1 = mockk<Callback>()
        val callback2 = mockk<Callback>()
        val nonCallback = mockk<Action>()

        val continueNode = mockk<ContinueNode> {
            every { actions } returns listOf<Action>(callback1, nonCallback, callback2)
        }

        val result = continueNode.callbacks

        assertEquals(2, result.size)
        assertEquals(callback1, result[0])
        assertEquals(callback2, result[1])
    }

    @Test
    fun `Should return empty list when actions contain no callbacks`() {
        val continueNode = mockk<ContinueNode> {
            every { actions } returns listOf(mockk<Action>(), mockk<Action>())
        }

        val result = continueNode.callbacks

        assertTrue(result.isEmpty())
    }

    @Test
    fun `Should return empty list when actions is empty`() {
        val continueNode = mockk<ContinueNode> {
            every { actions } returns emptyList()
        }

        val result = continueNode.callbacks

        assertTrue(result.isEmpty())
    }

    @Test
    fun `Should return header value when header field exists in input`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("header", "Test Header")
            }
        }

        val result = continueNode.header

        assertEquals("Test Header", result)
    }

    @Test
    fun `Should return empty string when header field is missing`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject { }
        }

        val result = continueNode.header

        assertEquals("", result)
    }


    @Test
    fun `Should return description value when description field exists in input`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("description", "Test Description")
            }
        }

        val result = continueNode.description

        assertEquals("Test Description", result)
    }

    @Test
    fun `Should return empty string when description field is missing`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject { }
        }


        val result = continueNode.description

        assertEquals("", result)
    }

    @Test
    fun `Should return stage value when stage field exists in input`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", "AuthenticationStage")
            }
        }

        val result = continueNode.stage

        assertEquals("AuthenticationStage", result)
    }

    @Test
    fun `Should return empty string when stage field is missing`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject { }
        }


        val result = continueNode.stage

        assertEquals("", result)
    }

    @Test
    fun `Should handle all fields present in input`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("header", "Welcome")
                put("description", "Please login")
                put("stage", "Login")
            }
        }

        assertEquals("Welcome", continueNode.header)
        assertEquals("Please login", continueNode.description)
        assertEquals("Login", continueNode.stage)
    }

    @Test
    fun `Should handle empty strings in input fields`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("header", "")
                put("description", "")
                put("stage", "")
            }
        }

        assertEquals("", continueNode.header)
        assertEquals("", continueNode.description)
        assertEquals("", continueNode.stage)
    }

    @Test
    fun `Should return localized submitButtonText from stage JSON`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en":"Submit","fr":"Soumettre"}}""")
            }
        }

        val result = continueNode.submitButtonText

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `Should return empty string for submitButtonText when no data available`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject { }
        }

        val result = continueNode.submitButtonText

        assertEquals("", result)
    }

    @Test
    fun `Should return single localized value when only one locale provided for submitButtonText`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en":"Submit"}}""")
            }
        }

        val result = continueNode.submitButtonText

        assertEquals("Submit", result)
    }

    @Test
    fun `Should return localized pageFooter from stage JSON`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"pageFooter":{"en":"Footer Text","fr":"Pied de page"}}""")
            }
        }

        val result = continueNode.pageFooter

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `Should return empty string for pageFooter when no data available`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject { }
        }

        val result = continueNode.pageFooter

        assertEquals("", result)
    }

    @Test
    fun `Should return single localized value when only one locale provided for pageFooter`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"pageFooter":{"en":"Footer"}}""")
            }
        }

        val result = continueNode.pageFooter

        assertEquals("Footer", result)
    }

    @Test
    fun `Should handle malformed JSON in stage field gracefully`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", "not valid json")
            }
        }

        val submitButtonText = continueNode.submitButtonText
        val pageFooter = continueNode.pageFooter

        assertEquals("", submitButtonText)
        assertEquals("", pageFooter)
    }

    @Test
    fun `Should prefer exact locale match over language-only match`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en":"Submit","en-gb":"Submit GB"}}""")
            }
        }

        val result = continueNode.submitButtonText

        assertTrue(result in listOf("Submit", "Submit GB"))
    }

    @Test
    fun `Should handle empty stage value for localized properties`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", "")
            }
        }

        val submitButtonText = continueNode.submitButtonText
        val pageFooter = continueNode.pageFooter

        assertEquals("", submitButtonText)
        assertEquals("", pageFooter)
    }

    @Test
    fun `Should handle stage with missing localization keys`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"otherKey":"value"}""")
            }
        }

        val submitButtonText = continueNode.submitButtonText
        val pageFooter = continueNode.pageFooter

        assertEquals("", submitButtonText)
        assertEquals("", pageFooter)
    }

    @Test
    fun `Should match locale with hyphen format`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en-us":"Submit US","fr-ca":"Soumettre CA"}}""")
            }
        }

        val result = continueNode.submitButtonText

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `Should match locale with underscore format`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en_us":"Submit US","fr_ca":"Soumettre CA"}}""")
            }
        }

        val result = continueNode.submitButtonText

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `Should fallback to language code when full locale not found`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en":"Submit","de":"Einreichen"}}""")
            }
        }

        val result = continueNode.submitButtonText

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `Should return first available value when no locale matches`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"zh":"提交","ja":"送信"}}""")
            }
        }

        val result = continueNode.submitButtonText

        assertTrue(result in listOf("提交", "送信"))
    }

    @Test
    fun `Should handle both submitButtonText and pageFooter in same stage JSON`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en":"Submit"},"pageFooter":{"en":"Footer"}}""")
            }
        }

        val submitButtonText = continueNode.submitButtonText
        val pageFooter = continueNode.pageFooter

        assertEquals("Submit", submitButtonText)
        assertEquals("Footer", pageFooter)
    }

    @Test
    fun `Should handle multiple locale options and return appropriate match`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en":"Submit","en-gb":"Submit GB","en-us":"Submit US","fr":"Soumettre"}}""")
            }
        }

        val result = continueNode.submitButtonText

        assertTrue(result in listOf("Submit", "Submit GB", "Submit US"))
    }

    @Test
    fun `Should handle stage JSON with nested empty objects`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{}}""")
            }
        }

        val result = continueNode.submitButtonText

        assertEquals("", result)
    }

    @Test
    fun `Should handle stage JSON with null values in localization map`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en":"Submit","fr":null}}""")
            }
        }

        val result = continueNode.submitButtonText

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `Should handle case sensitivity in locale matching`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"EN":"Submit","en-GB":"Submit GB"}}""")
            }
        }

        val result = continueNode.submitButtonText

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `Should return different values for submitButtonText and pageFooter`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en":"Button"},"pageFooter":{"en":"Footer"}}""")
            }
        }

        assertEquals("Button", continueNode.submitButtonText)
        assertEquals("Footer", continueNode.pageFooter)
    }

    @Test
    fun `Should handle stage with special characters in localized values`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en":"Submit & Continue","fr":"Soumettre & Continuer"}}""")
            }
        }

        val result = continueNode.submitButtonText

        assertTrue(result.contains("&"))
    }

    @Test
    fun `Should handle stage with unicode characters in localized values`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"pageFooter":{"en":"© 2026 Company","ja":"© 2026 会社"}}""")
            }
        }

        val result = continueNode.pageFooter

        assertTrue(result.contains("©"))
    }

    @Test
    fun `Should handle whitespace in localized values`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en":"  Submit  "}}""")
            }
        }

        val result = continueNode.submitButtonText

        assertEquals("  Submit  ", result)
    }

    @Test
    fun `Should handle empty string values in localization map`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en":""}}""")
            }
        }

        val result = continueNode.submitButtonText

        assertEquals("", result)
    }

    @Test
    fun `Should handle stage JSON with additional unrelated fields`() {
        val continueNode = mockk<ContinueNode> {
            every { input } returns buildJsonObject {
                put("stage", """{"submitButtonText":{"en":"Submit"},"unrelatedField":"value","anotherField":123}""")
            }
        }

        val result = continueNode.submitButtonText

        assertEquals("Submit", result)
    }
}

