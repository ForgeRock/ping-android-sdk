/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.HREF
import com.pingidentity.davinci.REPLACEMENTS
import com.pingidentity.davinci.RICH_CONTENT
import com.pingidentity.davinci.RichContent
import com.pingidentity.davinci.RichContentReplacement
import com.pingidentity.davinci.TARGET
import com.pingidentity.davinci.TYPE
import com.pingidentity.davinci.VALUE
import com.pingidentity.davinci.plugin.Collector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val CONTENT = "content"
private const val KEY = "key"

/**
 * Class representing a LABEL type.
 *
 * This class inherits from the [Collector] class. It is used to display a label on the form.
 * @constructor Creates a new LabelCollector.
 * @see RichContent
 * @see RichContentReplacement
 */
class LabelCollector : Collector<Nothing> {

    var key = ""
        private set

    /** Plain-text fallback content (top-level `content` field). */
    var content = ""
        private set

    /**
     * Rich content, potentially containing `{{tokenName}}` placeholders
     * that should be substituted with entries from [RichContentReplacement].
     * Falls back to [content] if no `richContent` is present.
     */
    var richContent: RichContent? = null
        private set

    override fun init(input: JsonObject): LabelCollector {
        content = input[CONTENT]?.jsonPrimitive?.contentOrNull ?: ""
        key = input[KEY]?.jsonPrimitive?.contentOrNull ?: ""

        val richContentJson = input[RICH_CONTENT] as? JsonObject
        richContent = RichContent(
            richText = richContentJson?.get(CONTENT)?.jsonPrimitive?.contentOrNull ?: content,
            replacements = (richContentJson?.get(REPLACEMENTS) as? JsonObject)
                ?.mapValues { (_, element) ->
                    val obj = element as? JsonObject ?: return@mapValues RichContentReplacement()
                    RichContentReplacement(
                        value = (obj[VALUE] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                        href = (obj[HREF] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                        type = (obj[TYPE] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                        target = (obj[TARGET] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    )
                } ?: emptyMap()
        )

        return this
    }

    override fun id(): String = key
}