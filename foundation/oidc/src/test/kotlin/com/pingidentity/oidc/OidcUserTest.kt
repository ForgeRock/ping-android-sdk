/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import com.pingidentity.utils.Result.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.rules.TestWatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OidcUserTest {

    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    @TestRailCase(22100)
    @Test
    fun `token should return success result`() = runTest {
        val mockOidcClient = mockk<OidcClient>()
        val expectedToken = Token("accessToken", "refreshToken")
        coEvery { mockOidcClient.token() } returns Success(expectedToken)

        val oidcUser: User = OidcUser(mockOidcClient)
        val result = oidcUser.token()

        assertTrue(result is Success)
        assertEquals(expectedToken, result.value)
    }

    @TestRailCase(22101)
    @Test
    fun `token should return error result`() = runTest {
        val mockOidcClient = mockk<OidcClient>()
        val expectedError = OidcError.ApiError(404, "")
        coEvery { mockOidcClient.token() } returns Failure(expectedError)

        val oidcUser: User = OidcUser(mockOidcClient)
        val result = oidcUser.token()

        assertTrue(result is Failure)
        assertEquals(expectedError, result.value)
    }

    @TestRailCase(22102)
    @Test
    fun `revoke should call oidcClient revoke`() = runTest {
        val mockOidcClient = mockk<OidcClient>(relaxed = true)

        val oidcUser: User = OidcUser(mockOidcClient)
        oidcUser.revoke()

        coVerify { mockOidcClient.revoke() }
    }

    @TestRailCase(22103)
    @Test
    fun `userinfo should return cached userinfo if cache is true`() = runTest {
        val mockOidcClient = mockk<OidcClient>()
        val cachedUserinfo = buildJsonObject { put("name", "John Doe") }
        val oidcUser = OidcUser(mockOidcClient)
        oidcUser.userinfo = cachedUserinfo

        val result = oidcUser.userinfo(true)

        assertTrue(result is Success)
        assertEquals(cachedUserinfo, result.value)
    }

    @TestRailCase(22104)
    @Test
    fun `userinfo should fetch userinfo if cache is false`() = runTest {
        val mockOidcClient = mockk<OidcClient>()
        val fetchedUserinfo = buildJsonObject { put("name", "John Doe") }
        coEvery { mockOidcClient.userinfo() } returns Success(fetchedUserinfo)

        val oidcUser: User = OidcUser(mockOidcClient)
        val result = oidcUser.userinfo(false)

        assertTrue(result is Success)
        assertEquals(fetchedUserinfo, result.value)
    }

    @TestRailCase(22105)
    @Test
    fun `logout should call oidcClient endSession`() = runTest {
        val mockOidcClient = mockk<OidcClient>(relaxed = true)

        val oidcUser: User = OidcUser(mockOidcClient)
        oidcUser.logout()

        coVerify { mockOidcClient.endSession() }
    }
}