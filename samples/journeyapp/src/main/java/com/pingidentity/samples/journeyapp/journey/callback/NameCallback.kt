/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.journeyapp.journey.callback

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pingidentity.journey.callback.NameCallback

@Composable
fun NameCallback(
    field: NameCallback,
    onNodeUpdated: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .padding(4.dp)
            .fillMaxWidth(),
    ) {
        // var text by rememberSaveable { mutableStateOf("") }

        Spacer(modifier = Modifier.weight(1f, true))

        OutlinedTextField(
            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
            value = field.name,
            onValueChange = { value ->
                // text = value
                field.name = value
                onNodeUpdated()
            },
            label = { androidx.compose.material3.Text(field.prompt) },
        )
        Spacer(modifier = Modifier.weight(1f, true))
    }
}