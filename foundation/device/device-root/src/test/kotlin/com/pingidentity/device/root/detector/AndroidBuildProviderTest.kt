/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root.detector

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidBuildProviderTest {
    private val buildProviderTest = object : AndroidBuildTagProvider {
        override fun getBuildTags(): String? {
            return KEYS
        }
    }

    @Test
    fun `test getBuildTags`(){
        val tags = buildProviderTest.getBuildTags()
        assertEquals(tags, KEYS)
    }

    companion object {
        private const val KEYS = "keys"
    }
}