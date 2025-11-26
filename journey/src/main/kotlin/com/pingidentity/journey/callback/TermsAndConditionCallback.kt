/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey.callback

import com.pingidentity.journey.plugin.AbstractCallback
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * A callback for collecting terms and conditions acceptance.
 *
 * @property version The version of the terms and conditions.
 * @property terms The terms and conditions text.
 * @property createDate The date the terms and conditions were created.
 * @property accepted Whether the user accepts the terms and conditions.
 */
class TermsAndConditionsCallback : AbstractCallback() {
    var version: String = ""
        private set

    var terms: String = ""
        private set

    var createDate: String = ""
        private set

    var accepted = false

    override fun init(name: String, value: JsonElement) {
        when (name) {
            "version" -> this.version = value.jsonPrimitive.content
            "terms" -> this.terms = value.jsonPrimitive.content
            "createDate" -> this.createDate = value.jsonPrimitive.content
            else -> {}
        }
    }

    override fun payload() = input(accepted)

}
