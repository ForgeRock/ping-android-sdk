/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp.journey

import com.pingidentity.journey.plugin.AbstractCallback
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Callback that handles the selection of an external Identity Provider (IdP).
 *
 * This callback is triggered when the user needs to select an authentication method
 * from available external identity providers (such as Google, Facebook, Apple, etc.)
 * during the authentication journey.
 */
class SelectIdpCallback : AbstractCallback() {

    /**
     * List of available identity providers that the user can select from.
     * This list is populated from the callback's JSON data.
     */
    var providers = emptyList<IdPValue>()
        private set

    /**
     * The selected identity provider identifier.
     * This value should be set to the provider ID that the user has chosen.
     */
    var value: String = ""

    /**
     * Initializes the callback with data from the authentication server.
     *
     * @param name The name of the property being initialized
     * @param value The value for the property being initialized
     */
    override fun init(name: String, value: JsonElement) {
        when (name) {
            "providers" -> {
                providers = value.jsonArray.map {
                    IdPValue(it.jsonObject)
                }
            }
        }
    }

    /**
     * Generates a JSON object payload to be sent back to the authentication server.
     * This payload includes the user's selected identity provider.
     *
     * @return A JsonObject containing the selected identity provider
     */
    override fun payload(): JsonObject {
        return input(value)
    }

    /**
     * Represents an identity provider option presented to the user.
     * Contains the provider identifier and UI configuration details.
     *
     * @property provider The identifier of the identity provider
     * @property uiConfig JSON configuration for UI elements related to this provider
     */
    class IdPValue(jsonObject: JsonObject) {
        /**
         * The identifier of the identity provider (e.g., "google", "facebook", "apple").
         */
        var provider: String = jsonObject["provider"]?.jsonPrimitive?.content ?: ""

        /**
         * UI configuration for displaying this provider.
         * May contain elements like icon URLs, display names, colors, etc.
         */
        var uiConfig: JsonObject = jsonObject["uiConfig"]?.jsonObject ?: buildJsonObject {}
    }

}