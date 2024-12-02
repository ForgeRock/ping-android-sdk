/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.divinci
import com.pingidentity.orchestrate.Request
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import io.ktor.client.request.forms.FormDataContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class RequestTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @TestRailCase(22125)
    @Test
    fun `url sets the correct url`() {
        val request = Request()
        request.url("http://example.com")
        assertEquals("http://example.com", request.builder.url.toString())
    }

    @TestRailCase(22126)
    @Test
    fun `parameter appends the correct parameter`() {
        val request = Request()
        request.parameter("key", "value")
        assertEquals("value", request.builder.url.parameters["key"])
    }

    @TestRailCase(22127)
    @Test
    fun `header appends the correct header`() {
        val request = Request()
        request.header("Content-Type", "application/json")
        assertEquals("application/json", request.builder.headers["Content-Type"])
    }

    @TestRailCase(22128)
    @Test
    fun `cookies sets the correct cookies`() {
        val request = Request()

        val cookies = listOf("interactionId=178ce234-afd2-4207-984e-bda28bd7042c; Max-Age=3600; Path=/; Expires=Thu, 09 May 2024 21:38:44 GMT; HttpOnly; Secure;",
            "interactionToken=abc; Max-Age=3600; Path=/; Expires=Thu, 09 May 2024 21:38:44 GMT; HttpOnly; Secure")
        request.cookies(cookies)
        request.builder.headers.getAll("Cookie")
        assertContains(request.builder.headers.getAll("Cookie")!![0], "interactionId" )
        assertContains(request.builder.headers.getAll("Cookie")!![0], "interactionToken" )
    }

    @TestRailCase(22129)
    @Test
    fun `body sets the correct body`() {
        val request = Request()
        val json = Json { prettyPrint = true }
        val body = json.parseToJsonElement("""{"key": "value"}""").jsonObject
        request.body(body)
        assertEquals("""{"key":"value"}""", request.builder.body.toString())
    }

    @TestRailCase(22130)
    @Test
    fun `form sets the correct form data`() {
        val request = Request()
        request.form {
            append("key", "value")
        }
        val body = request.builder.body as FormDataContent
        assertEquals("value", body.formData["key"])
    }
}