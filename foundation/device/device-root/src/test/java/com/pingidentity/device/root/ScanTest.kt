/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.root

import android.content.Context
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.root.detector.BuildTagsDetector
import com.pingidentity.device.root.detector.RootDetector
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ScanTest {

    @BeforeTest
    fun setup() {
        val context: Context = mockk()
        every { context.applicationContext } returns mockk()
        ContextProvider.init(context)
    }

    @Test
    fun `Test with custom detector`() = runTest {

        val result = scan {
            detector {
                add(RootDetector {
                    0.2
                })
                add(RootDetector {
                    0.5
                })

            }
        }
        assertEquals(0.5, result )
    }

    @Test
    fun testScanWithDefaultDetector() = runTest {
        val result = scan()
        assertEquals(1.0, result) // Assuming no root detected by default detectors
    }

    @Test
    fun `mix with custom and predefined`() = runTest {
        val result = scan {
            detector {
                add(RootDetector { 0.3 })
                add(BuildTagsDetector)
            }
        }
        assertEquals(0.0, result) // Assuming BuildTagsDetector does not detect root
    }
}