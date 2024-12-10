/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.testrail

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddRunRequest(
    val name: String,
    @SerialName("include_all")
    val includeAll: Boolean = true,
)

@Serializable
data class GetRunResponse(
    val id: String,
)

@Serializable
data class AddResultRequest(
    @SerialName("status_id")
    val statusId: Int,
    val comment: String = "Triggered by automated test",
)

