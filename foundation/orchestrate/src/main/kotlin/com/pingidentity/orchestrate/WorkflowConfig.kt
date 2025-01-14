/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate

import androidx.annotation.VisibleForTesting
import com.pingidentity.logger.Logger
import com.pingidentity.logger.None
import com.pingidentity.utils.PingDsl
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging

/**
 * Enum class representing the mode of module override.
 */
enum class OverrideMode {
    OVERRIDE, // Override the previous registered module
    APPEND, // Append to the list, and cannot be overridden
    IGNORE // Ignore if the module is already registered
}

/**
 * DSL class for configuring a Workflow.
 */
@PingDsl
open class WorkflowConfig {
    // Use a map instead of a list, so that we can override the registered module
    // But when using a map, what if I want to register a module twice? for example inject customHeader
    @VisibleForTesting
    val modules = mutableListOf<ModuleRegistry<*>>()

    // Timeout for the HTTP client, default is 15 seconds
    var timeout: Long = 15000

    // Logger for the log, default is None
    var logger = Logger.logger

    // HTTP client for the engine
    lateinit var httpClient: HttpClient

    /**
     * Register a module.
     *
     * @param module The module to be registered.
     * @param priority The priority of the module in the registry. Default is 10.
     * @param mode The mode of the module registration. Default is OVERRIDE. If the mode is OVERRIDE, the module will be overridden if it is already registered.
     * @param config The configuration for the module.
     */
    fun <Config : Any> module(
        module: Module<Config>,
        priority: Int = 10,
        mode: OverrideMode = OverrideMode.OVERRIDE,
        config: Config.() -> Unit = {},
    ) {
        when (mode) {
            OverrideMode.OVERRIDE -> {
                // For override, we need to replace the module if it is already registered
                // and retain the order and priority of the modules
                modules.indexOfFirst { it.module == module }.let {
                    if (it != -1) {
                        modules[it] = ModuleRegistry(
                            module,
                            modules[it].priority, // Retain the priority
                            module.config().apply(config),
                            module.setup
                        )
                    } else {
                        modules.add(
                            ModuleRegistry(
                                module,
                                priority,
                                module.config().apply(config),
                                module.setup
                            )
                        )
                    }
                }
            }

            OverrideMode.APPEND -> {
                modules.add(
                    ModuleRegistry(
                        ModuleDelegate(module),
                        priority,
                        module.config().apply(config),
                        module.setup
                    )
                )
            }

            OverrideMode.IGNORE -> {
                if (modules.none { it.module == module }) {
                    modules.add(
                        ModuleRegistry(
                            module,
                            priority,
                            module.config().apply(config),
                            module.setup
                        )
                    )
                }
            }
        }
    }

    /**
     * Registers the workflow and initializes the HTTP client if not already initialized.
     *
     * @param workflow The workflow to be registered.
     */
    internal fun register(workflow: Workflow) {
        if (!::httpClient.isInitialized) {
            httpClient = HttpClient(CIO) {

                val log = logger
                followRedirects = false
                if (logger !is None) {
                    install(Logging) {
                        logger =
                            object : io.ktor.client.plugins.logging.Logger {
                                override fun log(message: String) {
                                    log.d(message)
                                }
                            }
                        level = LogLevel.ALL
                    }
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = timeout
                }
            }
        }
        modules.sortBy { it.priority }
        modules.forEach { it.register(workflow) }
    }
}