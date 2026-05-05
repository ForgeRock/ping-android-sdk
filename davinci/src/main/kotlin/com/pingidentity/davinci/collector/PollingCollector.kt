/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci.collector

import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.davinci.plugin.DaVinciAware
import com.pingidentity.davinci.plugin.Submittable
import com.pingidentity.orchestrate.Closeable
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ContinueNodeAware
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// JSON field constants for polling configuration and status

/** Event type identifier for polling collectors */
private const val POLLING = "polling"

/** JSON key for the polling interval in milliseconds between requests */
private const val POLL_INTERVAL = "pollInterval"

/** JSON key for the maximum number of polling retry attempts */
private const val POLL_RETRIES = "pollRetries"

/** JSON key indicating whether to actively poll for challenge status */
private const val POLL_CHALLENGE_STATUS = "pollChallengeStatus"

/** JSON key for the status field in payloads and responses */
private const val STATUS = "status"

/** JSON key for the challenge identifier used in polling URLs */
private const val CHALLENGE = "challenge"

/** JSON key in server responses indicating if a challenge is complete */
private const val IS_CHALLENGE_COMPLETE = "isChallengeComplete"

/** JSON key in error responses identifying the type of service error */
private const val SERVICE_NAME = "serviceName"

/** The serviceName value returned by the server when the challenge has expired */
private const val CHALLENGE_EXPIRED = "challengeExpired"

/**
 * Sealed class representing the status of a polling operation.
 */
sealed class PollingStatus {
    /**
     * Indicates that polling is in progress.
     * @param retryCount The current retry count.
     * @param maxRetries The maximum number of retries.
     */
    data class Continue(val retryCount: Int, val maxRetries: Int) : PollingStatus()

    /**
     * Indicates that polling has expired (max retries reached).
     */
    data object TimedOut : PollingStatus()

    /**
     * Indicates that the challenge has expired because the server returned a non-200 HTTP response.
     * Polling stops immediately when this status is emitted.
     */
    data object Expired : PollingStatus()

    /**
     * Indicates that an error occurred during polling.
     * @param exception The exception that occurred.
     */
    data class Error(val exception: Exception) : PollingStatus()

    /**
     * Indicates that polling has completed successfully.
     */
    data class Complete(val status: String) : PollingStatus()
}

/**
 * A collector that handles asynchronous polling operations in DaVinci authentication flows.
 *
 * The PollingCollector is used for authentication scenarios that require waiting for user actions
 * on another device or channel, such as:
 * - Push notification authentication
 * - QR code scanning
 * - Email verification
 * - Out-of-band (OOB) authentication
 *
 * ## Polling Modes
 *
 * ### Simple Polling Mode
 * When [pollChallengeStatus] is `false` or [challenge] is empty, performs a simple delay-based
 * polling without actively checking server status. After each delay:
 * - Decrements [retriesAllowed]
 * - Emits [PollingStatus.Continue] if retries remain
 * - Emits [PollingStatus.TimedOut] when retries reach 0
 *
 * This mode is useful for basic waiting periods where the client simply needs to wait before
 * checking again.
 *
 * ### Challenge Status Polling Mode
 * When [pollChallengeStatus] is `true` and [challenge] is not empty, actively polls a server
 * endpoint to check the status of an authentication challenge. The polling continues until:
 * - The challenge is completed successfully ([PollingStatus.Complete])
 * - Maximum retries are reached ([PollingStatus.TimedOut])
 * - The server returns a non-200 HTTP response ([PollingStatus.Expired])
 * - An error occurs ([PollingStatus.Error])
 *
 * **Polling Endpoint:** `{baseUrl}/davinci/user/credentials/challenge/{challenge}/status`
 *
 * ## Flow-based API
 *
 * The [pollStatus] method returns a Flow that emits [PollingStatus] updates throughout the polling
 * lifecycle, providing real-time feedback to the application for UI updates.
 *
 * **Flow Interception:** The flow automatically intercepts each emission using `onEach` to set
 * the [value] property before the caller receives it. This ensures [value] is always synchronized
 * with the emitted status.
 *
 * ```
 *
 * ## Value Assignment
 *
 * The [value] property is automatically set via flow interception based on the polling outcome:
 * - [PollingStatus.Complete] → Server status value (e.g., "approved")
 * - [PollingStatus.TimedOut] → "timedOut"
 * - [PollingStatus.Expired] → "expired"
 * - [PollingStatus.Error] → "error"
 * - [PollingStatus.Continue] → "continue"
 *
 * This value is submitted as part of the form data when continuing to the next node in the flow.
 *
 * @see PollingStatus
 * @see poll
 */
class PollingCollector : SingleValueCollector(), Submittable, ContinueNodeAware, DaVinciAware,
    Closeable {

    /**
     * The continue node for the DaVinci flow.
     * Used to extract configuration and context for the polling operation.
     */
    override lateinit var continueNode: ContinueNode

    /**
     * The DaVinci workflow instance.
     * Provides access to HTTP client and logger for polling operations.
     */
    override lateinit var davinci: DaVinci

    /**
     * Polling interval in milliseconds between each polling attempt.
     * Default value is "2000" (2 seconds).
     */
    lateinit var pollInterval: String
        private set

    /**
     * Maximum number of polling attempts before timing out.
     * Default value is "60".
     */
    lateinit var pollRetries: String
        private set

    /**
     * Whether to actively poll for challenge status.
     * When `false`, performs simple delay polling.
     * When `true`, polls the server endpoint for challenge completion.
     */
    var pollChallengeStatus = false
        private set

    /**
     * The challenge identifier to poll for.
     * Used to construct the polling endpoint URL.
     */
    lateinit var challenge: String
        private set


    override fun eventType(): String = POLLING

    /**
     * The number of polling attempts remaining before timeout in simple polling mode.
     *
     * This property is only used in simple polling mode (when [pollChallengeStatus] is false).
     * It is initialized to [pollRetries] value and decremented after each polling interval.
     * When it reaches 0, [PollingStatus.TimedOut] is emitted.
     *
     * For challenge status polling mode, this value is maintained but not actively used
     * in the polling logic.
     */
    var retriesAllowed: Int = 0

    /**
     * Initializes the PollingCollector with configuration from the input JSON.
     *
     * Extracts and sets the following parameters:
     * - [pollInterval]: Polling interval in milliseconds (default: "2000")
     * - [pollRetries]: Maximum number of retries (default: "60")
     * - [pollChallengeStatus]: Whether to poll for challenge status (default: false)
     * - [challenge]: Challenge identifier for polling (default: "")
     *
     * @param input JSON object containing the collector configuration
     * @return This PollingCollector instance for method chaining
     *
     * @see SingleValueCollector.init
     */
    override fun init(input: JsonObject): PollingCollector {
        super.init(input)
        // Extract polling configuration from input JSON with sensible defaults
        pollInterval = input[POLL_INTERVAL]?.jsonPrimitive?.content ?: "2000" // Default: 2 seconds
        pollRetries = input[POLL_RETRIES]?.jsonPrimitive?.content ?: "60" // Default: 60 attempts
        pollChallengeStatus =
            input[POLL_CHALLENGE_STATUS]?.jsonPrimitive?.boolean ?: false // Default: simple polling
        challenge = input[CHALLENGE]?.jsonPrimitive?.content ?: "" // Default: no challenge ID

        // Initialize remaining retries counter for simple polling mode
        retriesAllowed = pollRetries.toInt()
        return this
    }

    /**
     * Polls for the challenge status and emits the current polling status.
     *
     * This method returns a Flow that emits [PollingStatus] updates throughout the polling lifecycle.
     * The polling behavior depends on the configuration set during [init]:
     *
     * ## Challenge Status Polling Mode
     * When [pollChallengeStatus] is `true` and [challenge] is not empty:
     *
     * 1. **Extracts configuration** from [continueNode].input:
     *    - `_links.next.href`: Used to construct base URL
     *    - `interactionId`: Required header for polling requests
     *
     * 2. **Constructs polling URL**:
     *    `{baseUrl}/davinci/user/credentials/challenge/{challenge}/status`
     *
     * 3. **Polling loop** (repeats up to [pollRetries] times):
     *    - Delays for [pollInterval] milliseconds
     *    - Makes HTTP POST request with `interactionId` header
     *    - Parses JSON response for `isChallengeComplete` field
     *    - **If HTTP error (status ≠ 200)**: Emits [PollingStatus.Expired] and stops polling
     *    - **If challenge complete**: Emits [PollingStatus.Complete] with server status and exits
     *    - **If not complete**: Emits [PollingStatus.Continue] and continues polling
     *    - **If exception occurs**: Emits [PollingStatus.Error] and exits
     *
     * 4. **If max retries reached**: Emits [PollingStatus.TimedOut]
     *
     * 5. **If configuration missing**: Emits [PollingStatus.Error] immediately
     *
     * ## Simple Polling Mode
     * When [pollChallengeStatus] is `false` or [challenge] is empty:
     *
     * 1. **Validates [pollInterval]** (must be > 0)
     * 2. **Delays** for [pollInterval] milliseconds
     * 3. **Decrements [retriesAllowed]**
     * 4. **If retries remain**: Emits [PollingStatus.Continue] with remaining retries
     * 5. **If retries exhausted**: Emits [PollingStatus.TimedOut]
     * 6. **If invalid interval**: Emits [PollingStatus.Error]
     *
     * ## Flow Interception
     *
     * After emission but before the caller receives it, the flow uses `onEach` to set
     * the [value] property based on the emitted status:
     *
     * | Status Type | Value Set |
     * |-------------|-----------|
     * | [PollingStatus.Complete] | Server status (e.g., "approved") |
     * | [PollingStatus.TimedOut] | "timedOut" |
     * | [PollingStatus.Expired] | "expired" |
     * | [PollingStatus.Error] | "error" |
     * | [PollingStatus.Continue] | "continue" |
     *
     * @return A Flow of [PollingStatus] that emits status updates throughout the polling operation.
     *         The Flow completes naturally when polling finishes (Complete, TimedOut, Expired, or Error).
     *
     * @throws IllegalStateException if [continueNode] or [davinci] are not initialized (challenge mode only)
     *
     * @see PollingStatus
     * @see PollingStatus.Continue
     * @see PollingStatus.Complete
     * @see PollingStatus.TimedOut
     * @see PollingStatus.Expired
     * @see PollingStatus.Error
     */
    fun pollStatus(): Flow<PollingStatus> = flow {
        // Determine polling mode based on configuration
        if (pollChallengeStatus && challenge.isNotEmpty()) {
            // --- Challenge Status Polling Mode ---
            // Actively polls the server to check if a challenge has been completed

            // Extract polling configuration from the continue node input
            val links = continueNode.input["_links"]?.jsonObject
            val selfHref = links?.get("self")?.jsonObject?.get("href")?.jsonPrimitive?.content
            val interactionId = continueNode.input["interactionId"]?.jsonPrimitive?.content

            if (selfHref != null && interactionId != null) {
                // Construct the polling endpoint URL
                // Example: https://auth.pingone.ca/env-id/davinci/user/credentials/challenge/abc123/status
                val baseUrl = selfHref.substringBefore("/davinci/connections")
                val pollingUrl = "$baseUrl/davinci/user/credentials/challenge/$challenge/status"

                val httpClient = davinci.config.httpClient
                val maxRetries = pollRetries.toIntOrNull() ?: 60
                val interval = pollInterval.toLongOrNull() ?: 2000L

                var retryCount = 0
                var shouldContinuePolling = true

                // Poll repeatedly until challenge is complete, max retries reached, or error occurs
                while (retryCount < maxRetries && shouldContinuePolling) {
                    // Wait before making the next polling request
                    delay(interval)

                    try {
                        // Make HTTP POST request to check challenge status
                        val response = httpClient.request {
                            url = pollingUrl
                            header("interactionId", interactionId)
                            post()
                        }

                        // Handle non-200 HTTP responses
                        if (response.status != 200) {
                            val errorBody: String = response.body()
                            davinci.config.logger.i("Server returned non-200 response: ${response.status}, $errorBody")
                            val errorJson = runCatching {
                                Json.parseToJsonElement(errorBody).jsonObject
                            }.getOrNull()
                            val serviceName = errorJson?.get(SERVICE_NAME)?.jsonPrimitive?.content
                            if (serviceName == CHALLENGE_EXPIRED) {
                                emit(PollingStatus.Expired)
                            } else {
                                emit(PollingStatus.Error(IllegalStateException("Server error: ${response.status}, $errorBody")))
                            }
                            shouldContinuePolling = false
                        } else {
                            // Parse the JSON response to check challenge completion status
                            val responseBody: String = response.body()
                            val jsonResponse = Json.parseToJsonElement(responseBody).jsonObject
                            val challengeComplete =
                                jsonResponse[IS_CHALLENGE_COMPLETE]?.jsonPrimitive?.boolean ?: false

                            if (challengeComplete) {
                                // Challenge successfully completed - emit Complete with status from server
                                val statusValue = jsonResponse[STATUS]?.jsonPrimitive?.content ?: ""
                                emit(PollingStatus.Complete(statusValue))
                                shouldContinuePolling = false // Exit the polling loop
                            } else {
                                // Challenge not yet complete - emit Continue status with current retry count
                                emit(PollingStatus.Continue(retryCount + 1, maxRetries))
                            }
                        }
                    } catch (e: Exception) {
                        // Handle network errors, JSON parsing errors, or other exceptions
                        currentCoroutineContext().ensureActive()
                        davinci.config.logger.w("Error polling challenge status", e)
                        emit(PollingStatus.Error(e))
                        shouldContinuePolling = false // Exit the polling loop on exception
                    }

                    // Yield to allow coroutine cancellation between polling attempts
                    if (shouldContinuePolling) {
                        yield()
                        retryCount++
                    }
                }

                // If we exited the loop due to max retries (not due to completion or error)
                if (shouldContinuePolling) {
                    emit(PollingStatus.TimedOut)
                }
            } else {
                // Required configuration (selfHref or interactionId) is missing
                emit(PollingStatus.Error(IllegalStateException("Missing selfHref or interactionId")))
            }
        } else {
            // --- Continue Polling Mode ---
            // Performs a simple delay without actively checking server status

            if (pollInterval.toInt() > 0) {
                // Wait for the specified interval
                delay(pollInterval.toLong())

                // Decrement retries and emit Continue status
                if (--retriesAllowed <= 0) {
                    emit(PollingStatus.TimedOut)
                } else {
                    // Continue Polling is consider completed, but set status to server with value "continue"
                    emit(PollingStatus.Complete("continue"))
                }
            } else {
                // Invalid polling interval - emit error
                emit(PollingStatus.Error(IllegalArgumentException("Invalid pollInterval: $pollInterval")))
            }
        }
    }.onEach { status ->
        // Intercept each emission and set the value property before caller receives it
        value = when (status) {
            is PollingStatus.Complete -> status.status
            is PollingStatus.TimedOut -> "timedOut"
            is PollingStatus.Error -> "error"
            is PollingStatus.Continue -> "continue"
            is PollingStatus.Expired -> "expired"
        }
    }

    /**
     * Clears the collector's value property.
     * @see Closeable.close
     */
    override fun close() {
        value = ""
    }
}