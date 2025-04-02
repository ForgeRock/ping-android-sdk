/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.testrail

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

inline fun TestRailClient(block: TestRailClientConfig.() -> Unit = {}): TestRailClient {
    val config = TestRailClientConfig().apply(block)
    return TestRailClient(config)
}

class TestRailClient(private val config: TestRailClientConfig) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(Auth) {
            basic {
                credentials {
                    BasicAuthCredentials(config.username, config.password)
                }
                sendWithoutRequest { true }
            }
        }
        install(Logging) {
            if (config.debug) {
                logger =
                    object : Logger {
                        override fun log(message: String) {
                            println(message)
                        }
                    }
                level = LogLevel.ALL
            }
        }
    }

    fun prepareRun() = runBlocking {
        if (config.enable.not()) return@runBlocking

        if (config.runId.isEmpty()) {
            val response: GetRunResponse =
                client.post("${config.url}/index.php?/api/v2/add_run/${config.projectId}") {
                    contentType(ContentType.Application.Json)
                    setBody(AddRunRequest(config.runName))
                }.body()
            //Set the run id
            config.runId = response.id
        }
    }

    fun addResult(caseId: Int, statusId: Int) = runBlocking {
        if (config.enable.not()) return@runBlocking

        val response: HttpResponse =
            client.post("${config.url}/index.php?/api/v2/add_result_for_case/${config.runId}/${caseId}") {
                contentType(ContentType.Application.Json)
                setBody(AddResultRequest(statusId))
            }
        if (response.status.value != 200) {
            if (!config.continueWhenFailed) {
                throw Exception("Failed to add result for case $caseId")
            } else {
                println("Failed to add result for case $caseId: ${response.body<String>()}")
            }
        }
    }

}