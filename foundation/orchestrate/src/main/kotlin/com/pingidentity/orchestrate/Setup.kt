/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate

import com.pingidentity.logger.Logger
import io.ktor.client.HttpClient

/**
 * Class for a Setup. A Setup represents the setup of a module in the application.
 * @property workflow The workflow of the application.
 * @property sharedContext The shared context of the application.
 * @property logger The logger used in the application.
 * @property httpClient The HTTP client used in the application.
 * @property config The configuration for the module.
 */
class Setup<ModuleConfig : Any> internal constructor(
    val workflow: Workflow,
    val sharedContext: SharedContext = workflow.sharedContext,
    val logger: Logger = workflow.config.logger,
    val httpClient: HttpClient = workflow.config.httpClient,
    val config: ModuleConfig,
) {
    /**
     * Adds an initialization block to the workflow.
     * @param block The block to be added.
     */
    fun init(block: suspend () -> Unit) {
        workflow.init.add(block)
    }

    /**
     * Adds a start block to the workflow.
     * @param block The block to be added.
     */
    fun start(block: suspend FlowContext.(Request) -> Request) {
        workflow.start.add(block)
    }

    /**
     * Adds a next block to the workflow.
     * @param block The block to be added.
     */
    fun next(block: suspend FlowContext.(ContinueNode, Request) -> Request) {
        workflow.next.add(block)
    }

    /**
     * Adds a response block to the workflow.
     * @param block The block to be added.
     */
    fun response(block: suspend FlowContext.(Response) -> Unit) {
        workflow.response.add(block)
    }

    /**
     * Adds a node block to the workflow.
     * @param block The block to be added.
     */
    fun node(block: suspend FlowContext.(Node) -> Node) {
        workflow.node.add(block)
    }

    /**
     * Adds a success block to the workflow.
     * @param block The block to be added.
     */
    fun success(block: suspend FlowContext.(SuccessNode) -> SuccessNode) {
        workflow.success.add(block)
    }

    /**
     * Sets the transform block of the workflow.
     * @param block The block to be set.
     */
    fun transform(block: suspend FlowContext.(Response) -> Node) {
        workflow.transform = block
    }

    /**
     * Adds a sign off block to the workflow.
     * @param block The block to be added.
     */
    fun signOff(block: suspend (Request) -> Request) {
        workflow.signOff.add(block)
    }
}