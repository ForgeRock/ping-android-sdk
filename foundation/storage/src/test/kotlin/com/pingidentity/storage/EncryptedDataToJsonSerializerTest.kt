/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.storage

import androidx.datastore.core.CorruptionException
import com.pingidentity.storage.encrypt.Encryptor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class EncryptedDataToJsonSerializerTest {

    @Serializable
    data class TestData(val value: String, val number: Int = 0)

    private val mockEncryptor = mockk<Encryptor>()

    @Test
    fun `should return null for empty input stream`() = runTest {
        val serializer = EncryptedDataToJsonSerializer<TestData>(mockEncryptor)
        val emptyInputStream = ByteArrayInputStream(byteArrayOf())

        val result = serializer.readFrom(emptyInputStream)

        assertNull(result)
    }

    @Test
    fun `should encrypt and decrypt data object successfully`() = runTest {
        val testData = TestData("test", 42)
        val serializedData = """{"value":"test","number":42}""".toByteArray()
        val encryptedData = byteArrayOf(1, 2, 3, 4, 5) // Mock encrypted data

        coEvery { mockEncryptor.encrypt(serializedData) } returns encryptedData
        coEvery { mockEncryptor.decrypt(encryptedData) } returns serializedData

        val serializer = EncryptedDataToJsonSerializer<TestData>(mockEncryptor)

        // Test writing
        val outputStream = ByteArrayOutputStream()
        serializer.writeTo(testData, outputStream)

        // Test reading
        val inputStream = ByteArrayInputStream(encryptedData)
        val result = serializer.readFrom(inputStream)

        assertEquals(testData.value, result?.value)
        assertEquals(testData.number, result?.number)

        coVerify { mockEncryptor.encrypt(serializedData) }
        coVerify { mockEncryptor.decrypt(encryptedData) }
    }

    @Test
    fun `should handle null data when writing`() = runTest {
        val serializer = EncryptedDataToJsonSerializer<TestData>(mockEncryptor)
        val outputStream = ByteArrayOutputStream()

        serializer.writeTo(null, outputStream)

        assertEquals(0, outputStream.size())
    }

    @Test
    fun `should handle ByteArray data type`() = runTest {
        val testByteArray = byteArrayOf(10, 20, 30, 40)
        val encryptedData = byteArrayOf(1, 2, 3, 4, 5)

        coEvery { mockEncryptor.encrypt(testByteArray) } returns encryptedData
        coEvery { mockEncryptor.decrypt(encryptedData) } returns testByteArray

        val serializer = EncryptedDataToJsonSerializer<ByteArray>(mockEncryptor)

        // Test writing
        val outputStream = ByteArrayOutputStream()
        serializer.writeTo(testByteArray, outputStream)

        // Test reading
        val inputStream = ByteArrayInputStream(encryptedData)
        val result = serializer.readFrom(inputStream)

        assertTrue(testByteArray.contentEquals(result))

        coVerify { mockEncryptor.encrypt(testByteArray) }
        coVerify { mockEncryptor.decrypt(encryptedData) }
    }

    @Test
    fun `should return null for empty decrypted ByteArray`() = runTest {
        val encryptedData = byteArrayOf(1, 2, 3, 4, 5)
        val emptyDecryptedData = byteArrayOf()

        coEvery { mockEncryptor.decrypt(encryptedData) } returns emptyDecryptedData

        val serializer = EncryptedDataToJsonSerializer<ByteArray>(mockEncryptor)
        val inputStream = ByteArrayInputStream(encryptedData)

        val result = serializer.readFrom(inputStream)

        assertNull(result)
    }

    @Test
    fun `should return null for empty decrypted JSON data`() = runTest {
        val encryptedData = byteArrayOf(1, 2, 3, 4, 5)
        val emptyDecryptedData = byteArrayOf()

        coEvery { mockEncryptor.decrypt(encryptedData) } returns emptyDecryptedData

        val serializer = EncryptedDataToJsonSerializer<TestData>(mockEncryptor)
        val inputStream = ByteArrayInputStream(encryptedData)

        val result = serializer.readFrom(inputStream)

        assertNull(result)
    }

    @Test(expected = CorruptionException::class)
    fun `should throw CorruptionException when decryption fails`() = runTest {
        val encryptedData = byteArrayOf(1, 2, 3, 4, 5)

        coEvery { mockEncryptor.decrypt(encryptedData) } throws RuntimeException("Decryption failed")

        val serializer = EncryptedDataToJsonSerializer<TestData>(mockEncryptor)
        val inputStream = ByteArrayInputStream(encryptedData)

        serializer.readFrom(inputStream)
    }

    @Test(expected = CorruptionException::class)
    fun `should throw CorruptionException when JSON parsing fails`() = runTest {
        val encryptedData = byteArrayOf(1, 2, 3, 4, 5)
        val invalidJsonData = "invalid json".toByteArray()

        coEvery { mockEncryptor.decrypt(encryptedData) } returns invalidJsonData

        val serializer = EncryptedDataToJsonSerializer<TestData>(mockEncryptor)
        val inputStream = ByteArrayInputStream(encryptedData)

        serializer.readFrom(inputStream)
    }

    @Test
    fun `should handle complex nested data structures`() = runTest {
        @Serializable
        data class ComplexData(
            val id: String,
            val metadata: Map<String, String>,
            val items: List<String>
        )

        val complexData = ComplexData(
            "test-id",
            mapOf("key1" to "value1", "key2" to "value2"),
            listOf("item1", "item2", "item3")
        )

        val serializedData = """{"id":"test-id","metadata":{"key1":"value1","key2":"value2"},"items":["item1","item2","item3"]}""".toByteArray()
        val encryptedData = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

        coEvery { mockEncryptor.encrypt(serializedData) } returns encryptedData
        coEvery { mockEncryptor.decrypt(encryptedData) } returns serializedData

        val serializer = EncryptedDataToJsonSerializer<ComplexData>(mockEncryptor)

        // Test writing
        val outputStream = ByteArrayOutputStream()
        serializer.writeTo(complexData, outputStream)

        // Test reading
        val inputStream = ByteArrayInputStream(encryptedData)
        val result = serializer.readFrom(inputStream)

        assertEquals(complexData.id, result?.id)
        assertEquals(complexData.metadata, result?.metadata)
        assertEquals(complexData.items, result?.items)
    }

    @Test
    fun `should have null as default value`() {
        val serializer = EncryptedDataToJsonSerializer<TestData>(mockEncryptor)

        assertNull(serializer.defaultValue)
    }

    @Test
    fun `should handle multiple write operations to same stream`() = runTest {
        val testData1 = TestData("first", 1)
        val testData2 = TestData("second", 2)

        val serializedData1 = """{"value":"first","number":1}""".toByteArray()
        val serializedData2 = """{"value":"second","number":2}""".toByteArray()
        val encryptedData1 = byteArrayOf(1, 1, 1)
        val encryptedData2 = byteArrayOf(2, 2, 2)

        coEvery { mockEncryptor.encrypt(serializedData1) } returns encryptedData1
        coEvery { mockEncryptor.encrypt(serializedData2) } returns encryptedData2

        val serializer = EncryptedDataToJsonSerializer<TestData>(mockEncryptor)
        val outputStream = ByteArrayOutputStream()

        // Write first object
        serializer.writeTo(testData1, outputStream)

        // Write second object (should overwrite)
        serializer.writeTo(testData2, outputStream)

        coVerify { mockEncryptor.encrypt(serializedData1) }
        coVerify { mockEncryptor.encrypt(serializedData2) }
    }

    @Test
    fun `should handle empty string data`() = runTest {
        @Serializable
        data class StringData(val text: String)

        val emptyStringData = StringData("")
        val serializedData = """{"text":""}""".toByteArray()
        val encryptedData = byteArrayOf(1, 2, 3)

        coEvery { mockEncryptor.encrypt(serializedData) } returns encryptedData
        coEvery { mockEncryptor.decrypt(encryptedData) } returns serializedData

        val serializer = EncryptedDataToJsonSerializer<StringData>(mockEncryptor)

        // Test writing
        val outputStream = ByteArrayOutputStream()
        serializer.writeTo(emptyStringData, outputStream)

        // Test reading
        val inputStream = ByteArrayInputStream(encryptedData)
        val result = serializer.readFrom(inputStream)

        assertEquals("", result?.text)
    }

    @Test
    fun `should handle data with special characters`() = runTest {
        val specialData = TestData("test with special chars: ñáéíóú 中文 🚀", 123)
        val serializedData = """{"value":"test with special chars: ñáéíóú 中文 🚀","number":123}""".toByteArray()
        val encryptedData = byteArrayOf(10, 20, 30, 40, 50)

        coEvery { mockEncryptor.encrypt(serializedData) } returns encryptedData
        coEvery { mockEncryptor.decrypt(encryptedData) } returns serializedData

        val serializer = EncryptedDataToJsonSerializer<TestData>(mockEncryptor)

        // Test writing
        val outputStream = ByteArrayOutputStream()
        serializer.writeTo(specialData, outputStream)

        // Test reading
        val inputStream = ByteArrayInputStream(encryptedData)
        val result = serializer.readFrom(inputStream)

        assertEquals(specialData.value, result?.value)
        assertEquals(specialData.number, result?.number)
    }
}
