/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfapp.davinci

import com.pingidentity.orchestrate.Node

// The counter just to ensure compose triggers recomposition.
// When [prev] and [node] are the same, the recomposition will not be triggered.
data class DaVinciState(val node: Node? = null, val counter: Int = 0)
