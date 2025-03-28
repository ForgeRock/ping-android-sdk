/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import com.pingidentity.utils.Result
import com.pingidentity.journey.callback.NameCallback
import com.pingidentity.journey.callback.PasswordCallback
import com.pingidentity.journey.module.Oidc
import com.pingidentity.journey.module.Session
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.logger.STANDARD
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.module.Cookie
import com.pingidentity.storage.MemoryStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.assertTrue
import kotlin.time.Duration

class JourneyTest {

    @BeforeTest
    fun setUp() {
        CallbackRegistry().initialize()
    }

    @Ignore
    fun `Journey happy path test with Oidc`() =
        runTest {
            val journey =
                Journey {
                    serverUrl = "http://andy.petrov.ca:8080/openam"
                    realm = "root"
                    journeyName = "login"
                    logger = Logger.CONSOLE
                    cookie = "iPlanetDirectoryPro"
                   // Oidc as module
                    module(Oidc) {
                        clientId = "AndroidTest"
                        discoveryEndpoint =
                            "http://andy.petrov.ca:8080/openam/oauth2/.well-known/openid-configuration"
                        scopes = mutableSetOf("openid", "email", "address")
                        redirectUri = "org.forgerock.demo:/oauth2redirect"
                        storage = MemoryStorage()
                        logger = Logger.STANDARD
                    }
                    module(Cookie) {
                        persist = mutableListOf("iPlanetDirectoryPro")
                        storage = MemoryStorage()
                    }
                    module(Session) {
                        storage = MemoryStorage()
                    }
                }

            var node = journey.start() // Return first Node
            assertTrue(node is ContinueNode)
            assertTrue { (node as ContinueNode).callbacks.size == 2 }

            node.callbacks.forEach {
                when (it) {
                    is NameCallback -> {
                        it.name = "demo"
                    }

                    is PasswordCallback -> {
                        it.password = "Demo4567!"
                    }
                }
            }

            node = node.next()
            assertTrue(node is SuccessNode)
            node.session

            val user = journey.user()
            val result = user?.token()
            assertTrue(result is Result.Success)

            val ssoToken: SSOToken? = journey.session()
        }

    @Ignore
    fun `Journey happy path test without Oidc`() =
        runTest(timeout = Duration.INFINITE) {
            val journey =
                Journey {
                    serverUrl = "http://andy.petrov.ca:8080/openam"
                    realm = "root"
                    journeyName = "login"
                    logger = Logger.CONSOLE
                    // Oidc as module
                    module(Cookie) {
                        persist = mutableListOf("iPlanetDirectoryPro")
                        storage = MemoryStorage()
                    }
                    module(Session) {
                        storage = MemoryStorage()
                    }
                }

            var node = journey.start() // Return first Node
            assertTrue(node is ContinueNode)
            assertTrue { (node as ContinueNode).callbacks.size == 2 }

            node.callbacks.forEach {
                when (it) {
                    is NameCallback -> {
                        it.name = "demo"
                    }

                    is PasswordCallback -> {
                        it.password = "Demo4567!"
                    }
                }
            }

            node = node.next()
            assertTrue(node is SuccessNode)

            assertTrue {
                (node as SuccessNode).session.value.isNotEmpty()
            }

            //start again should return Success immediately, since the session is already established with the Cookie module
            node = journey.start()
            assertTrue(node is SuccessNode)

        }



}
