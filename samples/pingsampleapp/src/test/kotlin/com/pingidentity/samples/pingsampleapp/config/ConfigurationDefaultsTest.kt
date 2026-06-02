/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigurationDefaultsTest {

    @Test
    fun `parseSourceUrl returns URL from plain text file`() {
        val text = "https://raw.githubusercontent.com/org/repo/main/ping_sdk_config.json"
        assertEquals(text, ConfigurationDefaults.parseSourceUrl(text))
    }

    @Test
    fun `parseSourceUrl skips comment lines and returns first non-comment`() {
        val text = """
            # This is a comment
            # Another comment
            https://cdn.example.com/ping_sdk_config.json
            https://should-be-ignored.example.com/config.json
        """.trimIndent()
        assertEquals(
            "https://cdn.example.com/ping_sdk_config.json",
            ConfigurationDefaults.parseSourceUrl(text)
        )
    }

    @Test
    fun `parseSourceUrl returns null when all lines are comments`() {
        val text = """
            # Remote URL for the shared SDK configuration.
            # Leave blank to skip remote fetching.
        """.trimIndent()
        assertNull(ConfigurationDefaults.parseSourceUrl(text))
    }

    @Test
    fun `parseSourceUrl returns null for blank file`() {
        assertNull(ConfigurationDefaults.parseSourceUrl(""))
        assertNull(ConfigurationDefaults.parseSourceUrl("   "))
        assertNull(ConfigurationDefaults.parseSourceUrl("\n\n\n"))
    }

    @Test
    fun `parseSourceUrl trims leading and trailing whitespace from the URL`() {
        val text = "   https://example.com/config.json   "
        assertEquals("https://example.com/config.json", ConfigurationDefaults.parseSourceUrl(text))
    }

    @Test
    fun `parseSourceUrl handles Windows-style CRLF line endings`() {
        val text = "# comment\r\nhttps://example.com/config.json\r\n"
        assertEquals("https://example.com/config.json", ConfigurationDefaults.parseSourceUrl(text))
    }
}
