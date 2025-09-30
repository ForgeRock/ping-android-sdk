/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.Callback
import com.pingidentity.journey.plugin.CallbackRegistry.callbacks
import com.pingidentity.journey.plugin.Journey
import com.pingidentity.journey.plugin.JourneyAware
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val PING_ONE_PROTECT_INITIALIZE_CALLBACK = "PingOneProtectInitializeCallback"
private const val PING_ONE_PROTECT_EVALUATION_CALLBACK = "PingOneProtectEvaluationCallback"
private const val FIDO_2_REGISTRATION_CALLBACK = "Fido2RegistrationCallback"
private const val FIDO_2_AUTHENTICATION_CALLBACK = "Fido2AuthenticationCallback"

private const val ACTION = "_action"
private const val TYPE = "_type"
private const val WEBAUTHN_REGISTRATION = "webauthn_registration"
private const val WEB_AUTHN = "WebAuthn"
private const val _PUB_KEY_CRED_PARAMS = "_pubKeyCredParams"
private const val PUB_KEY_CRED_PARAMS = "pubKeyCredParams"
private const val WEBAUTHN_AUTHENTICATION = "webauthn_authentication"
private const val PING_ONE_PROTECT = "PingOneProtect"
private const val PROTECT_INITIALIZE = "protect_initialize"
private const val PROTECT_RISK_EVALUATION = "protect_risk_evaluation"
private const val ALLOW_CREDENTIALS = "allowCredentials"
private const val _ALLOW_CREDENTIALS = "_allowCredentials"

/**
 * A callback for providing metadata.
 *
 * @property value The metadata value.
 */
class MetadataCallback : AbstractCallback(), JourneyAware {

    /**
     * Reference to the parent journey.
     * Required by the [JourneyAware] interface.
     */
    override lateinit var journey: Journey

    private val logger by lazy { journey.config.logger }

    var value: JsonObject = buildJsonObject { }
        private set

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "data" -> this.value = value.jsonObject
        }
    }

    override fun init(jsonObject: JsonObject): Callback {
        super.init(jsonObject)
        when {
            isProtectInitialize() -> {
                return callbacks()[PING_ONE_PROTECT_INITIALIZE_CALLBACK]?.let {
                    inject(journey, jsonObject, it)
                } ?: run {
                    logger.w("Callback not found for $PING_ONE_PROTECT_INITIALIZE_CALLBACK")
                    this
                }
            }

            isProtectEvaluation() -> {
                return callbacks()[PING_ONE_PROTECT_EVALUATION_CALLBACK]?.let {
                    inject(journey, jsonObject, it)
                } ?: run {
                    logger.w("Callback not found for $PING_ONE_PROTECT_EVALUATION_CALLBACK")
                    this
                }
            }

            isFidoRegistration() -> return callbacks()[FIDO_2_REGISTRATION_CALLBACK]?.let {
                inject(journey, jsonObject, it)
            } ?: run {
                logger.w("Callback not found for $FIDO_2_REGISTRATION_CALLBACK")
                this
            }

            isFidoAuthentication() -> return callbacks()[FIDO_2_AUTHENTICATION_CALLBACK]?.let {
                inject(journey, jsonObject, it)
            } ?: run {
                logger.w("Callback not found for $FIDO_2_AUTHENTICATION_CALLBACK")
                this
            }

            else -> return this
        }
    }

    private fun inject(journey: Journey, jsonObject: JsonObject, block: () -> Callback): Callback {
        return block().let {
            if (it is JourneyAware) {
                it.journey = journey
            }
            it.init(jsonObject)
        }
    }

    private fun isFidoRegistration(): Boolean {
        // _action is provided AM version >= AM 7.1
        if (value[ACTION]?.jsonPrimitive?.contentOrNull == WEBAUTHN_REGISTRATION) {
            return true
        }

        // Checking for existence and content of _TYPE and either PUB_KEY_CRED_PARAMS
        // or _PUB_KEY_CRED_PARAMS
        return value[TYPE]?.jsonPrimitive?.contentOrNull == WEB_AUTHN &&
                (value.containsKey(PUB_KEY_CRED_PARAMS) || value.containsKey(_PUB_KEY_CRED_PARAMS))
    }

    private fun isFidoAuthentication(): Boolean {
        // _action is provided AM version >= AM 7.1
        if (value[ACTION]?.jsonPrimitive?.contentOrNull == WEBAUTHN_AUTHENTICATION) {
            return true
        }
        // Checking for existence and content of _TYPE and not with PUB_KEY_CRED_PARAMS
        // and _PUB_KEY_CRED_PARAMS
        return value[TYPE]?.jsonPrimitive?.contentOrNull == WEB_AUTHN &&
                (value.containsKey(ALLOW_CREDENTIALS) || value.containsKey(_ALLOW_CREDENTIALS))
    }

    private fun isProtectInitialize(): Boolean {
        return value[TYPE]?.jsonPrimitive?.contentOrNull == PING_ONE_PROTECT &&
                value[ACTION]?.jsonPrimitive?.contentOrNull == PROTECT_INITIALIZE
    }

    private fun isProtectEvaluation(): Boolean {
        return value[TYPE]?.jsonPrimitive?.contentOrNull == PING_ONE_PROTECT &&
                value[ACTION]?.jsonPrimitive?.contentOrNull == PROTECT_RISK_EVALUATION
    }
}