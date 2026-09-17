/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * A callback for collecting a name.
 *
 * @property prompt The prompt for the name.
 * @property name The name input by the user.
 * @property autocompleteValues The autocomplete hints for the field, e.g.
 * `["username", "webauthn"]` when the server marks this field as the target for
 * conditional-mediation passkey suggestions (autofill with passkeys).
 */
class NameCallback : AbstractCallback() {
    var prompt: String = ""
        private set

    var autocompleteValues: List<String> = emptyList()
        private set

    //Input
    var name: String = ""

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "prompt" -> this.prompt = value.jsonPrimitive.content
            "autocompleteValues" ->
                this.autocompleteValues = (value as? JsonArray)
                    ?.map { it.jsonPrimitive.content }
                    .orEmpty()
        }
    }

    override fun payload() = input(name)

}