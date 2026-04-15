/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.pingidentity.davinci.module.details
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.pingonemfapp.R

/**
 * Displays a DaVinci error dialog for the provided [ErrorNode].
 * It extracts detailed server-side error messages from the DaVinci response
 * and renders them in a dismissible Material dialog.
 *
 * @param node The DaVinci error node containing response details to display.
 * @param onDismissRequest Callback invoked when the dialog is dismissed.
 */
@Composable
fun Alert(node: ErrorNode, onDismissRequest: () -> Unit) {
    var showConfirmation by remember {
        mutableStateOf(true)
    }

    var error = ""
    node.details().forEach {
        it.rawResponse.let { rawResponse ->
            rawResponse.details?.forEach { detail ->
                error += ("${detail.message}\n\n")
                detail.innerError?.errors?.forEach { (key, value) ->
                    error += ("$key: $value\n\n")
                }
            }
        }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showConfirmation = false
                onDismissRequest()
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showConfirmation = false
                    onDismissRequest()
                })
                { Text(text = stringResource(id = R.string.ok)) }
            },
            text = {
                Text(text = error)
            }
        )
    }
}
