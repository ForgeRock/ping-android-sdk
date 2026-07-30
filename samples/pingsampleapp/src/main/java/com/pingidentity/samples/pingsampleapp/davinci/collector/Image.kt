/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.davinci.collector

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.net.toUri
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.SvgDecoder
import com.pingidentity.android.ContextProvider
import com.pingidentity.davinci.collector.ImageCollector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Image(field: ImageCollector) {
    ImageCard(
        imageUrl = field.imageUrl,
        description = field.description,
        hyperlinkUrl = field.hyperlinkUrl,
    )
}

@Composable
fun ImageCard(
    imageUrl: String,
    description: String,
    hyperlinkUrl: String?,
) {
    val context = LocalContext.current
    val outlineColor = MaterialTheme.colorScheme.outline
    val density = LocalDensity.current

    // Track component width so the placeholder icon can scale up to 48 dp max
    var componentWidthDp by remember { mutableStateOf(0.dp) }
    val placeholderSize: Dp = min(componentWidthDp, 48.dp)

    val safeHyperlinkUri = hyperlinkUrl
        ?.takeIf { it.isNotBlank() }
        ?.toUri()
        ?.takeIf { it.scheme == "http" || it.scheme == "https" }

    val borderModifier = Modifier.border(
        width = 1.dp,
        color = outlineColor,
        shape = RoundedCornerShape(4.dp),
    )

    Column(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .then(borderModifier)
            .padding(8.dp)
            .then(
                if (safeHyperlinkUri != null) {
                    Modifier.clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, safeHyperlinkUri)
                        )
                    }
                } else Modifier
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val imageLoader = ImageLoader.Builder(ContextProvider.context)
            .components { add(SvgDecoder.Factory()) }
            .build()

        var painterState by remember(imageUrl) {
            mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    componentWidthDp = with(density) { size.width.toDp() }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = description,
                imageLoader = imageLoader,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
                onState = { painterState = it },
            )

            // Show placeholder icon while loading or if the image fails/is empty
            if (painterState !is AsyncImagePainter.State.Success) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = description.ifBlank { "Image placeholder" },
                    modifier = Modifier.size(placeholderSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        safeHyperlinkUri?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = safeHyperlinkUri.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
