/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.detector

import android.content.Context
import com.pingidentity.android.ContextProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class FileDetectorTest {
    private val context: Context = mockk()
    private val mockFile = mockk<File>()

    @BeforeTest
    fun setup() {
        every { context.applicationContext } returns mockk()
        ContextProvider.init(context)
    }

    @Test
    fun `FileDetector checks for existence of files`() = runTest {

        every { mockFile.exists() } returns true

        val testDetector = object : FileDetector() {
            override fun getFilenames(): List<String> {
                return listOf("testfile1", "testfile2")
            }
        }

        val result = testDetector.analyze(context)
        assertEquals(0.0, result)
    }

    @Ignore("Needs to be run on a real device or emulator without busybox installed")
    @Test
    fun `BusyBoxProgramFileDetector detects busybox file presence`(): Unit = runTest {
        val busyBoxDetector = BusyBoxProgramFileDetector()
        val result = busyBoxDetector.analyze(context)
        assertEquals(0.0, result)
    }

    @Test
    fun `NativeDetector detects su file presence`(): Unit = runTest {
        val nativeDetector = NativeDetector()
        val result = nativeDetector.analyze(context)
        assertEquals(0.0, result)
    }

    @Test
    fun `RootProgramFileDetector detects root program filenames to check for tampering detection`() = runTest {
        val rootProgramFileDetector = RootProgramFileDetector()
        val result = rootProgramFileDetector.analyze(context)
        assertEquals(1.0, result)
    }
}