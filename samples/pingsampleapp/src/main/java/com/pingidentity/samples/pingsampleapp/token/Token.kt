/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.token

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TokenScreen(
    tokenViewModel: TokenViewModel = viewModel<TokenViewModel>(),
    onBack: (() -> Unit)? = null,
) {
    val formattedToken by tokenViewModel.formattedToken.collectAsState(initial = "Loading...")
    val scroll = rememberScrollState(0)
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(true) {
        // Not relaunch when recomposition
        tokenViewModel.accessToken()
    }

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text("Token") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Card(
                    elevation =
                    CardDefaults.cardElevation(
                        defaultElevation = 10.dp,
                    ),
                    modifier =
                    Modifier
                        .height(400.dp)
                        .fillMaxWidth()
                        .padding(8.dp)
                        .combinedClickable(
                            onClick = { },
                            onLongClick = {
                                expanded = !expanded
                            }
                        ),
                    border = BorderStroke(2.dp, Color.Black),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        modifier =
                            Modifier
                                .padding(4.dp)
                                .verticalScroll(scroll),
                        text = formattedToken,
                    )
                }

                Row(
                    modifier =
                    Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.aligned(Alignment.Start),
                ) {
                    Button(
                        modifier = Modifier.padding(4.dp),
                        onClick = { tokenViewModel.accessToken() },
                    ) {
                        Text(text = "AccessToken")
                    }
                    Button(
                        modifier = Modifier.padding(4.dp),
                        onClick = { tokenViewModel.reset() },
                    ) {
                        Text(text = "Clear")
                    }
                }
                Row(
                    modifier = Modifier.padding(8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.aligned(Alignment.Start),
                ) {
                    Button(
                        modifier = Modifier.padding(4.dp),
                        onClick = { tokenViewModel.refresh() }
                    ) {
                        Text(text = "Refresh")
                    }
                    Button(
                        modifier = Modifier.padding(4.dp),
                        onClick = { tokenViewModel.revoke() }
                    ) {
                        Text(text = "Revoke")
                    }
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Refresh") },
                    onClick = {
                        tokenViewModel.refresh()
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Revoke") },
                    onClick = {
                        tokenViewModel.revoke()
                        expanded = false
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewToken() {
    TokenScreen()
}
