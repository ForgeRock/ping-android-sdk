/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido.davinci

import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.domerrors.NotSupportedError
import androidx.credentials.exceptions.domerrors.UnknownError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.davinci.plugin.DaVinciAware
import com.pingidentity.davinci.plugin.Failable
import com.pingidentity.davinci.plugin.Submittable
import com.pingidentity.fido.Constants
import com.pingidentity.logger.Logger
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

/**
 * An abstract base class for FIDO2 collectors in the DaVinci workflow.
 *
 * This class provides common functionality for FIDO2-related collectors, handling
 * initialization of basic properties like key, label, trigger, and required status.
 * It implements the necessary interfaces for DaVinci workflow integration.
 *
 * @property key The unique identifier for the collector
 * @property label The display label for the collector
 * @property trigger The trigger event that activates this collector
 * @property required Whether the collector is mandatory for the workflow
 */
abstract class AbstractFidoCollector : Collector<JsonObject>, DaVinciAware, Submittable, Failable {
    /**
     * The DaVinci workflow instance that this collector is associated with.
     * This is automatically injected by the DaVinci framework.
     */
    override lateinit var davinci: DaVinci

    /**
     * Logger instance for this collector, lazily initialized from the DaVinci configuration.
     */
    val logger: Logger by lazy {
        davinci.config.logger
    }

    var key = ""
        private set

    var label = ""
        private set
    var trigger = ""
        private set
    var required = false
        private set


    var error: String? = null

    /**
     * Returns the event type that this collector handles.
     *
     * @return The event type string, always "submit" for FIDO2 collectors
     */
    override fun eventType(): String {
        return if (error == null) Constants.EVENT_TYPE_SUBMIT else Constants.EVENT_TYPE_ACTION
    }

    /**
     * Returns the unique identifier for this collector instance.
     *
     * @return The collector's key as its identifier
     */
    override fun id(): String {
        return key
    }

    override fun error(): String? = error

    /**
     * Initializes the collector with the provided input data.
     *
     * Extracts and sets the key, label, trigger, and required properties from the input JSON.
     *
     * @param input The JSON object containing initialization parameters
     * @return This collector instance for method chaining
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    override fun init(input: JsonObject): Collector<JsonObject> {
        logger.d("Initializing FIDO2 collector with input: $input")
        key = input[Constants.FIELD_KEY]?.jsonPrimitive?.content ?: ""
        label = input[Constants.FIELD_LABEL]?.jsonPrimitive?.content ?: ""
        trigger = input[Constants.FIELD_TRIGGER]?.jsonPrimitive?.content ?: ""
        required = input[Constants.FIELD_REQUIRED]?.jsonPrimitive?.boolean ?: false
        return this
    }


    /**
     * Handles errors that occur during FIDO2 operations.
     *
     * This method converts various types of credential exceptions into appropriate
     * error messages that the Journey server can understand and process.
     *
     * @param exception The throwable error to handle and convert
     */
    fun handleError(exception: Throwable) {
        logger.e(
            "Handling FIDO2 error: ${exception::class.simpleName} - ${exception.message}",
            exception
        )
        when (exception) {
            is CreateCredentialUnsupportedException -> {
                logger.d("Credential creation unsupported")
                error = NotSupportedError::class.simpleName
            }

            is GetCredentialUnsupportedException -> {
                logger.d("Get Credential unsupported")
                error = NotSupportedError::class.simpleName
            }

            is CreateCredentialCancellationException -> {
                logger.d("Credential creation cancelled")
                error = NotAllowedError::class.simpleName
            }

            is GetCredentialCancellationException -> {
                logger.d("Get Credential cancelled")
                error = NotAllowedError::class.simpleName
            }

            is CreatePublicKeyCredentialDomException -> {
                logger.d("DOM exception occurred: ${exception.domError::class.simpleName}")
                error = exception.domError::class.simpleName
            }

            is GetPublicKeyCredentialDomException -> {
                logger.d("DOM exception occurred: ${exception.domError::class.simpleName}")
                error = exception.domError::class.simpleName
            }

            else -> {
                logger.d("Unknown error occurred")
                error = UnknownError::class.simpleName
            }
        }
    }
}