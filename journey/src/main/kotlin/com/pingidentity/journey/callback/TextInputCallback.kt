package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class TextInputCallback : AbstractCallback() {

    var prompt: String = ""
        private set

    /**
     * The text to be used as the default text displayed with the prompt.
     */
    var defaultText: String = ""
        private set

    var text: String = ""

    override fun onAttribute(name: String, value: JsonElement) {
        when (name) {
            "prompt" -> prompt = value.jsonPrimitive.content
            "defaultText" -> defaultText = value.jsonPrimitive.content
        }
    }

    override fun asJson() = input(text)

}