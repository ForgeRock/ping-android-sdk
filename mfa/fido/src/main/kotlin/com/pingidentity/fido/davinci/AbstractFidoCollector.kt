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
import com.pingidentity.davinci.plugin.ActionKeyProvider
import com.pingidentity.davinci.plugin.Collector
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.davinci.plugin.DaVinciAware
import com.pingidentity.davinci.plugin.Submittable
import com.pingidentity.fido.Constants
import com.pingidentity.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * An abstract base class for FIDO2 collectors in the DaVinci workflow.
 *
 * @property key The unique identifier for the collector
 * @property label The display label for the collector
 * @property trigger The trigger event that activates this collector
 * @property required Whether the collector is mandatory for the workflow
 */
abstract class AbstractFidoCollector : Collector<JsonObject>, DaVinciAware, Submittable, ActionKeyProvider {
    override lateinit var davinci: DaVinci

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

    /** DOMException name set when a FIDO operation fails; drives the DaVinci error response. */
    var errorCode: String? = null

    /** Supplies the DOMException name as `actionKey` when a FIDO error has occurred. */
    override val actionKey: String? get() = errorCode

    override fun eventType(): String {
        return if (errorCode == null) Constants.EVENT_TYPE_SUBMIT else Constants.EVENT_TYPE_ACTION
    }

    override fun id(): String {
        return key
    }

    override fun init(input: JsonObject): Collector<JsonObject> {
        logger.d("Initializing FIDO2 collector with input: $input")
        key = input[Constants.FIELD_KEY]?.jsonPrimitive?.content ?: ""
        label = input[Constants.FIELD_LABEL]?.jsonPrimitive?.content ?: ""
        trigger = input[Constants.FIELD_TRIGGER]?.jsonPrimitive?.content ?: ""
        required = input[Constants.FIELD_REQUIRED]?.jsonPrimitive?.boolean ?: false
        errorCode = null
        return this
    }

    /**
     * Returns an empty [JsonObject] when a FIDO error has occurred — non-null sentinel so
     * `Collectors.eventType` picks up this collector and the `actionKey` error path fires.
     * Subclasses override to provide the success payload.
     */
    override fun payload(): JsonObject? {
        return if (errorCode != null) buildJsonObject { } else null
    }

    /**
     * Handles errors during FIDO2 operations, setting [errorCode] to the appropriate
     * WebAuthn DOMException name.
     */
    fun handleError(exception: Throwable) {
        if (exception is CancellationException) throw exception
        logger.e(
            "Handling FIDO2 error: ${exception::class.simpleName} - ${exception.message}",
            exception
        )
        when (exception) {
            is CreateCredentialUnsupportedException -> {
                logger.d("Credential creation unsupported")
                errorCode = NotSupportedError::class.simpleName
            }

            is GetCredentialUnsupportedException -> {
                logger.d("Get Credential unsupported")
                errorCode = NotSupportedError::class.simpleName
            }

            is CreateCredentialCancellationException -> {
                logger.d("Credential creation cancelled")
                errorCode = NotAllowedError::class.simpleName
            }

            is GetCredentialCancellationException -> {
                logger.d("Get Credential cancelled")
                errorCode = NotAllowedError::class.simpleName
            }

            is CreatePublicKeyCredentialDomException -> {
                logger.d("DOM exception occurred: ${exception.domError::class.simpleName}")
                errorCode = exception.domError::class.simpleName ?: UnknownError::class.simpleName
            }

            is GetPublicKeyCredentialDomException -> {
                logger.d("DOM exception occurred: ${exception.domError::class.simpleName}")
                errorCode = exception.domError::class.simpleName ?: UnknownError::class.simpleName
            }

            else -> {
                logger.d("Unknown error occurred")
                errorCode = UnknownError::class.simpleName
            }
        }
    }
}
