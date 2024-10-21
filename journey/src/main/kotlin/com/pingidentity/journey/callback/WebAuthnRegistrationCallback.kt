package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import kotlinx.serialization.json.JsonElement

class WebAuthnRegistrationCallback: AbstractCallback() {

    override fun onAttribute(name: String, value: JsonElement) {
        TODO("Not yet implemented")
    }
}