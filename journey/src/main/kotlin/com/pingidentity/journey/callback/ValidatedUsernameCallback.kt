package com.pingidentity.journey.callback

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class ValidatedUsernameCallback : AbstractValidatedCallback() {
    var prompt: String = ""
        private set

    var username: String = ""

    override fun onAttribute(name: String, value: JsonElement) {
        super.onAttribute(name, value)
        when (name) {
            "prompt" -> this.prompt = value.jsonPrimitive.content
        }
    }

    override fun asJson() = input(username, inputValidateOnly)

}