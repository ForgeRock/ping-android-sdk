/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.Collector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val CONTENT = "content"
private const val KEY = "key"
private const val RICH_CONTENT = "richContent"
private const val REPLACEMENTS = "replacements"
private const val VALUE = "value"
private const val HREF = "href"
private const val TYPE = "type"
private const val TARGET = "target"

/**
 * Represents a single replacement token inside a rich-text label.
 *
 * @property value The display text of the replacement (e.g. "google.com").
 * @property href  The URL the replacement links to (empty string if not a link).
 * @property type  The replacement type (e.g. "link").
 * @property target The link target (e.g. "_self", "_blank").
 */
data class RichContentReplacement(
    val value: String = "",
    val href: String = "",
    val type: String = "",
    val target: String = "",
)

/**
 * Class representing a LABEL type.
 *
 * This class inherits from the [Collector] class. It is used to display a label on the form.
 * @constructor Creates a new LabelCollector.
 */
class LabelCollector : Collector<Nothing> {

    var key = ""
        private set

    /** Plain-text fallback content (top-level `content` field). */
    var content = ""
        private set

    /**
     * Rich-text content string, potentially containing `{{tokenName}}` placeholders
     * that should be substituted with entries from [replacements].
     * Falls back to [content] if no `richContent` is present.
     */
    var richText = ""
        private set

    /**
     * Map of token name → [RichContentReplacement] parsed from
     * `richContent.replacements`.  Empty when there are no replacements.
     */
    var replacements: Map<String, RichContentReplacement> = emptyMap()
        private set

    override fun init(input: JsonObject): LabelCollector {
        content = input[CONTENT]?.jsonPrimitive?.contentOrNull ?: ""
        key = input[KEY]?.jsonPrimitive?.contentOrNull ?: ""

        val richContent = input[RICH_CONTENT] as? JsonObject
        richText = richContent?.get(CONTENT)?.jsonPrimitive?.contentOrNull ?: content

        replacements = (richContent?.get(REPLACEMENTS) as? JsonObject)
            ?.mapValues { (_, element) ->
                val obj = element as? JsonObject ?: return@mapValues RichContentReplacement()
                RichContentReplacement(
                    value  = (obj[VALUE] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    href   = (obj[HREF] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    type   = (obj[TYPE] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    target = (obj[TARGET] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                )
            } ?: emptyMap()

        return this
    }

    override fun id(): String = key
}