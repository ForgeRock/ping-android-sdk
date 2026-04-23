/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.davinci.collector

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pingidentity.davinci.collector.SingleCheckboxAppearance
import com.pingidentity.davinci.collector.BooleanCollector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleCheckbox(field: BooleanCollector, onNodeUpdated: () -> Unit) {
    var isValid by remember(field) {
        mutableStateOf(true)
    }
    var isChecked by remember(field) {
        mutableStateOf(field.value)
    }

    OutlinedCard (
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val onChange: (Boolean) -> Unit = { checked ->
                isChecked = checked
                field.value = checked
                isValid = field.validate().isEmpty()
                onNodeUpdated()
            }
            if (field.appearance ==  SingleCheckboxAppearance.SWITCH) {
                Switch(checked = isChecked, onCheckedChange = onChange)
            } else {
                Checkbox(checked = isChecked, onCheckedChange = onChange)
            }
            val text = if (field.richContent != null) {
                if (field.required) "${field.richContent}*" else field.richContent
            } else {
                if (field.required) "${field.label}*" else field.label
            }
            Text(text = text ?: "")
        }
        if (!isValid) {
            ErrorMessage(field.validate())
        }
    }
}