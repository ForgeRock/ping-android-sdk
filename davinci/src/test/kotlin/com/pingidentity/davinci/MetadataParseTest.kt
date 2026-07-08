/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.module.Metadata
import com.pingidentity.davinci.module.MetadataException
import com.pingidentity.davinci.module.parseOrNull
import com.pingidentity.davinci.module.parseOrThrow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pure-JVM unit tests for [Metadata.Companion.parseOrNull] and [Metadata.Companion.parseOrThrow].
 *
 * No Ktor / MockEngine needed — this exercises only the JSON parsing layer.
 */
class MetadataParseTest {

    // -------------------------------------------------------------------------------------
    // Shared fixtures
    // -------------------------------------------------------------------------------------

    /** A well-formed metadata JSON object as the server would send it. */
    private val validJson = buildJsonObject {
        put("TYPE", Metadata.Type.PINGONE_MFA_SDK)
        put("OPERATION", "CHECK_FOR_MFA")
        put("configs", buildJsonObject {
            put("sdkId", "ping-mfa-sdk")
            put("version", "2.0")
        })
    }

    // -------------------------------------------------------------------------------------
    // parseOrNull — happy path
    // -------------------------------------------------------------------------------------

    @Test
    fun `parseOrNull returns Metadata for a valid JSON object`() {
        val result = Metadata.parseOrNull(validJson)

        assertNotNull(result)
        assertEquals(Metadata.Type.PINGONE_MFA_SDK, result!!.type)
        assertEquals("CHECK_FOR_MFA", result.operation)
        assertEquals(2, result.configs.size)
    }

    @Test
    fun `parseOrNull preserves all TYPE constant values correctly`() {
        for (type in listOf(
            Metadata.Type.METADATA,
            Metadata.Type.EXTERNAL_SDK,
            Metadata.Type.PINGONE_MFA_SDK,
            Metadata.Type.KEYLESS_SDK,
        )) {
            val json = buildJsonObject {
                put("TYPE", type)
                put("OPERATION", "OP")
                put("configs", buildJsonObject {})
            }
            val result = Metadata.parseOrNull(json)
            assertNotNull("parseOrNull should succeed for TYPE=$type", result)
            assertEquals(type, result!!.type)
        }
    }

    @Test
    fun `parseOrNull accepts empty configs object`() {
        val json = buildJsonObject {
            put("TYPE", Metadata.Type.EXTERNAL_SDK)
            put("OPERATION", "INIT")
            put("configs", buildJsonObject {})
        }
        val result = Metadata.parseOrNull(json)
        assertNotNull(result)
        assertTrue(result!!.configs.isEmpty())
    }

    // -------------------------------------------------------------------------------------
    // parseOrNull — null when required key is missing
    // -------------------------------------------------------------------------------------

    @Test
    fun `parseOrNull returns null when TYPE is missing`() {
        val json = buildJsonObject {
            put("OPERATION", "CHECK_FOR_MFA")
            put("configs", buildJsonObject { put("k", "v") })
        }
        assertNull(Metadata.parseOrNull(json))
    }

    @Test
    fun `parseOrNull returns null when OPERATION is missing`() {
        val json = buildJsonObject {
            put("TYPE", Metadata.Type.PINGONE_MFA_SDK)
            put("configs", buildJsonObject { put("k", "v") })
        }
        assertNull(Metadata.parseOrNull(json))
    }

    @Test
    fun `parseOrNull returns null when configs is missing`() {
        val json = buildJsonObject {
            put("TYPE", Metadata.Type.PINGONE_MFA_SDK)
            put("OPERATION", "CHECK_FOR_MFA")
        }
        assertNull(Metadata.parseOrNull(json))
    }

    @Test
    fun `parseOrNull returns null when TYPE is not a primitive`() {
        val json = buildJsonObject {
            put("TYPE", buildJsonObject { put("nested", "bad") })
            put("OPERATION", "CHECK_FOR_MFA")
            put("configs", buildJsonObject {})
        }
        assertNull(Metadata.parseOrNull(json))
    }

    @Test
    fun `parseOrNull returns null when OPERATION is not a primitive`() {
        val json = buildJsonObject {
            put("TYPE", Metadata.Type.EXTERNAL_SDK)
            put("OPERATION", buildJsonObject { put("nested", "bad") })
            put("configs", buildJsonObject {})
        }
        assertNull(Metadata.parseOrNull(json))
    }

    @Test
    fun `parseOrNull returns null when configs is not a JSON object`() {
        val json = buildJsonObject {
            put("TYPE", Metadata.Type.PINGONE_MFA_SDK)
            put("OPERATION", "CHECK_FOR_MFA")
            // configs is a primitive, not an object
            put("configs", JsonPrimitive("not-an-object"))
        }
        assertNull(Metadata.parseOrNull(json))
    }

    // -------------------------------------------------------------------------------------
    // parseOrThrow — happy path
    // -------------------------------------------------------------------------------------

    @Test
    fun `parseOrThrow returns Metadata for a valid JSON object`() {
        val result = Metadata.parseOrThrow(validJson)

        assertEquals(Metadata.Type.PINGONE_MFA_SDK, result.type)
        assertEquals("CHECK_FOR_MFA", result.operation)
        assertEquals(2, result.configs.size)
    }

    // -------------------------------------------------------------------------------------
    // parseOrThrow — Malformed thrown with key-naming message, no payload data
    // -------------------------------------------------------------------------------------

    @Test
    fun `parseOrThrow throws Malformed when TYPE is missing and message names TYPE`() {
        val json = buildJsonObject {
            put("OPERATION", "CHECK_FOR_MFA")
            put("configs", buildJsonObject {})
        }
        try {
            Metadata.parseOrThrow(json)
            fail("Expected MetadataException.Malformed")
        } catch (e: MetadataException.Malformed) {
            assertTrue(
                "Exception message should name the key 'TYPE'",
                e.message!!.contains("TYPE"),
            )
        }
    }

    @Test
    fun `parseOrThrow throws Malformed when OPERATION is missing and message names OPERATION`() {
        val json = buildJsonObject {
            put("TYPE", Metadata.Type.KEYLESS_SDK)
            put("configs", buildJsonObject {})
        }
        try {
            Metadata.parseOrThrow(json)
            fail("Expected MetadataException.Malformed")
        } catch (e: MetadataException.Malformed) {
            assertTrue(
                "Exception message should name the key 'OPERATION'",
                e.message!!.contains("OPERATION"),
            )
        }
    }

    @Test
    fun `parseOrThrow throws Malformed when configs is missing and message names configs`() {
        val json = buildJsonObject {
            put("TYPE", Metadata.Type.PINGONE_MFA_SDK)
            put("OPERATION", "ENROLL")
        }
        try {
            Metadata.parseOrThrow(json)
            fail("Expected MetadataException.Malformed")
        } catch (e: MetadataException.Malformed) {
            assertTrue(
                "Exception message should name the key 'configs'",
                e.message!!.contains("configs"),
            )
        }
    }

    @Test
    fun `parseOrThrow Malformed message does not contain any configs value`() {
        // The configs object contains a sentinel value; the exception message must not echo it.
        val secretMarker = "SENTINEL_SECRET_XYZ_DO_NOT_ECHO"
        val json = buildJsonObject {
            put("TYPE", Metadata.Type.PINGONE_MFA_SDK)
            // OPERATION is intentionally absent to trigger Malformed
            put("configs", buildJsonObject {
                put("sensitiveKey", secretMarker)
            })
        }
        try {
            Metadata.parseOrThrow(json)
            fail("Expected MetadataException.Malformed")
        } catch (e: MetadataException.Malformed) {
            val msg = e.message ?: ""
            assertTrue(
                "Exception message must not contain configs values",
                !msg.contains(secretMarker),
            )
        }
    }

    @Test
    fun `parseOrThrow Malformed message does not contain TYPE value`() {
        // Even the TYPE value itself (which could be arbitrary) must not be echoed in malformed
        // messages for missing keys.
        val json = buildJsonObject {
            put("TYPE", Metadata.Type.PINGONE_MFA_SDK)
            // configs is a primitive — wrong type — triggers Malformed for wrong type
            put("OPERATION", "OP")
            put("configs", JsonPrimitive("SHOULD_NOT_APPEAR_IN_EXCEPTION"))
        }
        try {
            Metadata.parseOrThrow(json)
            fail("Expected MetadataException.Malformed")
        } catch (e: MetadataException.Malformed) {
            val msg = e.message ?: ""
            assertTrue(
                "Exception message must not contain the wrong-type value",
                !msg.contains("SHOULD_NOT_APPEAR_IN_EXCEPTION"),
            )
        }
    }

    // -------------------------------------------------------------------------------------
    // MetadataException subclass identity
    // -------------------------------------------------------------------------------------

    @Test
    fun `MissingResumeLink has the expected fixed message`() {
        val ex = MetadataException.MissingResumeLink()
        assertEquals("Metadata node has no _links.next.href", ex.message)
    }

    @Test
    fun `UnsupportedType exposes rawType and formats message`() {
        val raw = "UNKNOWN_FUTURE_TYPE"
        val ex = MetadataException.UnsupportedType(raw)
        assertEquals(raw, ex.rawType)
        assertTrue(ex.message!!.contains(raw))
    }

    @Test
    fun `MetadataException subclasses are distinct types`() {
        val exceptions: List<MetadataException> = listOf(
            MetadataException.Malformed("bad key"),
            MetadataException.MissingResumeLink(),
            MetadataException.UnsupportedType("FOO"),
        )

        assertTrue(exceptions[0] is MetadataException.Malformed)
        assertTrue(exceptions[1] is MetadataException.MissingResumeLink)
        assertTrue(exceptions[2] is MetadataException.UnsupportedType)

        // Cross-type checks: each instance must not match the other two subtypes.
        for (i in exceptions.indices) {
            for (j in exceptions.indices) {
                if (i == j) continue
                assertFalse(
                    "exceptions[$i] should not be the same type as exceptions[$j]",
                    exceptions[i]::class == exceptions[j]::class,
                )
            }
        }
    }
}
