/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect

import android.content.Context
import com.pingidentity.android.ContextProvider
import com.pingidentity.orchestrate.EmptySession
import com.pingidentity.orchestrate.Module
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.Workflow
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtectLifecycleTest {
    private val context = mockk<Context>(relaxed = true)
    private lateinit var mockEngine: MockEngine

    @BeforeTest
    fun setup() {
        mockkObject(Protect)
        coEvery { Protect.init() } returns Unit
        mockkObject(ContextProvider)
        every { ContextProvider.context } returns context
        mockEngine = MockEngine {
            return@MockEngine respond(
                content =
                    ByteReadChannel(""),
                status = HttpStatusCode.InternalServerError,
            )
        }
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(Protect)
        unmockkObject(ContextProvider)
    }

    @Test
    fun initConfiguresAndInitializesProtect() = runTest {
        var configApplied: ProtectConfig? = null
        every { Protect.config(any()) } answers {
            val block = arg<(ProtectConfig.() -> Unit)>(0)
            val pc = ProtectConfig()
            pc.block()
            configApplied = pc
        }
        coEvery { Protect.init() } returns Unit

        val workflow = Workflow() {
            module(ProtectLifecycle) {
                envId = "env"
                deviceAttributesToIgnore = listOf("a")
                customHost = "host"
                isConsoleLogEnabled = true
                isLazyMetadata = true
                isBehavioralDataCollection = false

                pauseBehavioralDataOnSuccess =
                    true // Pause data collection after successful authentication.
                resumeBehavioralDataOnStart = true // Resume data collection on application start.
            }
        }

        workflow.init()
        assertEquals("env", configApplied?.envId)
        assertEquals(listOf("a"), configApplied?.deviceAttributesToIgnore)
        assertEquals("host", configApplied?.customHost)
        assertEquals(true, configApplied?.isConsoleLogEnabled)
        assertEquals(true, configApplied?.isLazyMetadata)
        assertEquals(false, configApplied?.isBehavioralDataCollection)
    }

    @Test
    fun startResumesBehavioralDataIfConfigured() = runTest {
        val workflow = Workflow() {
            module(ProtectLifecycle) {
                resumeBehavioralDataOnStart = true // Resume data collection on application start.
            }
        }
        every { Protect.resumeBehavioralData() } just runs
        workflow.start()
        verify(exactly = 1) { Protect.config(any()) }
    }

    @Test
    fun startDoesNotResumeBehavioralDataIfNotConfigured() = runTest {
       val workflow = Workflow() {
            module(ProtectLifecycle) {
                resumeBehavioralDataOnStart = false // Resume data collection on application start.
            }
        }
        every { Protect.resumeBehavioralData() } just runs
        workflow.start()
        verify(exactly = 0) { Protect.resumeBehavioralData() }
    }

    @Test
    fun successPausesBehavioralDataIfConfigured() = runTest {
        val transform = Module.of {
            transform {
                SuccessNode(session = EmptySession)
            }
        }
        val workflow = Workflow() {
            httpClient = HttpClient(mockEngine)
            module(ProtectLifecycle) {
                pauseBehavioralDataOnSuccess = true
            }
            module(transform)
        }
        every { Protect.resumeBehavioralData() } just runs
        every { Protect.pauseBehavioralData() } just runs
        workflow.start()
        verify(exactly = 1) { Protect.pauseBehavioralData() }
    }

    @Test
    fun successDoesNotPauseBehavioralDataIfNotConfigured() = runTest {
        val transform = Module.of {
            transform {
                SuccessNode(session = EmptySession)
            }
        }
        val workflow = Workflow() {
            httpClient = HttpClient(mockEngine)
            module(ProtectLifecycle) {
                pauseBehavioralDataOnSuccess = false
            }
            module(transform)
        }
        every { Protect.resumeBehavioralData() } just runs
        every { Protect.pauseBehavioralData() } just runs
        workflow.start()
        verify(exactly = 0) { Protect.pauseBehavioralData() }
    }

}
