/*
 * Copyright (c) 2025 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.recognize.journey

import com.pingidentity.journey.plugin.AbstractCallback
import com.pingidentity.journey.plugin.Callback
import com.pingidentity.journey.plugin.ValueCallback
import com.pingidentity.journey.plugin.callbacks
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.orchestrate.ContinueNodeAware
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Callback value-callback ID fragment used to identify a client-error field. */
const val CLIENT_ERROR = "clientError"

/** Callback value-callback ID fragment that matches the Keyless authentication result field. */
const val KEYLESS_AUTHENTICATION = "keylessAuthentication"

/** Callback value-callback ID fragment that matches the Keyless enrollment result field. */
const val KEYLESS_ENROLLMENT = "keylessEnrolment"

/**
 * Abstract base class for PingOne Recognize callbacks in Journey workflows.
 *
 * Parses all output fields common to both [PingOneRecognizeEnrollCallback] and
 * [PingOneRecognizeAuthenticateCallback] from the server JSON and exposes them as
 * read-only public properties. Subclasses only need to implement their specific
 * operation (enroll / authenticate) on top of these already-populated fields.
 *
 * The class handles two server JSON shapes transparently:
 * - **`PingOneRecognizeCallback`** — standard Journey callback with an `output` array
 *   containing named fields (delegated to [AbstractCallback.init]).
 * - **`MetadataCallback`** — a `data` envelope inside the first `output` entry, used
 *   when the Journey node wraps its payload in a metadata callback.
 *
 * Input submission is routed through [input] (standard callback) or [valueCallbackSignal] /
 * [valueCallbackError] (derived MetadataCallback) depending on how the callback arrived.
 *
 * @see RecognizeCallback
 * @see PingOneRecognizeEnrollCallback
 * @see PingOneRecognizeAuthenticateCallback
 */
abstract class AbstractRecognizeCallback : ContinueNodeAware, AbstractCallback() {

    override lateinit var continueNode: ContinueNode

    /**
     * `true` when this callback was received as a `MetadataCallback` envelope rather than a
     * direct `PingOneRecognizeCallback`. Affects which submission path ([valueCallbackSignal] /
     * [valueCallbackError] vs [input]) is used when reporting results back to the Journey.
     */
    private var derivedCallback: Boolean = false

    // ── Common output fields ──────────────────────────────────────────────────

    /** URL of the PingOne Recognize authentication service WebSocket. */
    var websocketURL: String = ""
        private set

    /** Customer / tenant name configured on the server. */
    var customerName: String = ""
        private set

    /** RSA public key used to encrypt images before transmission. */
    var imageEncryptionPublicKey: String = ""
        private set

    /** Key ID that corresponds to [imageEncryptionPublicKey]. */
    var imageEncryptionKeyId: String = ""
        private set

    /** PingOne Recognize node host URL. */
    var host: String = ""
        private set

    /** API key for the Keyless / Recognize SDK. */
    var apiKey: String = ""
        private set

    /** Username of the subject. */
    var username: String = ""
        private set

    /** Opaque transaction data to be signed by the SDK. */
    var transactionData: String = ""
        private set

    /** Indicates whether the SDK should generate a new client state. */
    var generateClientState: String = ""
        private set

    /** Existing client state payload supplied by the server. */
    var clientState: String = ""
        private set

    /**
     * Options forwarded to the PingOne Recognize **mobile** SDK.
     *
     * Relevant keys include `livenessConfiguration`, `operationInfoId`,
     * `operationInfoPayload`, `customSecret`, `presentation`, etc.
     */
    var mobileSDKOptions: JsonObject = JsonObject(emptyMap())
        private set

    /**
     * Options intended for the PingOne Recognize **web** SDK (ignored on Android).
     *
     * Provided here for completeness; mobile callers need not inspect this value.
     */
    var webSDKOptions: JsonObject = JsonObject(emptyMap())
        private set

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * Entry point called by the Journey framework with the raw callback JSON.
     *
     * Detects whether the JSON represents a `MetadataCallback` envelope (server wraps the
     * Recognize payload inside a `data` object) or a standard callback, and delegates
     * accordingly. Sets [derivedCallback] so that result submission uses the correct path.
     *
     * @param jsonObject Full callback JSON object from the server.
     * @return This callback instance, fully initialised and ready for use.
     */
    override fun init(jsonObject: JsonObject): Callback {
        val type = jsonObject["type"]?.jsonPrimitive?.content
        if (type == "MetadataCallback") {
            json = jsonObject
            derivedCallback = true
            jsonObject["output"]?.jsonArray?.let { output ->
                output[0].jsonObject.let { nameValuePair ->
                    if (nameValuePair["name"]?.jsonPrimitive?.contentOrNull == "data") {
                        nameValuePair["value"]?.jsonObject?.let { value ->
                            value.forEach { attr ->
                                init(attr.key, attr.value)
                            }
                        }
                    }
                }
            }
            return this
        } else {
            return super.init(jsonObject)
        }
    }

    /**
     * Parses a single named output field and stores it in the corresponding property.
     *
     * Called once per field in the server JSON output array (or per key in the `data` envelope).
     * Subclasses may override this and call `super.init(name, value)` to handle any
     * operation-specific fields in addition to these common ones.
     *
     * @param name Field name as returned by the server (e.g. `"transactionData"`).
     * @param value Raw JSON element for the field value.
     */
    override fun init(name: String, value: JsonElement) {
        when (name) {
            "websocketURL" -> websocketURL = value.jsonPrimitive.content
            "customerName" -> customerName = value.jsonPrimitive.content
            "imageEncryptionPublicKey" -> imageEncryptionPublicKey = value.jsonPrimitive.content
            "imageEncryptionKeyId" -> imageEncryptionKeyId = value.jsonPrimitive.content
            "host" -> host = value.jsonPrimitive.content
            "apiKey" -> apiKey = value.jsonPrimitive.content
            "username" -> username = value.jsonPrimitive.content
            "transactionData" -> transactionData = value.jsonPrimitive.content
            "generateClientState" -> generateClientState = value.jsonPrimitive.content
            "clientState" -> clientState = value.jsonPrimitive.content
            "mobileSDKOptions" -> if (value is JsonObject) mobileSDKOptions = value
            "webSDKOptions" -> if (value is JsonObject) webSDKOptions = value //May be we can ignore this for native
            else -> {}
        }
    }

    // ── Input submission helpers ──────────────────────────────────────────────

    /**
     * Reports a client-side error back to the Journey server.
     *
     * Routes the value to the appropriate input field depending on whether this callback
     * arrived as a `MetadataCallback` envelope ([derivedCallback] `= true`) or a standard
     * `PingOneRecognizeCallback`.
     *
     * @param value Human-readable error description or error type code.
     */
    fun error(value: String) {
        if (derivedCallback) {
            valueCallbackError(value)
        } else {
            input(value)
        }
    }

    /**
     * Submits the signed JWT and an optional error string back to the Journey server.
     *
     * Routes each non-empty value to the matching [ValueCallback] in [continueNode] when
     * this is a derived (MetadataCallback) flow, or to [input] for a standard callback.
     *
     * @param value The signed JWT produced by the Keyless SDK, or empty on failure.
     * @param error Error message to submit, or empty on success.
     */
    fun value(value: String, error: String) {
        if (derivedCallback) {
            if (value.isNotEmpty()) valueCallbackSignal(value)
            if (error.isNotEmpty()) valueCallbackError(error)
        } else {
            input(value, error)
        }
    }

    /**
     * Sets the signed-JWT result on any [ValueCallback] whose ID contains [KEYLESS_AUTHENTICATION]
     * or [KEYLESS_ENROLLMENT], used when the callback is a MetadataCallback envelope.
     */
    private fun valueCallbackSignal(value: String) {
        continueNode.callbacks.forEach { callback ->
            if (callback is ValueCallback) {
                if (callback.id.contains(KEYLESS_AUTHENTICATION) || callback.id.contains(KEYLESS_ENROLLMENT)) {
                    callback.value = value
                }
            }
        }
    }

    /**
     * Sets an error value on any [ValueCallback] whose ID contains [CLIENT_ERROR],
     * used when the callback is a MetadataCallback envelope.
     */
    private fun valueCallbackError(value: String) {
        continueNode.callbacks.forEach { callback ->
            if (callback is ValueCallback) {
                if (callback.id.contains(CLIENT_ERROR)) {
                    callback.value = value
                }
            }
        }
    }
}