/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.app.davinci.collector

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pingidentity.davinci.RichContent
import com.pingidentity.davinci.RichContentReplacement
import com.pingidentity.davinci.collector.LabelCollector
import com.pingidentity.davinci.collector.escapeHtml

private val tokenPattern = Regex("""\{\{(\w+)\}\}""")
private val htmlTagPattern = Regex("""<[a-zA-Z][^>]*>""")

@Composable
fun Label(field: LabelCollector) {
    Row(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth(),
    ) {
        androidx.compose.material3.Text(
            text = buildLabelText(field),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .wrapContentWidth(Alignment.CenterHorizontally)
                .weight(1f)
        )
    }
}

/**
 * Selects the appropriate rendering strategy for a [LabelCollector]:
 *
 * 1. **Token replacement** — `richText` contains `{{token}}` placeholders →
 *    inline clickable links are substituted from [LabelCollector.richContent].
 * 2. **HTML** — `richText` contains HTML tags →
 *    rendered via [AnnotatedString.Companion.fromHtml].
 * 3. **Plain text** — everything else → rendered as-is.
 */
private fun buildLabelText(field: LabelCollector): AnnotatedString {
    val richContent = field.richContent ?: return AnnotatedString(field.content)
    val hasTokens = tokenPattern.containsMatchIn(richContent.richText)
    val hasHtml = htmlTagPattern.containsMatchIn(richContent.richText)
    return when {
        hasTokens && hasHtml ->
            AnnotatedString
                .fromHtml(
                    tokenPattern.replace(richContent.richText) { match ->
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
            AnnotatedString.fromHtml(richContent.richText)
        else ->
            AnnotatedString(richContent.richText)
    }
}

/**
 * Builds an [AnnotatedString] from a [richContent] template that may contain
 * `{{tokenName}}` placeholders. Each token found in [com.pingidentity.davinci.REPLACEMENTS] is
 * rendered as a clickable hyperlink using the token's [RichContentReplacement.href]
 * and [RichContentReplacement.value]. Unresolved tokens fall back to their
 * raw placeholder text.
 */
private fun buildRichAnnotatedString(
    richContent: RichContent,
) = buildAnnotatedString {
    var lastIndex = 0
    for (match in tokenPattern.findAll(richContent.richText)) {
        // Append any plain text before this token
        append(richContent.richText.substring(lastIndex, match.range.first))

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
    append(richContent.richText.substring(lastIndex))
}
