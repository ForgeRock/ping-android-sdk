/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.ActionKeyProvider
import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.davinci.plugin.Collectors
import com.pingidentity.davinci.plugin.Submittable
import com.pingidentity.orchestrate.FlowContext
import com.pingidentity.orchestrate.RequestInterceptor
import io.mockk.mockk
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.pingidentity.network.HttpRequest as Request

class CollectorsTest {

    // Test implementation of Submittable collector
    private class TestSubmittableCollector(
        private val collectorId: String,
        private val collectorPayload: JsonObject?,
        private val collectorEventType: String = "submit"
    ) : Collector<JsonObject>, Submittable {
        override fun id(): String = collectorId
        override fun eventType(): String = collectorEventType
        override fun payload(): JsonObject? = collectorPayload
        override fun init(input: JsonObject): Collector<JsonObject> = this
    }

    // Test implementation of Submittable and ActionKeyProvider collector
    private class TestActionKeyCollector(
        private val collectorId: String,
        private val collectorPayload: JsonObject?,
        override val actionKey: String?,
        private val collectorEventType: String = "action"
    ) : Collector<JsonObject>, Submittable, ActionKeyProvider {
        override fun id(): String = collectorId
        override fun eventType(): String = collectorEventType
        override fun payload(): JsonObject? = collectorPayload
        override fun init(input: JsonObject): Collector<JsonObject> = this
    }

    // Test implementation of RequestInterceptor collector
    private class TestRequestInterceptorCollector(
        private val collectorId: String,
        private val interceptedRequest: Request
    ) : Collector<JsonObject>, RequestInterceptor {
        override fun id(): String = collectorId
        override fun payload(): JsonObject? = null
        override fun init(input: JsonObject): Collector<JsonObject> = this
        override var intercept: FlowContext.(Request) -> Request = { interceptedRequest }
    }

    // Test implementation of regular collector
    private class TestRegularCollector(
        private val collectorId: String,
        private val collectorPayload: JsonObject?
    ) : Collector<JsonObject> {
        override fun id(): String = collectorId
        override fun payload(): JsonObject? = collectorPayload
        override fun init(input: JsonObject): Collector<JsonObject> = this
    }

    @Test
    fun `eventType should return null for empty collectors list`() {
        val collectors: Collectors = emptyList()
        assertNull(collectors.eventType())
    }

    @Test
    fun `eventType should return null when no submittable collectors`() {
        val collectors: Collectors = listOf(
            TestRegularCollector("collector1", buildJsonObject { put("key", JsonPrimitive("value")) })
        )
        assertNull(collectors.eventType())
    }

    @Test
    fun `eventType should return null when submittable has no payload`() {
        val collectors: Collectors = listOf(
            TestSubmittableCollector("submit1", null, "submit")
        )
        assertNull(collectors.eventType())
    }

    @Test
    fun `eventType should return submit when submittable has payload`() {
        val payload = buildJsonObject { put("key", JsonPrimitive("value")) }
        val collectors: Collectors = listOf(
            TestSubmittableCollector("submit1", payload, "submit")
        )
        assertEquals("submit", collectors.eventType())
    }

    @Test
    fun `eventType should return action when ActionKeyProvider has non-null actionKey`() {
        val collectors: Collectors = listOf(
            TestActionKeyCollector("provider1", buildJsonObject { }, "NotAllowedError", "action")
        )
        assertEquals("action", collectors.eventType())
    }

    @Test
    fun `eventType should return action when ActionKeyProvider actionKey is null but payload is non-null`() {
        // Second pass: any Submittable with a non-null payload returns its eventType (e.g. FIDO error fallback).
        val payload = buildJsonObject { put("key", JsonPrimitive("value")) }
        val collectors: Collectors = listOf(
            TestActionKeyCollector("provider1", payload, null, "action")
        )
        assertEquals("action", collectors.eventType())
    }

    @Test
    fun `eventType should return null when ActionKeyProvider has no payload and no actionKey`() {
        val collectors: Collectors = listOf(
            TestActionKeyCollector("provider1", null, null, "action")
        )
        assertNull(collectors.eventType())
    }

    @Test
    fun `eventType should return null when ActionKeyProvider actionKey is set but payload is null`() {
        // Neither pass fires: first pass requires SubmitCollector/FlowCollector, second pass requires non-null payload.
        val collectors: Collectors = listOf(
            TestActionKeyCollector("provider1", null, "NotAllowedError", "action")
        )
        assertNull(collectors.eventType())
    }

    @Test
    fun `eventType should return first matching event type`() {
        val payload1 = buildJsonObject { put("key1", JsonPrimitive("value1")) }
        val payload2 = buildJsonObject { put("key2", JsonPrimitive("value2")) }
        val collectors: Collectors = listOf(
            TestSubmittableCollector("submit1", payload1, "submit"),
            TestSubmittableCollector("submit2", payload2, "continue")
        )
        assertEquals("submit", collectors.eventType())
    }

    @Test
    fun `eventType should skip non-submittable collectors`() {
        val payload = buildJsonObject { put("key", JsonPrimitive("value")) }
        val collectors: Collectors = listOf(
            TestRegularCollector("regular1", buildJsonObject { put("key", JsonPrimitive("value")) }),
            TestSubmittableCollector("submit1", payload, "submit")
        )
        assertEquals("submit", collectors.eventType())
    }

    @Test
    fun `request should return original request when no interceptors`() {
        val context = mockk<FlowContext>()
        val originalRequest = mockk<Request>()
        val collectors: Collectors = listOf(
            TestRegularCollector("collector1", null)
        )

        val result = collectors.request(context, originalRequest)

        assertEquals(originalRequest, result)
    }

    @Test
    fun `request should apply single interceptor`() {
        val context = mockk<FlowContext>()
        val originalRequest = mockk<Request>()
        val interceptedRequest = mockk<Request>()
        val collectors: Collectors = listOf(
            TestRequestInterceptorCollector("interceptor1", interceptedRequest)
        )

        val result = collectors.request(context, originalRequest)

        assertEquals(interceptedRequest, result)
    }

    @Test
    fun `request should apply multiple interceptors in order`() {
        val context = mockk<FlowContext>()
        val originalRequest = mockk<Request>()
        val interceptedRequest1 = mockk<Request>()
        val interceptedRequest2 = mockk<Request>()

        val interceptor1 = TestRequestInterceptorCollector("interceptor1", interceptedRequest1)
        val interceptor2 = TestRequestInterceptorCollector("interceptor2", interceptedRequest2)

        val collectors: Collectors = listOf(interceptor1, interceptor2)

        val result = collectors.request(context, originalRequest)

        // The second interceptor should receive the result from the first
        assertEquals(interceptedRequest2, result)
    }

    @Test
    fun `asJson should return empty formData for empty collectors`() {
        val collectors: Collectors = emptyList()

        val result = collectors.asJson()

        val formData = result["formData"]?.jsonObject
        assertEquals(0, formData?.size)
    }

    @Test
    fun `asJson should set actionKey for SubmitCollector with payload`() {
        val payload = JsonPrimitive("test")
        val init = buildJsonObject { put("key", JsonPrimitive("submitKey")) }
        val submitCollector = SubmitCollector()
        submitCollector.init(init)
        submitCollector.init(payload)

        val collectors: Collectors = listOf(submitCollector)

        val result = collectors.asJson()

        assertEquals("submitKey", result["actionKey"]?.jsonPrimitive?.content)
    }

    @Test
    fun `asJson should set actionKey for FlowCollector with payload`() {
        val flowCollector = FlowCollector()
        flowCollector.init(buildJsonObject { put("key", JsonPrimitive("flowKey")) })
        flowCollector.init(JsonPrimitive("true"))

        val collectors: Collectors = listOf(flowCollector)


        val result = collectors.asJson()

        assertEquals("flowKey", result["actionKey"]?.jsonPrimitive?.content)
    }

    @Test
    fun `asJson should set actionKey for ActionKeyProvider with non-null actionKey and empty payload sentinel`() {
        // empty payload is the error sentinel; actionKey takes priority over formData
        val collectors: Collectors = listOf(
            TestActionKeyCollector("provider1", buildJsonObject { }, "NotAllowedError", "action")
        )

        val result = collectors.asJson()

        assertEquals("NotAllowedError", result["actionKey"]?.jsonPrimitive?.content)
        // error payload must not spill into formData
        assertEquals(0, result["formData"]?.jsonObject?.size)
    }

    @Test
    fun `asJson should add regular collector payload to formData`() {
        val payload = buildJsonObject { put("username", JsonPrimitive("john.doe")) }
        val collectors: Collectors = listOf(
            TestRegularCollector("usernameField", payload)
        )

        val result = collectors.asJson()

        val formData = result["formData"]?.jsonObject
        val usernameField = formData?.get("usernameField")?.jsonObject
        assertEquals("john.doe", usernameField?.get("username")?.jsonPrimitive?.content)
    }

    @Test
    fun `asJson should add ActionKeyProvider payload to formData when actionKey is null`() {
        val payload = buildJsonObject { put("data", JsonPrimitive("test")) }
        val collectors: Collectors = listOf(
            TestActionKeyCollector("provider1", payload, null, "submit")
        )

        val result = collectors.asJson()

        val formData = result["formData"]?.jsonObject
        val providerField = formData?.get("provider1")?.jsonObject
        assertEquals("test", providerField?.get("data")?.jsonPrimitive?.content)
    }

    @Test
    fun `asJson should not add regular collector without payload to formData`() {
        val collectors: Collectors = listOf(
            TestRegularCollector("emptyField", null)
        )

        val result = collectors.asJson()

        val formData = result["formData"]?.jsonObject
        assertEquals(0, formData?.size)
    }

    @Test
    fun `asJson should handle multiple collectors`() {
        val payload1 = buildJsonObject { put("field1", JsonPrimitive("value1")) }
        val payload2 = buildJsonObject { put("field2", JsonPrimitive("value2")) }

        val submitCollector = SubmitCollector()
        submitCollector.init(buildJsonObject { put("key", JsonPrimitive("submitButton")) })
        submitCollector.init(JsonPrimitive("true"))

        val collectors: Collectors = listOf(
            TestRegularCollector("collector1", payload1),
            TestRegularCollector("collector2", payload2),
            submitCollector
        )

        val result = collectors.asJson()

        assertEquals("submitButton", result["actionKey"]?.jsonPrimitive?.content)

        val formData = result["formData"]?.jsonObject
        assertEquals("value1", formData?.get("collector1")?.jsonObject?.get("field1")?.jsonPrimitive?.content)
        assertEquals("value2", formData?.get("collector2")?.jsonObject?.get("field2")?.jsonPrimitive?.content)
    }

    @Test
    fun `asJson should handle nested maps in payload`() {
        val payload = buildJsonObject {
            put("nested", buildJsonObject {
                put("key", JsonPrimitive("value"))
            })
        }
        val collectors: Collectors = listOf(
            TestRegularCollector("nestedField", payload)
        )

        val result = collectors.asJson()

        val formData = result["formData"]?.jsonObject
        val nestedField = formData?.get("nestedField")?.jsonObject
        val nested = nestedField?.get("nested")?.jsonObject
        assertEquals("value", nested?.get("key")?.jsonPrimitive?.content)
    }

    @Test
    fun `asJson should handle arrays in payload`() {
        val payload = buildJsonObject {
            put("items", JsonArray(listOf(
                JsonPrimitive("item1"),
                JsonPrimitive("item2"),
                JsonPrimitive("item3")
            )))
        }
        val collectors: Collectors = listOf(
            TestRegularCollector("arrayField", payload)
        )

        val result = collectors.asJson()

        val formData = result["formData"]?.jsonObject
        val arrayField = formData?.get("arrayField")?.jsonObject
        val items = arrayField?.get("items")?.jsonArray
        assertEquals(3, items?.size)
        assertEquals("item1", items?.get(0)?.jsonPrimitive?.content)
    }

    @Test
    fun `asJson should handle boolean values in payload`() {
        val payload = buildJsonObject {
            put("enabled", JsonPrimitive(true))
            put("disabled", JsonPrimitive(false))
        }
        val collectors: Collectors = listOf(
            TestRegularCollector("booleanField", payload)
        )

        val result = collectors.asJson()

        val formData = result["formData"]?.jsonObject
        val booleanField = formData?.get("booleanField")?.jsonObject
        assertEquals(true, booleanField?.get("enabled")?.jsonPrimitive?.content?.toBoolean())
        assertEquals(false, booleanField?.get("disabled")?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun `asJson should set actionKey and include payload in formData for MetadataCollector with result`() {
        val resultPayload = buildJsonObject { put("score", JsonPrimitive(42)) }
        val metadataCollector = MetadataCollector()
        metadataCollector.init(buildJsonObject {
            put("key", JsonPrimitive("deviceProfile"))
            put("payload", buildJsonObject { put("challenge", JsonPrimitive("abc123")) })
        })
        metadataCollector.setResult(resultPayload)

        val collectors: Collectors = listOf(metadataCollector)
        val result = collectors.asJson()

        assertEquals("deviceProfile", result["actionKey"]?.jsonPrimitive?.content)
        val formData = result["formData"]?.jsonObject
        assertEquals(resultPayload, formData?.get("deviceProfile")?.jsonObject)
    }

    @Test
    fun `asJson should not set actionKey for MetadataCollector without result`() {
        val metadataCollector = MetadataCollector()
        metadataCollector.init(buildJsonObject {
            put("key", JsonPrimitive("deviceProfile"))
            put("payload", buildJsonObject { put("challenge", JsonPrimitive("abc123")) })
        })

        val collectors: Collectors = listOf(metadataCollector)
        val result = collectors.asJson()

        assertNull(result["actionKey"])
        assertEquals(0, result["formData"]?.jsonObject?.size)
    }

    @Test
    fun `asJson should not include empty error sentinel payload in formData`() {
        // FIDO error path: actionKey set, payload is empty sentinel — must not pollute formData
        val collectors: Collectors = listOf(
            TestActionKeyCollector("fido2", buildJsonObject { }, "NotAllowedError", "action")
        )
        val result = collectors.asJson()

        assertEquals("NotAllowedError", result["actionKey"]?.jsonPrimitive?.content)
        assertEquals(0, result["formData"]?.jsonObject?.size)
    }

    @Test
    fun `asJson should handle number values in payload`() {
        val payload = buildJsonObject {
            put("count", JsonPrimitive(42))
            put("price", JsonPrimitive(19.99))
        }
        val collectors: Collectors = listOf(
            TestRegularCollector("numberField", payload)
        )

        val result = collectors.asJson()

        val formData = result["formData"]?.jsonObject
        val numberField = formData?.get("numberField")?.jsonObject
        assertEquals("42", numberField?.get("count")?.jsonPrimitive?.content)
        assertEquals("19.99", numberField?.get("price")?.jsonPrimitive?.content)
    }
}



