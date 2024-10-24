package com.pingidentity.journey.callback

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

abstract class AttributeInputCallback : AbstractValidatedCallback() {
    var prompt = ""
        private set
    var name = ""
        private set
    var required = false
        private set

    override fun onAttribute(name: String, value: JsonElement) {
        super.onAttribute(name, value)
        when (name) {
            "name" -> this.name = value.jsonPrimitive.content
            "prompt" -> this.prompt = value.jsonPrimitive.content
            "required" -> this.required = value.jsonPrimitive.boolean
        }
    }
}