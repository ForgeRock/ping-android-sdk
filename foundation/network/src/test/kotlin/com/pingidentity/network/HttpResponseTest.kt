/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpResponseTest {

    @Test
    fun `isSuccess returns true for 2xx status codes`() {
        for (status in 200 until 300) {
            assertTrue(status.isSuccess(), "Status $status should be successful")
        }
    }

    @Test
    fun `isSuccess returns false for 1xx status codes`() {
        for (status in 100 until 200) {
            assertFalse(status.isSuccess(), "Status $status should not be successful")
        }
    }

    @Test
    fun `isSuccess returns false for status codes`() {
        for (status in 300 until 600) {
            assertFalse(status.isSuccess(), "Status $status should not be successful")
        }
    }
}

