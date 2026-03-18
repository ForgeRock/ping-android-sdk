/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.plugin

import com.pingidentity.orchestrate.ContinueNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/**
 * Extension property for Connector class to get a list of collectors.
 *
 * @return A list of Collector instances.
 */
val ContinueNode.callbacks: List<Callback>
    get() = this.actions.filterIsInstance<Callback>()

private const val HEADER = "header"
private const val DESCRIPTION = "description"
private const val STAGE = "stage"
private const val SUBMIT_BUTTON_TEXT = "submitButtonText"
private const val PAGE_FOOTER = "pageFooter"

/**
 * Extension property to retrieve the header text for this ContinueNode.
 *
 * The header text is typically displayed at the top of the authentication screen
 * to provide context about the current step in the journey.
 *
 * @return The header text from the node's input, or an empty string if not present
 */
val ContinueNode.header: String
    get() = this.input[HEADER]?.jsonPrimitive?.content ?: ""

/**
 * Extension property to retrieve the description text for this ContinueNode.
 *
 * The description provides additional context or instructions for the current
 * authentication step, typically displayed below the header.
 *
 * @return The description text from the node's input, or an empty string if not present
 *
 */
val ContinueNode.description: String
    get() = this.input[DESCRIPTION]?.jsonPrimitive?.content ?: ""

/**
 * Extension property to retrieve the stage identifier for this ContinueNode.
 *
 * The stage field contains metadata about the current authentication step, which can include:
 * - A simple stage name (e.g., "Login", "MFA")
 * - A JSON string with localized UI text (e.g., `{"submitButtonText":{"en":"Submit"}}`)
 *
 * This property returns the raw stage value as a string. For parsed localized values,
 * use [submitButtonText] or [pageFooter] properties instead.
 *
 * @return The stage identifier from the node's input, or an empty string if not present
 *
 */
val ContinueNode.stage: String
    get() = this.input[STAGE]?.jsonPrimitive?.content ?: ""

/**
 * Extension property to retrieve the localized submit button text for this ContinueNode.
 *
 * This property intelligently derives the button text from multiple sources in order of priority:
 *
 * 1. **Localized value from stage JSON**: Extracts locale-specific text from the `stage` field
 *    ```json
 *    {"submitButtonText":{"en":"Submit","en-gb":"Submit","fr":"Soumettre"}}
 *    ```
 * The localization matching follows this priority:
 * - Exact locale match (e.g., "en_US" or "en-us")
 * - Language-only match (e.g., "en" for "en_US")
 * - First available value if no match found
 *
 * @return The localized submit button text, or an empty string if not available
 *
 * @see getLocalizedValueFromStage
 * @see pageFooter
 */
val ContinueNode.submitButtonText: String
    get() {
        // First, try to get localized value from stage JSON
        return getLocalizedValueFromStage(SUBMIT_BUTTON_TEXT) ?: ""
    }

/**
 * Extension property to retrieve the localized page footer text for this ContinueNode.
 *
 * This property extracts locale-specific footer text from the `stage` field's JSON structure.
 * The footer is typically displayed at the bottom of the authentication screen to provide
 * additional information, legal notices, or copyright text.
 *
 * The `stage` field may contain:
 * ```json
 * {"pageFooter":{"en":"© 2026 Company","en-gb":"© 2026 Company Ltd","fr":"© 2026 Société"}}
 * ```
 *
 * The localization matching follows this priority:
 * - Exact locale match (e.g., "en_GB" or "en-gb")
 * - Language-only match (e.g., "en" for "en_GB")
 * - First available value if no match found
 *
 * @return The localized footer text, or an empty string if not available
 *
 * @see getLocalizedValueFromStage
 * @see submitButtonText
 */
val ContinueNode.pageFooter: String
    get() {
        // First, try to get localized value from stage JSON
        return getLocalizedValueFromStage(PAGE_FOOTER) ?: ""
    }

/**
 * Attempts to extract a localized value from the `stage` field's JSON structure.
 *
 * The `stage` field may contain a JSON string with localized UI text:
 * ```json
 * {"submitButtonText":{"en":"Submit","en-gb":"Submit","fr":"Soumettre"},"pageFooter":{"en":"Footer"}}
 * ```
 *
 * This method parses the JSON and returns the best matching localized value based on
 * the user's device locale ([Locale.getDefault]). The matching algorithm follows this priority:
 *
 * 1. **Single value optimization**: If only one localized value exists, returns it immediately
 * 2. **Exact locale match**: Tries to match the full locale identifier (e.g., "en_gb" or "en-gb")
 * 3. **Hyphen/underscore variants**: Attempts both underscore and hyphen formats (en_US vs en-us)
 * 4. **Language-only fallback**: Matches just the language code (e.g., "en" for "en_GB")
 * 5. **First available**: Returns the first value if no locale matches
 *
 * All locale comparisons are case-insensitive.
 *
 * @param key The key to look up in the stage JSON (e.g., "submitButtonText" or "pageFooter")
 * @return The localized string value, or `null` if:
 *         - The `stage` field is empty or missing
 *         - The JSON is malformed
 *         - The specified key doesn't exist in the JSON
 *         - The key's value is not a valid localization map
 *
 * @see submitButtonText
 * @see pageFooter
 */
private fun ContinueNode.getLocalizedValueFromStage(key: String): String? {
    return try {
        val stageValue = this.input[STAGE]?.jsonPrimitive?.content
        if (stageValue.isNullOrEmpty()) return null

        val jsonObject = Json.parseToJsonElement(stageValue).jsonObject
        val localizedDict = jsonObject[key]?.jsonObject ?: return null

        // Convert JsonObject to Map<String, String>
        val localizedMap = localizedDict.entries.associate { (k, v) ->
            k to (v.jsonPrimitive.content)
        }

        // If there's only one value, return it
        if (localizedMap.size == 1) {
            return localizedMap.values.first()
        }

        // Start with the default locale
        val defaultLocale = Locale.getDefault()

        val identifier = defaultLocale.toString().lowercase()

        // Try exact match first (e.g., "en_gb")
        localizedMap[identifier]?.let { return it }

        // Try with hyphen instead of underscore (e.g., "en-gb")
        val hyphenIdentifier = identifier.replace("_", "-")
        localizedMap[hyphenIdentifier]?.let { return it }

        // Try language code only (e.g., "en" from "en_GB")
        val languageCode = defaultLocale.language.lowercase()
        if (languageCode.isNotEmpty()) {
            localizedMap[languageCode]?.let { return it }
        }

        // If no match found, return the first available value
        localizedMap.values.firstOrNull()
    } catch (e: Exception) {
        null
    }
}


