/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the OathCodeInfo class.
 *
 * These tests verify the creation and conversion of OathCodeInfo objects,
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class OathCodeInfoTest {

    private val testCode = "123456"
    
    @Test
    fun testForTotp_withValidParams() {
        val timeRemaining = 15
        val totalPeriod = 30
        val expectedProgress = 0.5 // 1.0 - (15/30)
        
        val codeInfo = OathCodeInfo.forTotp(testCode, timeRemaining, totalPeriod)
        
        assertEquals(testCode, codeInfo.code)
        assertEquals(timeRemaining, codeInfo.timeRemaining)
        assertEquals(-1L, codeInfo.counter)
        assertEquals(expectedProgress, codeInfo.progress, 0.001) // Allow for small floating point errors
        assertEquals(totalPeriod, codeInfo.totalPeriod)
    }
    
    @Test
    fun testForTotp_withZeroTotalPeriod() {
        val timeRemaining = 15
        val totalPeriod = 0
        
        val codeInfo = OathCodeInfo.forTotp(testCode, timeRemaining, totalPeriod)
        
        assertEquals(testCode, codeInfo.code)
        assertEquals(timeRemaining, codeInfo.timeRemaining)
        assertEquals(-1L, codeInfo.counter)
        assertEquals(0.0, codeInfo.progress, 0.001)
        assertEquals(totalPeriod, codeInfo.totalPeriod)
    }
    
    @Test
    fun testForHotp_withValidParams() {
        val counter = 10L
        
        val codeInfo = OathCodeInfo.forHotp(testCode, counter)
        
        assertEquals(testCode, codeInfo.code)
        assertEquals(-1, codeInfo.timeRemaining)
        assertEquals(counter, codeInfo.counter)
        assertEquals(0.0, codeInfo.progress, 0.001)
        assertEquals(0, codeInfo.totalPeriod)
    }
    
    @Test
    fun testToJson_forTotp() {
        val codeInfo = OathCodeInfo.forTotp(testCode, 15, 30)
        
        val json = codeInfo.toJson()
        
        assertTrue(json.contains("\"code\":\"$testCode\""))
        assertTrue(json.contains("\"timeRemaining\":15"))
        assertTrue(json.contains("\"counter\":-1"))
        assertTrue(json.contains("\"progress\":0.5"))
        assertTrue(json.contains("\"totalPeriod\":30"))
    }
    
    @Test
    fun testToJson_forHotp() {
        val codeInfo = OathCodeInfo.forHotp(testCode, 10)
        
        val json = codeInfo.toJson()
        
        assertTrue(json.contains("\"code\":\"$testCode\""))
        assertTrue(json.contains("\"timeRemaining\":-1"))
        assertTrue(json.contains("\"counter\":10"))
        assertTrue(json.contains("\"progress\":0.0"))
        assertTrue(json.contains("\"totalPeriod\":0"))
    }
    
    @Test
    fun testFromJson_forTotp() {
        val jsonString = """
            {
                "code": "$testCode",
                "timeRemaining": 15,
                "counter": -1,
                "progress": 0.5,
                "totalPeriod": 30
            }
        """.trimIndent()
        
        val codeInfo = OathCodeInfo.fromJson(jsonString)
        
        assertEquals(testCode, codeInfo.code)
        assertEquals(15, codeInfo.timeRemaining)
        assertEquals(-1L, codeInfo.counter)
        assertEquals(0.5, codeInfo.progress, 0.001)
        assertEquals(30, codeInfo.totalPeriod)
    }
    
    @Test
    fun testFromJson_forHotp() {
        val jsonString = """
            {
                "code": "$testCode",
                "timeRemaining": -1,
                "counter": 10,
                "progress": 0.0,
                "totalPeriod": 0
            }
        """.trimIndent()
        
        val codeInfo = OathCodeInfo.fromJson(jsonString)
        
        assertEquals(testCode, codeInfo.code)
        assertEquals(-1, codeInfo.timeRemaining)
        assertEquals(10L, codeInfo.counter)
        assertEquals(0.0, codeInfo.progress, 0.001)
        assertEquals(0, codeInfo.totalPeriod)
    }
    
    @Test
    fun testJsonSerialization_roundTrip() {
        val originalCodeInfo = OathCodeInfo(
            code = testCode,
            timeRemaining = 15,
            counter = -1,
            progress = 0.5,
            totalPeriod = 30
        )
        
        // Convert to JSON
        val json = originalCodeInfo.toJson()
        
        // Parse back from JSON
        val parsedCodeInfo = OathCodeInfo.fromJson(json)
        
        // Verify the parsed code info matches the original
        assertEquals(originalCodeInfo.code, parsedCodeInfo.code)
        assertEquals(originalCodeInfo.timeRemaining, parsedCodeInfo.timeRemaining)
        assertEquals(originalCodeInfo.counter, parsedCodeInfo.counter)
        assertEquals(originalCodeInfo.progress, parsedCodeInfo.progress, 0.001)
        assertEquals(originalCodeInfo.totalPeriod, parsedCodeInfo.totalPeriod)
    }
    
    @Test
    fun testFromJson_withExtraFields() {
        val jsonString = """
            {
                "code": "$testCode",
                "timeRemaining": 15,
                "counter": -1,
                "progress": 0.5,
                "totalPeriod": 30,
                "extraField": "This should be ignored"
            }
        """.trimIndent()
        
        val codeInfo = OathCodeInfo.fromJson(jsonString)
        
        assertEquals(testCode, codeInfo.code)
        assertEquals(15, codeInfo.timeRemaining)
        assertEquals(-1L, codeInfo.counter)
        assertEquals(0.5, codeInfo.progress, 0.001)
        assertEquals(30, codeInfo.totalPeriod)
    }
    
    @Test
    fun testFromJson_withMalformedJson() {
        val jsonString = """
            {
                "code": "$testCode",
                "timeRemaining": "not-an-integer",
                "counter": -1,
                "progress": 0.5,
                "totalPeriod": 30
            }
        """.trimIndent()
        
        try {
            OathCodeInfo.fromJson(jsonString)
            assertTrue("Expected exception was not thrown", false)
        } catch (e: Exception) {
            // Exception is expected
            assertTrue(true)
        }
    }
    
    @Test
    fun testForTotp_withNegativeTimeRemaining() {
        val timeRemaining = -5
        val totalPeriod = 30
        
        val codeInfo = OathCodeInfo.forTotp(testCode, timeRemaining, totalPeriod)
        
        assertEquals(testCode, codeInfo.code)
        assertEquals(timeRemaining, codeInfo.timeRemaining)
        assertEquals(-1L, codeInfo.counter)
        // Progress should be greater than 1.0 since time remaining is negative
        assertEquals(1.0 + (5.0/30.0), codeInfo.progress, 0.001)
        assertEquals(totalPeriod, codeInfo.totalPeriod)
    }
    
    @Test
    fun testForTotp_withTimeRemainingGreaterThanTotalPeriod() {
        val timeRemaining = 45
        val totalPeriod = 30
        
        val codeInfo = OathCodeInfo.forTotp(testCode, timeRemaining, totalPeriod)
        
        assertEquals(testCode, codeInfo.code)
        assertEquals(timeRemaining, codeInfo.timeRemaining)
        assertEquals(-1L, codeInfo.counter)
        // Progress should be negative since time remaining is greater than total period
        assertEquals(-0.5, codeInfo.progress, 0.001)
        assertEquals(totalPeriod, codeInfo.totalPeriod)
    }
    
    @Test
    fun testDataClassEquality() {
        val codeInfo1 = OathCodeInfo(
            code = testCode,
            timeRemaining = 15,
            counter = -1,
            progress = 0.5,
            totalPeriod = 30
        )
        
        val codeInfo2 = OathCodeInfo(
            code = testCode,
            timeRemaining = 15,
            counter = -1,
            progress = 0.5,
            totalPeriod = 30
        )
        
        val codeInfo3 = OathCodeInfo(
            code = "654321", // Different code
            timeRemaining = 15,
            counter = -1,
            progress = 0.5,
            totalPeriod = 30
        )
        
        // Test equality
        assertEquals(codeInfo1, codeInfo2)
        
        // Test inequality
        assertTrue(codeInfo1 != codeInfo3)
    }
}
