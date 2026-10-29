/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import android.net.Uri
import com.pingidentity.oidc.exception.AuthorizeException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class StateTest {

    @Test
    fun `generateState returns a URL-safe value without padding`() {
        val state = generateState()
        assertEquals(43, state.length) // 32 bytes Base64 URL-safe, padding trimmed
        assertTrue { state.none { it == '+' || it == '/' || it == '=' } }
    }

    @Test
    fun `generateState is random`() {
        assertNotEquals(generateState(), generateState())
    }

    @Test
    fun `validateState passes on match`() {
        validateState(
            Uri.parse("https://example.com/callback?code=abc&state=abc123"),
            "abc123",
        )
    }

    @Test
    fun `validateState throws on mismatch`() {
        val exception = assertFailsWith<AuthorizeException> {
            validateState(
                Uri.parse("https://example.com/callback?code=abc&state=wrong"),
                "abc123",
            )
        }
        assertContains(exception.message ?: "", "State mismatch")
    }

    @Test
    fun `validateState throws when state is missing`() {
        val exception = assertFailsWith<AuthorizeException> {
            validateState(
                Uri.parse("https://example.com/callback?code=abc"),
                "abc123",
            )
        }
        assertContains(exception.message ?: "", "did not include the state parameter")
    }

    @Test
    fun `validateState fails when no expected state was recorded`() {
        // A state is always sent with web authorization flows, so a null expected state
        // means the flow never recorded one — fail rather than skip.
        val exception = assertFailsWith<AuthorizeException> {
            validateState(
                Uri.parse("https://example.com/callback?code=abc&state=anything"),
                null,
            )
        }
        assertContains(exception.message ?: "", "No expected state was recorded")
        assertFailsWith<AuthorizeException> {
            validateState(
                Uri.parse("https://example.com/callback?code=abc"),
                null,
            )
        }
    }

    @Test
    fun `extractOAuthError reads error params from the query`() {
        val message = extractOAuthError(
            Uri.parse("https://example.com/callback?error=access_denied&error_description=User+denied+consent"),
        )
        assertEquals(
            "The authorization server returned an error: access_denied — User denied consent",
            message,
        )
    }

    @Test
    fun `extractOAuthError reads error params from the fragment`() {
        val message = extractOAuthError(
            Uri.parse("https://example.com/callback#error=access_denied&error_uri=https://docs.example.com/err"),
        )
        assertEquals(
            "The authorization server returned an error: access_denied (see https://docs.example.com/err)",
            message,
        )
    }

    @Test
    fun `extractOAuthError returns null when the redirect is not an error`() {
        assertNull(
            extractOAuthError(Uri.parse("https://example.com/callback?code=abc&state=xyz")),
        )
    }

    @Test
    fun `validateState accepts state in the fragment`() {
        validateState(
            Uri.parse("https://example.com/callback#code=abc&state=abc123"),
            "abc123",
        )
    }

    @Test
    fun `validateState decodes the fragment state exactly once`() {
        // state=a%2520b on the wire encodes the value "a%20b": the fragment lookup must
        // decode it exactly once, like the query-string lookup does.
        validateState(
            Uri.parse("https://example.com/callback#state=a%2520b"),
            "a%20b",
        )
    }

    @Test
    fun `validateState throws when fragment state is missing`() {
        assertFailsWith<AuthorizeException> {
            validateState(
                Uri.parse("https://example.com/callback#code=abc"),
                "abc123",
            )
        }
    }

    @Test
    fun `validateState prefers query over fragment`() {
        // Query wins over fragment; here the query state matches.
        validateState(
            Uri.parse("https://example.com/callback?state=fromQuery#state=fromFragment"),
            "fromQuery",
        )
        // And a mismatching query value fails even when the fragment matches.
        assertFailsWith<AuthorizeException> {
            validateState(
                Uri.parse("https://example.com/callback?state=fromQuery#state=fromFragment"),
                "fromFragment",
            )
        }
    }
}
