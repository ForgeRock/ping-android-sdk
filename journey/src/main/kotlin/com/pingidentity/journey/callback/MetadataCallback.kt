/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.Callback
import com.pingidentity.journey.plugin.CallbackRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A callback for providing metadata.
 *
 * @property value The metadata value.
 */
class MetadataCallback : AbstractCallback() {

    var value: JsonObject = buildJsonObject { }
        private set

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "data" -> this.value = value.jsonObject
        }
    }

    override fun init(jsonObject: JsonObject): Callback {
        super.init(jsonObject)
        if (isProtectInitialize()) {
            return CallbackRegistry.callbacks()["PingOneProtectInitializeCallback"]?.let {
                return it().init(jsonObject)
            } ?: this
        }
        if (isProtectEvaluation()) {
            return CallbackRegistry.callbacks()["PingOneProtectEvaluationCallback"]?.let {
                return it().init(jsonObject)
            } ?: this
        }

        return this
    }

    private fun isFidoRegistration(): Boolean {
        // _action is provided AM version >= AM 7.1
        if (value["_action"]?.jsonPrimitive?.contentOrNull == "webauthn_registration") {
            return true
        }

        // Checking for existence and content of _TYPE and either PUB_KEY_CRED_PARAMS
        // or _PUB_KEY_CRED_PARAMS
        return value["_type"]?.jsonPrimitive?.contentOrNull == "WebAuthn" &&
                (value.containsKey("pubKeyCredParams") || value.containsKey("_pubKeyCredParams"))
    }

    private fun isFidoAuthentication(): Boolean {
        // _action is provided AM version >= AM 7.1
        if (value["_action"]?.jsonPrimitive?.contentOrNull == "webauthn_authentication") {
            return true
        }

        // Checking for existence and content of _TYPE and either PUB_KEY_CRED_PARAMS
        // or _PUB_KEY_CRED_PARAMS
        return value["_type"]?.jsonPrimitive?.contentOrNull == "WebAuthn" &&
                (value.containsKey("pubKeyCredParams") || value.containsKey("_pubKeyCredParams"))
    }

    private fun isProtectInitialize(): Boolean {
        return value["_type"]?.jsonPrimitive?.contentOrNull == "PingOneProtect" &&
                value["_action"]?.jsonPrimitive?.contentOrNull == "protect_initialize"
    }

    private fun isProtectEvaluation(): Boolean {
        return value["_type"]?.jsonPrimitive?.contentOrNull == "PingOneProtect" &&
                value["_action"]?.jsonPrimitive?.contentOrNull == "protect_risk_evaluation"
    }
}