/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.rar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingidentity.oidc.AuthorizationDetail
import com.pingidentity.samples.pingsampleapp.config.OidcConfigState
import com.pingidentity.samples.pingsampleapp.config.rarWeb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses the user-edited [jsonText] as an `authorization_details` array (RFC 9396 §2):
 * a JSON array of objects each carrying a non-blank `type`. Returns null when blank
 * (meaning "no per-transaction details — use config-level"), empty, or invalid.
 */
internal fun parseRarJson(jsonText: String): List<AuthorizationDetail>? {
    val trimmed = jsonText.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
        val element: JsonElement = Json.parseToJsonElement(trimmed)
        val array: JsonArray = when (element) {
            is JsonArray -> element
            is JsonObject -> JsonArray(listOf(element)) // single object convenience
            else -> return null
        }
        array.map { entry ->
            val obj = entry as? JsonObject ?: return null
            fun strings(key: String): List<String>? =
                (obj[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !== JsonNull }?.content }
            AuthorizationDetail(
                // JsonNull is a JsonPrimitive whose content is "null" — exclude it explicitly
                type = (obj["type"] as? JsonPrimitive)?.takeIf { it !== JsonNull }
                    ?.content?.takeIf { it.isNotBlank() } ?: return null,
                locations = strings("locations"),
                actions = strings("actions"),
                datatypes = strings("datatypes"),
                privileges = strings("privileges"),
                additionalFields = obj.toMap().filterKeys { it !in setOf("type", "locations", "actions", "datatypes", "privileges") },
            )
        }.takeIf { it.isNotEmpty() } // [] parses to null; callers reject non-blank unparseable input
    }.getOrNull()
}

data class RarLoginState(
    val running: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
)

class RarLoginViewModel : ViewModel() {

    var state = MutableStateFlow(RarLoginState())
        private set

    /** The selected Web config, exposed for the status card (client id, PAR flag, config details). */
    val webConfig: OidcConfigState?
        get() = com.pingidentity.samples.pingsampleapp.config.webConfig

    fun login(jsonText: String) {
        if (webConfig == null) {
            state.update { it.copy(error = "Select a Web config from Configuration first") }
            return
        }
        val perCallDetails = parseRarJson(jsonText)
        if (jsonText.isNotBlank() && perCallDetails == null) {
            state.update { it.copy(error = "Invalid authorization_details JSON") }
            return
        }
        // parseRarJson maps blank text to null: "use config-level details"
        // (the per-call authorizationDetails() call is skipped, so config wins).
        // An empty array [] also parses to null but is rejected above like any
        // other non-blank unparseable input — the screen treats it as invalid.
        viewModelScope.launch {
            state.update { it.copy(running = true, error = null) }
            try {
                val client = rarWeb ?: run {
                    state.update { it.copy(running = false, error = "RAR client not built") }
                    return@launch
                }
                client.authorize {
                    perCallDetails?.let { authorizationDetails(*it.toTypedArray()) }
                }.onSuccess {
                    state.update { s -> s.copy(running = false, success = true) }
                }.onFailure { throwable ->
                    state.update { s ->
                        s.copy(running = false, error = throwable.message ?: "Login failed")
                    }
                }
            } catch (e: CancellationException) {
                throw e // scope cancelled (e.g. user left the screen): propagate, don't surface as error
            } catch (e: Exception) {
                state.update { s -> s.copy(running = false, error = e.message ?: "Login failed") }
            }
        }
    }

    fun reset() {
        state.update { RarLoginState() }
    }
}
