/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.davinci

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingidentity.davinci.module.continueNode
import com.pingidentity.logger.Logger
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ErrorNode
import com.pingidentity.orchestrate.FailureNode
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.pingonemfapp.R
import com.pingidentity.pingonemfapp.ui.components.Alert
import com.pingidentity.pingonemfapp.ui.components.BackNavigationTopAppBar

/**
 * High-level DaVinci screen that connects the UI to [DaVinciViewModel].
 * It observes flow state, loading state, and forwards user actions back to the ViewModel.
 *
 * @param daVinciViewModel The ViewModel that owns the active DaVinci flow state.
 * @param onSuccess Optional callback invoked when the DaVinci flow reaches [SuccessNode].
 */
@Composable
fun DaVinci(
    logger: Logger,
    daVinciViewModel: DaVinciViewModel = viewModel<DaVinciViewModel>(),
    onSuccess: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onFinish: (() -> Unit)? = null,
) {
    BackHandler(enabled = onBack != null) {
        onBack?.invoke()
    }

    val state by daVinciViewModel.state.collectAsState()
    val loading by daVinciViewModel.loading.collectAsState()
    val currentOnSuccess by rememberUpdatedState(onSuccess)

    DaVinci(
        logger = logger,
        state = state,
        loading = loading,
        onNodeUpdated = {
            daVinciViewModel.refresh()
        },
        onNext = {
            daVinciViewModel.next(it)
        },
        onStart = {
            daVinciViewModel.start()
        },
        currentOnSuccess,
        onBack,
        onFinish,
    )
}

/**
 * Stateless DaVinci screen renderer.
 * It displays the current node state, loading indicator, and dispatches UI actions
 * through the provided callbacks.
 */
@Composable
fun DaVinci(
    logger: Logger,
    state: DaVinciState,
    loading: Boolean,
    onNodeUpdated: () -> Unit,
    onNext: (ContinueNode) -> Unit,
    onStart: () -> Unit,
    onSuccess: (() -> Unit)?,
    onBack: (() -> Unit)? = null,
    onFinish: (() -> Unit)? = null,
) {
    val scroll = rememberScrollState(0)

    Scaffold(
        topBar = {
            if (onBack != null) {
                BackNavigationTopAppBar(
                    title = stringResource(id = R.string.davinci_flow_title),
                    onBackClick = onBack,
                )
            }
        }
        ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(paddingValues)
        ) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .verticalScroll(scroll)
                        .padding(8.dp)
                        .fillMaxSize()
            ) {
                Logo(modifier = Modifier)

                when (val node = state.node) {
                    is ContinueNode -> {
                        Render(
                            node = node,
                            onNodeUpdated = onNodeUpdated,
                            onStart = onStart,
                            onNext = {
                                onNext(node)
                            },
                            onFinish = {
                                onFinish?.invoke()
                            },
                        )
                    }

                    is FailureNode -> {
                        logger.i("DaVinci flow reached FailureNode")

                        logger.e("DaVinci: $node.cause.message", node.cause)
                        Render(node = node)
                    }

                    is ErrorNode -> {
                        logger.i("DaVinci flow reached ErrorNode with message: ${node.message}")

                        Render(node)
                        // Render the previous node
                        node.continueNode()?.let {
                            Render(
                                node = it,
                                onNodeUpdated = onNodeUpdated,
                                onStart = onStart,
                                onNext = {
                                    onNext(it)
                                },
                                onFinish = {
                                    onFinish?.invoke()
                                },
                            )
                        }
                    }

                    is SuccessNode -> {
                        logger.i("DaVinci flow reached SuccessNode")
                        LaunchedEffect(true) {
                            onSuccess?.let { onSuccess() }
                        }
                    }

                    else -> {
                        logger.i("DaVinci flow started")
                    }
                }
            }

            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/**
 * Renders an unexpected DaVinci failure as an error card.
 */
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

/**
 * Renders a DaVinci error response and allows opening detailed server error information.
 */
@Composable
fun Render(node: ErrorNode) {
    var showAlert by remember { mutableStateOf(false) }

    if (showAlert) {
        Alert(node) {
            showAlert = false
        }
    }

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
                    .padding(8.dp)
                    .clickable {
                        showAlert = true
                    },
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

/**
 * Renders the current DaVinci [ContinueNode] using the dedicated collector-based UI.
 */
@Composable
fun Render(
    node: ContinueNode,
    onNodeUpdated: () -> Unit,
    onStart: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    PingOneMFAContinueNode(node, onNodeUpdated, onStart, onNext, onFinish)
}

/**
 * Displays the DaVinci logo at the top of the DaVinci screen.
 */
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
            painterResource(R.drawable.logo_davinci_white),
            contentDescription = null,
            modifier =
                Modifier
                    .height(100.dp)
                    .padding(8.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .then(modifier),
            tint = Color.Unspecified,
        )
        Spacer(modifier = Modifier.weight(1f, true))
    }
}
