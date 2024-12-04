/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

import com.pingidentity.davinci.collector.RegexError
import com.pingidentity.davinci.collector.Required
import com.pingidentity.davinci.collector.ValidatedCollector
import com.pingidentity.davinci.collector.ValidationError
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class ValidatedCollectorTest {

    @Test
    fun validatesSuccessfullyWhenNoErrors() {
        val input = buildJsonObject {
            put("validation", buildJsonObject {
                put("regex", ".*")
                put("errorMessage", "Invalid format")
            })
        }
        val collector = object : ValidatedCollector() {}
        collector.init(input)
        collector.value = "validValue"
        assertEquals(emptyList(), collector.validate())
    }

    @Test
    fun addsRequiredErrorWhenValueIsEmpty() {
        val input = buildJsonObject {
            put("required", true)
            put("validation", buildJsonObject {
                put("regex", ".*")
                put("errorMessage", "Invalid format")
            })
        }
        val collector = object : ValidatedCollector() {}
        collector.init(input)
        collector.value = ""
        assertEquals(listOf(Required), collector.validate())
    }

    @Test
    fun addsRegexErrorWhenValueDoesNotMatch() {
        val input = buildJsonObject {
            put("validation", buildJsonObject {
                put("regex", "^\\d+$")
                put("errorMessage", "Must be digits")
            })
        }
        val collector = object : ValidatedCollector() {}
        collector.init(input)
        collector.value = "invalidValue"
        assertEquals(listOf(RegexError("Must be digits")), collector.validate())
    }

    @Test
    fun addsBothErrorsWhenValueIsEmptyAndDoesNotMatchRegex() {
        val input = buildJsonObject {
            put("required", true)
            put("validation", buildJsonObject {
                put("regex", "^\\d+$")
                put("errorMessage", "Must be digits")
            })
        }
        val collector = object : ValidatedCollector() {}
        collector.init(input)
        collector.value = ""
        assertEquals(
            listOf(Required, RegexError("Must be digits")),
            collector.validate()
        )
    }
}