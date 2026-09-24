/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.Collector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A display-only [Collector] that surfaces an image in a DaVinci authentication flow.
 * It carries no user input — [id] returns [key] and the collector never contributes
 * a value to the form submission.
 *
 * ```json
 * {
 *     "type": "IMAGE",
 *     "key": "heroImage",
 *     "description": "Alt text",
 *     "imageUrl": "https://cdn.example.com/image.png",
 *     "hyperlinkUrl": "https://example.com/install"
 * }
 * ```
 */
class ImageCollector : Collector<Nothing> {

    /** Unique field identifier within the form. */
    var key = ""
        private set

    /** Accessibility / alt text for the image. */
    var description: String = ""
        private set

    /** URL of the image to display. */
    var imageUrl: String = ""
        private set

    /** Optional URL to open when the image is tapped. `null` when not provided. */
    var hyperlinkUrl: String? = null
        private set

    /**
     * Helper function to safely extract a string from a [JsonObject] by key,
     * returning `null` if the key is absent or not a string.
     * @param field The key to look up in the [JsonObject].
     * @return The string value associated with the key, or `null` if not present or not a string.
     */
    fun JsonObject.stringOrNull(field: String): String? =
        (this[field] as? JsonPrimitive)?.contentOrNull

    override fun init(input: JsonObject): ImageCollector {
        key = input.stringOrNull("key") ?: ""
        description = input.stringOrNull("description") ?: ""
        imageUrl = input.stringOrNull("imageUrl") ?: ""
        hyperlinkUrl = input.stringOrNull("hyperlinkUrl")
        return this
    }

    override fun id(): String = key
}