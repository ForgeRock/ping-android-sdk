/*
 * Copyright (c) 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class FacebookHandlerTest {

    @Test(expected = UnsupportedIdPException::class)
    fun `test missing Facebook SDK throws exception`() = runTest {
        FacebookHandler().authorize(IdpClient())
    }
}