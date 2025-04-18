/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.davinci
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.exception.ApiException
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.Request
import com.pingidentity.orchestrate.WorkflowConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.MalformedURLException
import java.net.URL
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IdpCollectorTest {

    private lateinit var daVinci: DaVinci

    @BeforeTest
    fun setUp() {
        val logger: Logger = mockk()
        val config: WorkflowConfig = mockk()
        daVinci = mockk()
        every { daVinci.config } returns config
        every { config.logger } returns logger
    }

    @Test
    fun `initialize with missing idpEnabled defaults to true`() {
        val jsonObject = buildJsonObject {
            put("idpId", "testId")
            put("idpType", "testType")
            put("label", "testLabel")
            put("links", buildJsonObject {
                put("authenticate", buildJsonObject {
                    put("href", "http://test.com")
                })
            })
        }

        val idpCollector = IdpCollector()

        idpCollector.init(jsonObject)

        assertEquals(true, idpCollector.idpEnabled)
        assertEquals("testId", idpCollector.idpId)
        assertEquals("testType", idpCollector.idpType)
        assertEquals("testLabel", idpCollector.label)
        assertEquals(URL("http://test.com"), idpCollector.link)
    }

    @Test
    fun `initialize with invalid URL throws MalformedURLException`() {
        val jsonObject = buildJsonObject {
            put("idpEnabled", JsonPrimitive(true))
            put("idpId", JsonPrimitive("testId"))
            put("idpType", JsonPrimitive("testType"))
            put("label", JsonPrimitive("testLabel"))
            put("links", buildJsonObject {
                put("authenticate", buildJsonObject {
                    put("href", JsonPrimitive("invalid-url"))
                })
            })
        }
        val idpCollector = IdpCollector()
        assertFailsWith<MalformedURLException> {
            idpCollector.init(jsonObject)
        }
    }


    @Test
    fun `authorize with mock idpRequestHandler returns success`() = runTest {
        val idpRequestHandler: IdpRequestHandler = mockk()
        val idpCollector = IdpCollector().apply {
            init(buildJsonObject {
                put("idpEnabled", JsonPrimitive(true))
                put("idpId", JsonPrimitive("testId"))
                put("idpType", JsonPrimitive("GOOGLE"))
                put("label", JsonPrimitive("testLabel"))
                put("links", buildJsonObject {
                    put("authenticate", buildJsonObject {
                        put("href", JsonPrimitive("http://test.com"))
                    })
                })
            })
        }

        idpCollector.davinci = daVinci

        coEvery { idpRequestHandler.authorize("http://test.com") } returns Request()
        val result = idpCollector.authorize(idpRequestHandler)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `authorize with mock idpRequestHandler throws exception`() = runTest {
        val idpRequestHandler: IdpRequestHandler = mockk()
        val idpCollector = IdpCollector().apply {
            init(buildJsonObject {
                put("idpEnabled", JsonPrimitive(true))
                put("idpId", JsonPrimitive("testId"))
                put("idpType", JsonPrimitive("GOOGLE"))
                put("label", JsonPrimitive("testLabel"))
                put("links", buildJsonObject {
                    put("authenticate", buildJsonObject {
                        put("href", JsonPrimitive("http://test.com"))
                    })
                })
            })
        }
        idpCollector.davinci = daVinci

        coEvery { idpRequestHandler.authorize("http://test.com") } throws
                ApiException(404, "Unsupported IDP")

        val result = idpCollector.authorize(idpRequestHandler)
        assertTrue(result.isFailure)
        assertTrue { result.exceptionOrNull() is ApiException }
    }
}