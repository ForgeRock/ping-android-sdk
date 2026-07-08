/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.davinci.collector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pingidentity.davinci.module.MetadataNode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Renders a [MetadataNode] in the DaVinci sample app.
 *
 * Displays the metadata type, operation, and a truncated preview of the configs payload.
 * Provides a "Resume with sample output" button that posts a sample JSON output back to
 * the DaVinci flow via [onResume].
 *
 * NOTE: This screen requires a DaVinci policy containing an SDK Integrator connector to
 * exercise this path at runtime.
 *
 * @param node     The [MetadataNode] to display.
 * @param onResume Called with the node and a sample output [JsonElement] when the user taps
 *                 the resume button.
 */
@Composable
fun MetadataNodeScreen(
    node: MetadataNode,
    onResume: (MetadataNode, JsonElement) -> Unit,
) {
    val configsPreview = node.metadata.configs.toString().let { raw ->
        if (raw.length > 100) raw.take(100) + "…" else raw
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "SDK Connector Metadata",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Type:",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(80.dp),
                )
                Text(text = node.metadata.type)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Operation:",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(80.dp),
                )
                Text(text = node.metadata.operation)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Configs:",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = configsPreview,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                modifier = Modifier.align(Alignment.End),
                onClick = {
                    val sampleOutput = buildJsonObject {
                        put("sampleField", "sampleValue")
                    }
                    onResume(node, sampleOutput)
                },
            ) {
                Text("Resume with sample output")
            }
        }
    }
}
