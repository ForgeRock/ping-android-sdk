/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate

import io.ktor.client.request.request
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Creates a new Workflow instance with the provided configuration block.
 * @param block The configuration block for the Workflow.
 * @return A new Workflow instance.
 */
fun Workflow(block: WorkflowConfig.() -> Unit = {}): Workflow {
    return Workflow(WorkflowConfig().apply(block))
}

/**
 * Class representing the context of a flow.
 * @property flowContext The shared context of the flow.
 */
class FlowContext(val flowContext: SharedContext)

/**
 * Class representing a workflow.
 * @property config The configuration for the workflow.
 */
class Workflow(val config: WorkflowConfig) {
    // Global context
    val sharedContext = SharedContext(ConcurrentHashMap())

    /** init -> start -> response -> transform -> node -> success -> signOff
     *                      ^                      |
     *                      |--------  next <----- |
     */
    internal val init = mutableListOf<suspend () -> Unit>()
    internal val start = mutableListOf<suspend FlowContext.(Request) -> Request>()
    internal val next = mutableListOf<suspend FlowContext.(ContinueNode, Request) -> Request>()
    internal val response = mutableListOf<suspend FlowContext.(Response) -> Unit>()
    internal val node = mutableListOf<suspend FlowContext.(Node) -> Node>()
    internal val success = mutableListOf<suspend FlowContext.(SuccessNode) -> SuccessNode>()
    internal val signOff = mutableListOf<suspend (Request) -> Request>()

    // Transform response to Node, we can only have one transform
    internal lateinit var transform: suspend FlowContext.(Response) -> Node

    // Control variables
    private var started = false
    private val lock = Mutex()
    private val initLock = Mutex()

    init {
        config.register(this)
    }

    /**
     * Starts the workflow with the provided request.
     * @param request The request to start the workflow with.
     * @return The resulting Node after processing the workflow.
     */
    suspend fun start(request: Request): Node = lock.withLock {
        // Before we start, make sure all the module init has been completed
        return catch {
            init()
            config.logger.i("Starting...")
            val sharedContext = FlowContext(SharedContext(mutableMapOf()))
            val req =
                start.asFlow()
                    .scan(request) { result, value -> sharedContext.value(result) }.last()
            val response = send(sharedContext, req)
            val initialNode = sharedContext.transform(response)
            return next(sharedContext, node.asFlow().scan(initialNode) { result, value ->
                value(sharedContext, result)
            }.last())
        }
    }

    /**
     * Starts the workflow with a default request.
     * @return The resulting Node after processing the workflow.
     */
    suspend fun start(): Node {
        return start(Request())
    }

    /**
     * Signs off the workflow.
     * @return A Result indicating the success or failure of the sign off.
     */
    suspend fun signOff(): Result<Unit> = lock.withLock {
        config.logger.i("SignOff...")
        try {
            init()
            signOff.asFlow().scan(Request()) { result, value -> value(result) }.last()
                .also { send(it) }
            return Result.success(Unit)
        } catch (e: Throwable) {
            config.logger.e("Error during sign off", e)
            coroutineContext.ensureActive()
            return Result.failure(e)
        }
    }

    /**
     * Processes the next node in the workflow.
     * @param context The context of the flow.
     * @param current The current connector.
     * @return The resulting Node after processing the next step.
     */
    internal suspend fun next(
        context: FlowContext,
        current: ContinueNode,
    ): Node = lock.withLock {
        return catch {
            config.logger.i("Next...")
            val initialRequest = current.asRequest()
            val request =
                next.asFlow()
                    .scan(initialRequest) { result, value -> context.value(current, result) }
                    .last()
            current.close()
            val initialNode = context.transform(send(context, request))
            return next(
                context,
                node.asFlow().scan(initialNode) { result, value -> value(context, result) }.last(),
            )
        }
    }

    /**
     * Initializes the workflow.
     */
    suspend fun init() =
        coroutineScope {
            if (!started) {
                initLock.withLock {
                    if (!started) {
                        init.map { async { it() } }.awaitAll()
                        started = true
                    }
                }
            }
        }

    /**
     * Processes the next node if it is a success node.
     * @param context The context of the flow.
     * @param node The current node.
     * @return The resulting Node after processing the next step.
     */
    private suspend fun next(
        context: FlowContext,
        node: Node,
    ): Node {
        return if (node is SuccessNode) {
            success.asFlow().scan(node) { result, value -> context.value(result) }.last()
        } else {
            node
        }
    }

    /**
     * Sends a request and returns the response.
     * @param context The context of the flow.
     * @param request The request to be sent.
     * @return The response received.
     */
    private suspend fun send(
        context: FlowContext,
        request: Request,
    ): Response {
        val resp = Response(request, config.httpClient.request(request.builder))
        response(context, resp)
        return resp
    }

    /**
     * Sends a request and returns the response.
     * @param request The request to be sent.
     * @return The response received.
     */
    private suspend fun send(request: Request): Response {
        return Response(request, config.httpClient.request(request.builder))
    }

    /**
     * Processes the response.
     * @param context The context of the flow.
     * @param resp The response to be processed.
     */
    private suspend fun response(
        context: FlowContext,
        resp: Response,
    ) = coroutineScope {
        //Execute all the response actions in parallel, response is read-only
        response.map { launch { context.it(resp) } }.joinAll()
    }
}