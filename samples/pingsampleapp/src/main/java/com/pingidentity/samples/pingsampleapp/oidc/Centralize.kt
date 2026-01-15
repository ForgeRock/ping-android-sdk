/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.oidc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Centralize(
    centralizeLoginViewModel: CentralizeLoginViewModel = viewModel<CentralizeLoginViewModel>(),
    onSuccess: (() -> Unit) = {},
    onBack: (() -> Unit)? = null,
) {
    val scroll = rememberScrollState(0)
    LaunchedEffect(true) {
        // Not relaunch when recomposition
        centralizeLoginViewModel.login()
    }

    val state by centralizeLoginViewModel.state.collectAsState()

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text("OIDC Flow") },
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(paddingValues),
        ) {
            Card(
                elevation =
                    CardDefaults.cardElevation(
                        defaultElevation = 10.dp,
                    ),
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .padding(8.dp),
                border = BorderStroke(2.dp, Color.Black),
                shape = MaterialTheme.shapes.medium,
            ) {
                state.user?.let {
                    LaunchedEffect(true) {
                        onSuccess()
                    }
                }
                Text(
                    modifier =
                        Modifier
                            .padding(4.dp)
                            .verticalScroll(scroll),
                    text =
                        state.error?.toString() ?: "",
                )
            }
        }
    }
}
