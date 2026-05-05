/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.LabelCollector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelCollectorTest {

    @Test
    fun initializesContentWithProvidedValue() {
        val input = buildJsonObject {
            put("key", "Test id")
            put("content", "Test Content")
        }
        val collector = LabelCollector()
        collector.init(input)
        assertEquals("Test Content", collector.content)
        assertEquals("Test id", collector.key)
    }

    @Test
    fun initializesContentWithEmptyStringWhenNoValueProvided() {
        val input = JsonObject(emptyMap())
        val collector = LabelCollector()
        collector.init(input)
        assertEquals("", collector.content)
    }

    @Test
    fun `richContent is null when no richContent field is present`() {
        val input = buildJsonObject {
            put("key", "label-key")
            put("content", "Plain text content")
        }
        val collector = LabelCollector()
        collector.init(input)
        // richContent is only populated when a richContent JSON object is present.
        // The rendering layer (buildRichTextLabel) handles the null fallback to content.
        assertNull(collector.richContent)
        assertEquals("Plain text content", collector.content)
    }

    @Test
    fun `richText is read from richContent content when present`() {
        val input = buildJsonObject {
            put("key", "rich-text")
            put("content", "A translatable rich text to take the user to google.com")
            put("richContent", buildJsonObject {
                put("content", "A translatable rich text to take the user to {{link1}}")
            })
        }
        val collector = LabelCollector()
        collector.init(input)
        assertEquals("A translatable rich text to take the user to google.com", collector.content)
        val richContent = collector.richContent
        assertNotNull(richContent)
        val replacement = richContent.replacements
        assertEquals("A translatable rich text to take the user to {{link1}}", richContent.content)
        assertTrue(replacement.isEmpty())
    }

    @Test
    fun `replacements are parsed from richContent when present`() {
        val input = buildJsonObject {
            put("key", "rich-text")
            put("content", "A translatable rich text to take the user to google.com")
            put("richContent", buildJsonObject {
                put("content", "A translatable rich text to take the user to {{link1}}")
                put("replacements", buildJsonObject {
                    put("link1", buildJsonObject {
                        put("value", "google.com")
                        put("href", "https://www.google.com")
                        put("type", "link")
                        put("target", "_self")
                    })
                })
            })
        }
        val collector = LabelCollector()
        collector.init(input)
        val richContent = collector.richContent

        assertNotNull(richContent)
        assertEquals("A translatable rich text to take the user to {{link1}}", richContent.content)
        assertEquals(1, richContent.replacements.size)

        val link1 = richContent.replacements["link1"]
        assertEquals(RichContentReplacement(
            value = "google.com",
            href = "https://www.google.com",
            type = "link",
            target = "_self"
        ), link1)
    }

    @Test
    fun `replacements map is empty when richContent has no replacements`() {
        val input = buildJsonObject {
            put("key", "rich-text")
            put("richContent", buildJsonObject {
                put("content", "Some rich text")
            })
        }
        val collector = LabelCollector()
        collector.init(input)
        val richContent = collector.richContent
        assertNotNull(richContent)
        assertTrue(richContent.replacements.isEmpty())
    }

    @Test
    fun `richContent is null and content retains HTML when no richContent field is present`() {
        val input = buildJsonObject {
            put("content", "<p><strong><em>Rich Text fields produce LABELs</em></strong></p><hr><p><br></p>")
        }
        val collector = LabelCollector()
        collector.init(input)
        // TEXTBLOB-style labels have no richContent JSON → richContent is null.
        // The raw HTML is stored in content; buildRichTextLabel parses it at render time.
        assertNull(collector.richContent)
        assertEquals("<p><strong><em>Rich Text fields produce LABELs</em></strong></p><hr><p><br></p>", collector.content)
        assertEquals("", collector.key)
    }

    @Test
    fun `richText is set from richContent when content has trailing whitespace`() {
        val input = buildJsonObject {
            put("key", "translatable-rich-text-key")
            put("content", "Translatable Rich Text produce LABELs too!\n\n")
            put("richContent", buildJsonObject {
                put("content", "Translatable Rich Text produce LABELs too!")
            })
        }
        val collector = LabelCollector()
        collector.init(input)
        val richContent = collector.richContent
        assertNotNull(richContent)
        // content retains the original value including trailing newlines
        assertEquals("Translatable Rich Text produce LABELs too!\n\n", collector.content)
        // richText is trimmed to the richContent value
        assertEquals("Translatable Rich Text produce LABELs too!", richContent.content)
        assertEquals("translatable-rich-text-key", collector.key)
        assertTrue(richContent.replacements.isEmpty())
    }

    @Test
    fun `replacement href with query params is stored as-is`() {
        // The server sends raw URLs; escaping is the rendering layer's job
        val input = buildJsonObject {
            put("key", "rich-text")
            put("richContent", buildJsonObject {
                put("content", "Search on {{searchLink}}")
                put("replacements", buildJsonObject {
                    put("searchLink", buildJsonObject {
                        put("value", "Google")
                        put("href", "https://google.com/search?q=hello&lang=en")
                        put("type", "link")
                        put("target", "_self")
                    })
                })
            })
        }
        val collector = LabelCollector()
        collector.init(input)
        val richContent = collector.richContent
        assertNotNull(richContent)

        val replacement = richContent.replacements["searchLink"]
        // href must be raw — escaping happens at render time, not parse time
        assertEquals("https://google.com/search?q=hello&lang=en", replacement?.href)
        assertEquals("Google", replacement?.value)
    }

    @Test
    fun `replacement value with special HTML characters is stored as-is`() {
        val input = buildJsonObject {
            put("key", "rich-text")
            put("richContent", buildJsonObject {
                put("content", "Learn about {{company}}")
                put("replacements", buildJsonObject {
                    put("company", buildJsonObject {
                        put("value", "AT&T <Wireless>")
                        put("href", "https://att.com")
                        put("type", "link")
                        put("target", "_blank")
                    })
                })
            })
        }
        val collector = LabelCollector()
        collector.init(input)
        val richContent = collector.richContent
        assertNotNull(richContent)

        val replacement = richContent.replacements["company"]
        // value must be raw — rendering layer calls escapeHtml() before injecting into HTML
        assertEquals("AT&T <Wireless>", replacement?.value)
    }

    @Test
    fun `replacement href with injection attempt is stored as-is`() {
        // The collector must not modify the server payload — escaping is deferred to render time
        val maliciousHref = """https://evil.com" onclick="stealData()"""
        val input = buildJsonObject {
            put("key", "rich-text")
            put("richContent", buildJsonObject {
                put("content", "Click {{link}}")
                put("replacements", buildJsonObject {
                    put("link", buildJsonObject {
                        put("value", "here")
                        put("href", maliciousHref)
                        put("type", "link")
                        put("target", "_self")
                    })
                })
            })
        }
        val collector = LabelCollector()
        collector.init(input)
        val richContent = collector.richContent
        assertNotNull(richContent)

        // Collector stores raw value; Label.kt's escapeHtml() neutralises it at render time
        assertEquals(maliciousHref, richContent.replacements["link"]?.href)
    }
}