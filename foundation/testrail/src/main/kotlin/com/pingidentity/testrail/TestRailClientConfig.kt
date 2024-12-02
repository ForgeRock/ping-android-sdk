/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.testrail

import java.lang.System.getenv
import java.util.Calendar

class TestRailClientConfig {
    var enable = getenv().getOrDefault("TESTRAIL_ENABLE", "false").toBoolean()
    var debug = getenv().getOrDefault("TESTRAIL_DEBUG", "false").toBoolean()
    var username = getenv().getOrDefault("TESTRAIL_USERNAME", "")
    var password = getenv().getOrDefault("TESTRAIL_API_KEY", "")
    var url = "https://forgerock.testrail.io"
    var projectId: String = getenv().getOrDefault("TESTRAIL_PROJECT_ID", "0")
    var runId: String =
        getenv().getOrDefault("TESTRAIL_RUN_ID", "") // When empty, a new run will be created
    var runName = getenv()
        .getOrDefault("TESTRAIL_RUN_NAME", "Automated Test Run ${Calendar.getInstance().time}")
    var continueWhenFailed = true

}