/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.journey

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.samples.pingsampleapp.R
import com.pingidentity.samples.pingsampleapp.journey.callback.JourneyContinueNode

@Composable
fun JourneyScreen(
    journeyViewModel: JourneyViewModel = viewModel<JourneyViewModel>(),
    onSuccess: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val state by journeyViewModel.state.collectAsState()
    val loading by journeyViewModel.loading.collectAsState()
    val currentOnSuccess by rememberUpdatedState(onSuccess)

    JourneyScreen(
        state = state,
        loading = loading,
        onNodeUpdated = {
            journeyViewModel.refresh()
        },
        onNext = {
            journeyViewModel.next(it)
        },
        currentOnSuccess,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyScreen(
    state: JourneyState,
    loading: Boolean,
    onNodeUpdated: () -> Unit,
    onNext: (ContinueNode) -> Unit,
    onSuccess: (() -> Unit)?,
    onBack: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text("Journey Flow") },
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
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (loading) {
                CircularProgressIndicator()
            }

            Column(
                modifier =
                Modifier
                    .padding(8.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Logo(modifier = Modifier)

                when (val node = state.node) {
                    is ContinueNode -> {
                        Render(node = node, onNodeUpdated) {
                            onNext(node)
                        }
                    }

                    is FailureNode -> {
                        Log.e("Journey", node.cause.message, node.cause)
                        Render(node = node)
                    }

                    is ErrorNode -> {
                        // TODO For Journey, we many not need to render the Failure node
                        Render(node)
                    }

                    is SuccessNode -> {
                        LaunchedEffect(true) {
                            onSuccess?.let { onSuccess() }
                        }
                    }

                    null -> {}
                }
            }
        }
    }
}

@Composable
fun Render(node: FailureNode) {
    Row(
        modifier =
        Modifier
            .padding(16.dp)
            .fillMaxWidth(),
    ) {
        Card(
            elevation =
            CardDefaults.cardElevation(
                defaultElevation = 10.dp,
            ),
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Error, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${node.cause}",
                    Modifier
                        .weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
fun Render(node: ErrorNode) {
    Row(
        modifier =
        Modifier
            .padding(16.dp)
            .fillMaxWidth(),
    ) {
        Card(
            elevation =
            CardDefaults.cardElevation(
                defaultElevation = 10.dp,
            ),
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Error, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = node.message,
                    Modifier
                        .weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
fun Render(
    node: ContinueNode,
    onNodeUpdated: () -> Unit,
    onNext: () -> Unit,
) {
    JourneyContinueNode(node, onNodeUpdated, onNext)
}

@Composable
private fun Logo(modifier: Modifier) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(modifier),
    ) {
        Spacer(modifier = Modifier.weight(1f, true))
        Icon(
            painterResource(R.drawable.ping_logo),
            contentDescription = null,
            modifier =
                Modifier
                    .height(100.dp)
                    .padding(8.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .then(Modifier),
            tint = Color.Unspecified,
        )
        Spacer(modifier = Modifier.weight(1f, true))
    }
}

@Preview
@Composable
fun PreviewJourneyScreen() {
    JourneyScreen()
}