/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.collector.escapeHtml
import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlEscaperTest {

    // ── individual character escapes ──────────────────────────────────────────

    @Test
    fun `escapeHtml escapes ampersand`() {
        assertEquals("AT&amp;T", "AT&T".escapeHtml())
    }

    @Test
    fun `escapeHtml escapes less-than`() {
        assertEquals("&lt;script&gt;", "<script>".escapeHtml())
    }

    @Test
    fun `escapeHtml escapes greater-than`() {
        assertEquals("1 &gt; 0", "1 > 0".escapeHtml())
    }

    @Test
    fun `escapeHtml escapes double-quote`() {
        assertEquals("say &quot;hello&quot;", """say "hello"""".escapeHtml())
    }

    @Test
    fun `escapeHtml escapes single-quote`() {
        assertEquals("it&#39;s fine", "it's fine".escapeHtml())
    }

    // ── order-of-operations: & must be replaced before any other char ─────────

    @Test
    fun `escapeHtml does not double-escape ampersand`() {
        // "&amp;" already contains "&"; a naïve second pass would produce "&amp;amp;"
        assertEquals("&amp;amp;", "&amp;".escapeHtml())
    }

    @Test
    fun `escapeHtml does not produce double-encoded entities for lt`() {
        // Input: "&lt;" — the & becomes &amp; first, then < is absent, so result is "&amp;lt;"
        assertEquals("&amp;lt;", "&lt;".escapeHtml())
    }

    // ── realistic href scenarios ──────────────────────────────────────────────

    @Test
    fun `escapeHtml makes URL with query params safe for href attribute`() {
        val raw = "https://example.com?a=1&b=2"
        assertEquals("https://example.com?a=1&amp;b=2", raw.escapeHtml())
    }

    @Test
    fun `escapeHtml neutralises href injection attempt via double-quote`() {
        val malicious = """https://evil.com" onclick="stealData()"""
        val escaped = malicious.escapeHtml()
        // The closing " must be escaped so it cannot terminate the href attribute
        assertEquals("""https://evil.com&quot; onclick=&quot;stealData()""", escaped)
    }

    @Test
    fun `escapeHtml neutralises href injection attempt via single-quote`() {
        val malicious = "https://evil.com' onclick='stealData()"
        val escaped = malicious.escapeHtml()
        assertEquals("https://evil.com&#39; onclick=&#39;stealData()", escaped)
    }

    // ── realistic display-text scenarios ─────────────────────────────────────

    @Test
    fun `escapeHtml neutralises script injection in display text`() {
        val malicious = "<script>alert(1)</script>"
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", malicious.escapeHtml())
    }

    @Test
    fun `escapeHtml leaves plain text unchanged`() {
        val plain = "Visit google.com for more info"
        assertEquals(plain, plain.escapeHtml())
    }

    @Test
    fun `escapeHtml handles empty string`() {
        assertEquals("", "".escapeHtml())
    }

    @Test
    fun `escapeHtml escapes all dangerous characters together`() {
        val input = """<a href="bad'url&more">text</a>"""
        assertEquals("""&lt;a href=&quot;bad&#39;url&amp;more&quot;&gt;text&lt;/a&gt;""", input.escapeHtml())
    }
}

