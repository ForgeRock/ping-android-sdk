/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp

import com.pingidentity.idp.davinci.IdpCollector
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.MalformedURLException
import java.net.URL
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IdpCollectorTest {

    private lateinit var idpCollector: IdpCollector

    @BeforeTest
    fun setUp() {
        idpCollector = IdpCollector()
    }

    @Test
    fun `initialize with valid json object`() {
        val jsonObject = buildJsonObject {
            put("idpEnabled", true)
            put("idpId", "testId")
            put("idpType", "testType")
            put("label", "testLabel")
            put("links", buildJsonObject {
                put("authenticate", buildJsonObject {
                    put("href", "http://test.com")
                })
            })
        }

        idpCollector.init(jsonObject)

        assertEquals(true, idpCollector.idpEnabled)
        assertEquals("testId", idpCollector.idpId)
        assertEquals("testType", idpCollector.idpType)
        assertEquals("testLabel", idpCollector.label)
        assertEquals(URL("http://test.com"), idpCollector.link)
    }

    @Test(expected = MalformedURLException::class)
    fun `initialize with empty json object`() {
        val jsonObject = buildJsonObject { }
        idpCollector.init(jsonObject)
    }

}