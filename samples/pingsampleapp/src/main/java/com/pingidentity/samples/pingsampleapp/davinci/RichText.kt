/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.davinci

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import com.pingidentity.davinci.RichContent
import com.pingidentity.davinci.RichContentReplacement
import com.pingidentity.davinci.collector.escapeHtml

object RichText {

    val tokenPattern = Regex("""\{\{(\w+)\}\}""")
    val htmlTagPattern = Regex("""<[a-zA-Z][^>]*>""")

    /**
     * Builds an [AnnotatedString] from a [richContent] template that may contain
     * `{{tokenName}}` placeholders. Each token found in [com.pingidentity.davinci.REPLACEMENTS] is
     * rendered as a clickable hyperlink using the token's [RichContentReplacement.href]
     * and [RichContentReplacement.value]. Unresolved tokens fall back to their
     * raw placeholder text.
     */
    fun buildRichAnnotatedString(
        richContent: RichContent,
    ) = buildAnnotatedString {
        var lastIndex = 0
        for (match in tokenPattern.findAll(richContent.content)) {
            // Append any plain text before this token
            append(richContent.content.substring(lastIndex, match.range.first))

            val token = match.groupValues[1]
            val replacement = richContent.replacements[token]

            if (replacement != null && replacement.href.isNotEmpty()) {
                pushLink(
                    LinkAnnotation.Url(
                        url = replacement.href,
                        styles = TextLinkStyles(
                            style = SpanStyle(textDecoration = TextDecoration.Underline)
                        )
                    )
                )
                append(replacement.value)
                pop()
            } else {
                // No replacement found – render the raw placeholder or display value
                append(replacement?.value?.takeIf { it.isNotEmpty() } ?: match.value)
            }

            lastIndex = match.range.last + 1
        }
        // Append any remaining plain text after the last token
        append(richContent.content.substring(lastIndex))
    }

    /**
     * Overload for building rich text from a generic [RichContent] with an optional fallback string
     * if the content is null. This can be used for any field that supports rich content, not just labels.
     *
     * 1. **Token replacement** — `richText` contains `{{token}}` placeholders →
     *    inline clickable links are substituted from [richContent].
     * 2. **HTML** — `richText` contains HTML tags →
     *    rendered via [AnnotatedString.Companion.fromHtml].
     * 3. **Plain text** — everything else → rendered as-is.
     *
     * @param richContent The [RichContent] to render, which may contain tokens and/or HTML.
     * @param fallbackContent A plain string to use if [richContent] is null.
     *
     * @return An [AnnotatedString] with tokens replaced and HTML rendered as appropriate, or the fallback content if [richContent] is null.
     */
    fun buildRichTextLabel(
        richContent: RichContent?,
        fallbackContent: String = "",
    ): AnnotatedString {
        if (richContent == null) return AnnotatedString(fallbackContent)
        val hasTokens = tokenPattern.containsMatchIn(richContent.content)
        val hasHtml = htmlTagPattern.containsMatchIn(richContent.content)
        return when {
            hasTokens && hasHtml ->
                AnnotatedString
                    .fromHtml(
                        tokenPattern.replace(richContent.content) { match ->
                            val token = match.groupValues[1]
                            val replacement = richContent.replacements[token]
                            when {
                                replacement?.href?.isNotEmpty() == true -> {
                                    val label = replacement.value.ifEmpty { match.value }.escapeHtml()
                                    val safeHref = replacement.href.escapeHtml()
                                    """<a href="$safeHref">$label</a>"""
                                }
                                replacement?.value?.isNotEmpty() == true -> replacement.value.escapeHtml()
                                else -> match.value
                            }
                        }
                    )
            hasTokens ->
                buildRichAnnotatedString(richContent)
            hasHtml ->
                AnnotatedString.fromHtml(richContent.content)
            else ->
                AnnotatedString(richContent.content)
        }
    }
}