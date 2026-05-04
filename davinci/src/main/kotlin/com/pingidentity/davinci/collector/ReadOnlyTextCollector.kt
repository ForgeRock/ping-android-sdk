/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.Collector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val KEY = "key"
private const val TYPE = "type"
private const val CONTENT = "content"
private const val TITLE = "title"
private const val TITLE_ENABLED = "titleEnabled"
private const val ENABLED = "enabled"
private const val AGREEMENT = "agreement"
private const val ID = "id"
private const val USE_DYNAMIC_AGREEMENT = "useDynamicAgreement"

/**
 * Class representing a READ_ONLY_TEXT type collector.
 *
 * This class handles the response from a DaVinci form field of type READ_ONLY_TEXT (inputType).
 * It displays read-only text content such as agreement/terms-of-service text.
 *
 * Example JSON:
 * ```json
 * {
 *   "type": "AGREEMENT",
 *   "inputType": "READ_ONLY_TEXT",
 *   "key": "agreement",
 *   "content": "This is example agreement text...",
 *   "titleEnabled": true,
 *   "title": "Terms of Service Agreement",
 *   "agreement": {
 *     "id": "6ff30c9e-cd98-4fe5-85ca-01111ca20702",
 *     "useDynamicAgreement": false
 *   },
 *   "enabled": true
 * }
 * ```
 *
 * @constructor Creates a new ReadOnlyTextCollector.
 */
class ReadOnlyTextCollector : Collector<Nothing> {

    /**
     * The key identifier for this collector, used as the form field key during submission.
     */
    var key = ""
        private set

    /**
     * The type of this collector (e.g., "AGREEMENT").
     */
    var type = ""
        private set

    /**
     * The text content to display to the user.
     */
    var content = ""
        private set

    /**
     * The title (shown when [titleEnabled] is true).
     */
    var title = ""
        private set

    /**
     * Whether to display the [title] above the content.
     */
    var titleEnabled = false
        private set

    /**
     * Whether this collector is enabled.
     */
    var enabled = true
        private set

    /**
     * The unique ID of the agreement definition.
     */
    var agreementId = ""
        private set

    /**
     * Whether the agreement content is loaded dynamically.
     */
    var useDynamicAgreement = false
        private set

    override fun init(input: JsonObject): ReadOnlyTextCollector {
        key = input[KEY]?.jsonPrimitive?.content ?: ""
        type = input[TYPE]?.jsonPrimitive?.content ?: ""
        content = input[CONTENT]?.jsonPrimitive?.content ?: ""
        title = input[TITLE]?.jsonPrimitive?.content ?: ""
        titleEnabled = input[TITLE_ENABLED]?.jsonPrimitive?.boolean ?: false
        enabled = input[ENABLED]?.jsonPrimitive?.boolean ?: true

        input[AGREEMENT]?.jsonObject?.let { agreement ->
            agreementId = agreement[ID]?.jsonPrimitive?.content ?: ""
            useDynamicAgreement = agreement[USE_DYNAMIC_AGREEMENT]?.jsonPrimitive?.boolean ?: false
        }

        return this
    }

    override fun id(): String = key
}