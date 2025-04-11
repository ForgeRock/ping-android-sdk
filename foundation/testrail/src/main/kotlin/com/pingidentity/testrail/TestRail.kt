/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.testrail

import org.junit.rules.TestWatcher
import org.junit.runner.Description

object TestRailWatcher: TestWatcher() {

    private val testRailClient: TestRailClient = TestRailClient {
    }

    init {
        testRailClient.prepareRun()
    }

    override fun failed(e: Throwable?, description: Description) {
        super.failed(e, description)
        val testRailCase = description.getAnnotation(TestRailCase::class.java)
        testRailCase?.ids?.forEach {
            testRailClient.addResult(it, 5)
        }
    }

    override fun succeeded(description: Description) {
        super.succeeded(description)
        val testRailCase = description.getAnnotation(TestRailCase::class.java)
        testRailCase?.ids?.forEach {
            testRailClient.addResult(it, 1)
        }
    }

}