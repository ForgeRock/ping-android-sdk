/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

const val RICH_CONTENT = "richContent"
const val REPLACEMENTS = "replacements"
const val VALUE = "value"
const val HREF = "href"
const val TYPE = "type"
const val TARGET = "target"


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
 * Represents rich text content with optional token replacements.
 *
 * @property richText The rich text content, potentially containing `{{tokenName}}` placeholders.
 * @property replacements Map of token name → [RichContentReplacement] parsed from
 * `richContent.replacements`.  Empty when there are no replacements.
 */
data class RichContent(
    val richText: String = "",
    val replacements: Map<String, RichContentReplacement> = emptyMap(),
)