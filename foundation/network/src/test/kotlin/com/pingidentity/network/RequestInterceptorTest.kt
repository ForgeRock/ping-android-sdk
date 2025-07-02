/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.network

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestInterceptorTest {

    @Test
    fun `test request interceptors are applied in priority order`() {
        // Create interceptors with different priorities
        val interceptor1 = object : RequestInterceptor {
            override val priority: Int = 1
            
            override fun intercept(request: HttpRequestBuilder): HttpRequestBuilder {
                // Get existing value and append to it
                val existingValue = request.headers["Test-Header"] ?: ""
                val newValue = if (existingValue.isEmpty()) "Value1" else "$existingValue, Value1"
                // Set the header with the combined value
                request.headers.remove("Test-Header")
                request.headers.append("Test-Header", newValue)
                return request
            }
        }
        
        val interceptor2 = object : RequestInterceptor {
            override val priority: Int = 0
            
            override fun intercept(request: HttpRequestBuilder): HttpRequestBuilder {
                request.headers.append("Test-Header", "Value2")
                return request
            }
        }
        
        // Create a request builder
        val request = HttpRequestBuilder()
        
        // Apply interceptors in the order they would be applied by the client
        // First sort by priority (lower first)
        val interceptors = listOf(interceptor1, interceptor2).sortedBy { it.priority }
        // Then apply them in order
        interceptors.forEach { interceptor ->
            interceptor.intercept(request)
        }
        
        // Verify that the headers were added in the correct order
        // Since interceptor2 has lower priority, it should be applied first
        assertEquals("Value2, Value1", request.headers["Test-Header"])
    }
    
    @Test
    fun `test custom headers are added`() {
        // Create an interceptor that adds custom headers
        val interceptor = object : RequestInterceptor {
            override val priority: Int = 0
            
            override fun intercept(request: HttpRequestBuilder): HttpRequestBuilder {
                request.headers.append(HttpHeaders.Authorization, "Bearer token")
                return request
            }
        }
        
        // Create a request builder
        val request = HttpRequestBuilder()
        
        // Apply the interceptor
        val modifiedRequest = interceptor.intercept(request)
        
        // Verify that the header was added
        assertEquals("Bearer token", modifiedRequest.headers[HttpHeaders.Authorization])
    }
}
