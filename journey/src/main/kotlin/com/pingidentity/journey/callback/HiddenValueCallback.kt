package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class HiddenValueCallback : AbstractCallback() {

    var id = ""
        private set
    var defaultValue = ""
        private set

    var value = ""
        private set

    var inputValue = ""

    override fun onAttribute(name: String, value: JsonElement) {
        when (name) {
            "value" -> this.value = value.jsonPrimitive.content
            "id" -> this.id = value.jsonPrimitive.content
            "defaultValue" -> this.defaultValue = value.jsonPrimitive.content
        }
    }

    override fun asJson() = input(inputValue)

}