package com.pingidentity.pingonemfa.util

import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountParserTest {

    private val parser = AccountParser()

    @Test
    fun `single region single user`() {
        val json = """
        {
          "regions": {
            "NA": {
              "users": [
                {
                  "id": "u1",
                  "environment": { "id": "env1" },
                  "device": { "id": "d1" },
                  "name": { "given": "John", "family": "Doe" }
                }
              ]
            }
          }
        }
        """

        val result = parser.parseAccounts(json)

        assertEquals(1, result.size)
        val account = result.first()

        assertEquals("NA", account.region)
        assertEquals("u1", account.id)
        assertEquals("env1", account.environment)
        assertEquals("d1", account.deviceId)
        assertEquals("John", account.name)
        assertEquals("Doe", account.family)
    }

    @Test
    fun `multiple regions multiple users`() {
        val json = """
        {
          "regions": {
            "NA": {
              "users": [{ "id": "u1" }]
            },
            "EU": {
              "users": [{ "id": "u2" }]
            }
          }
        }
        """

        val result = parser.parseAccounts(json)

        assertEquals(2, result.size)
        assertTrue(result.any { it.region == "NA" && it.id == "u1" })
        assertTrue(result.any { it.region == "EU" && it.id == "u2" })
    }

    @Test
    fun `missing users defaults to empty`() {
        val json = """
        {
          "regions": {
            "NA": {}
          }
        }
        """

        val result = parser.parseAccounts(json)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `missing nested objects produce empty strings`() {
        val json = """
        {
          "regions": {
            "NA": {
              "users": [{ "id": "u1" }]
            }
          }
        }
        """

        val account = parser.parseAccounts(json).first()

        assertEquals("", account.environment)
        assertEquals("", account.deviceId)
        assertEquals("", account.name)
        assertEquals("", account.family)
    }

    @Test
    fun `partial name object`() {
        val json = """
        {
          "regions": {
            "NA": {
              "users": [
                { "id": "u1", "name": { "given": "Alice" } }
              ]
            }
          }
        }
        """

        val account = parser.parseAccounts(json).first()

        assertEquals("Alice", account.name)
        assertEquals("", account.family)
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = """
        {
          "regions": {
            "NA": {
              "users": [
                {
                  "id": "u1",
                  "unknown": "value",
                  "environment": { "id": "env1", "extra": "x" }
                }
              ]
            }
          }
        }
        """

        val account = parser.parseAccounts(json).first()
        assertEquals("env1", account.environment)
    }

    @Test
    fun `large payload`() {
        val users = (1..100).joinToString(",") {
            """{ "id": "user-$it" }"""
        }

        val json = """
        {
          "regions": {
            "NA": {
              "users": [$users]
            }
          }
        }
        """

        val result = parser.parseAccounts(json)

        assertEquals(100, result.size)
        assertEquals("user-1", result.first().id)
        assertEquals("user-100", result.last().id)
    }

    @Test
    fun `AccountsResponse is serializable both ways`() {
        val json = Json { ignoreUnknownKeys = true }

        val original = AccountsResponse(
            regions = mapOf(
                "NA" to RegionDto(
                    users = listOf(
                        UserDto(
                            id = "u1",
                            environment = IdContainer("env"),
                            device = IdContainer("dev"),
                            name = NameDto("John", "Doe")
                        )
                    )
                )
            )
        )

        val encoded = json.encodeToString(AccountsResponse.serializer(), original)
        val decoded = json.decodeFromString(AccountsResponse.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `throws on invalid json`() {
        val result = runCatching {
            parser.parseAccounts("""{ "regions": "invalid" }""")
        }
        assertTrue(result.isFailure)
    }
}