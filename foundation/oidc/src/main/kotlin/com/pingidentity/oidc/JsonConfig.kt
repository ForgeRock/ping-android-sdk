/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.logger.NONE
import com.pingidentity.logger.STANDARD
import com.pingidentity.logger.WARN
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

object JsonConfigKey {
    // Top level keys - Required
    const val JOURNEY = "journey"
    const val OIDC = "oidc"
    const val LOG = "log"
    const val TIMEOUT = "timeout"
    const val SERVER_URL = "serverUrl"
    const val REALM = "realm"
    const val COOKIE_NAME = "cookieName"
    // OIDC keys - Required
    const val CLIENT_ID = "clientId"
    const val DISCOVERY_ENDPOINT = "discoveryEndpoint"
    const val REDIRECT_URI = "redirectUri"
    const val SCOPES = "scopes"

    // OIDC keys - Optional
    const val PAR = "par"
    const val SIGN_OUT_REDIRECT_URI = "signOutRedirectUri"
    const val REFRESH_THRESHOLD = "refreshThreshold"
    const val LOGIN_HINT = "loginHint"
    const val NONCE = "nonce"
    const val STATE = "state"
    const val DISPLAY = "display"
    const val PROMPT = "prompt"
    const val UI_LOCALES = "uiLocales"
    const val ACR_VALUES = "acrValues"
    const val ADDITIONAL_PARAMETERS = "additionalParameters"

    // OPEN ID
    const val OPEN_ID = "openId"
    const val DEVICE_AUTHORIZATION_ENDPOINT = "deviceAuthorizationEndpoint"
    const val AUTHORIZATION_ENDPOINT = "authorizationEndpoint"
    const val TOKEN_ENDPOINT = "tokenEndpoint"
    const val USER_INFO_ENDPOINT = "userInfoEndpoint"
    const val END_SESSION_ENDPOINT = "endSessionEndpoint"
    const val REVOCATION_ENDPOINT = "revocationEndpoint"
}

/**
 * Exception class for JSON configuration errors.
 *
 * @param message The error message describing the configuration issue.
 */
open class JsonConfigError(message: String) : Exception(message) {
    class MissingRequiredField(val field: String) :
        JsonConfigError("Missing required configuration field: '$field'")

    class InvalidType(val field: String, val expected: String) :
        JsonConfigError("Invalid type for configuration field '$field': expected $expected")
}

/**
 * A helper class for parsing JSON configuration with error handling.
 *
 * @param json The JSON object containing the configuration.
 */
class JsonConfigParser(@PublishedApi internal val json: JsonObject) {

    /**
     * Returns the value for [key] decoded as type [T].
     *
     * @param key The JSON key to look up.
     * @throws JsonConfigError.MissingRequiredField if the key is absent.
     * @throws JsonConfigError.InvalidType if the value cannot be decoded as [T].
     */
    inline fun <reified T> required(key: String): T {
        val element = json[key] ?: throw JsonConfigError.MissingRequiredField(key)
        return runCatching { Json.decodeFromJsonElement<T>(element) }.getOrElse {
            throw JsonConfigError.InvalidType(field = key, expected = T::class.simpleName ?: "unknown")
        }
    }

    /**
     * Returns the value for [key] decoded as type [T], or [default] if the key is absent.
     *
     * @param key The JSON key to look up.
     * @param default The value to return when the key is not present.
     * @throws JsonConfigError.InvalidType if the key is present but the value cannot be decoded as [T].
     */
    inline fun <reified T> optional(key: String, default: T): T {
        val element = json[key] ?: return default
        return runCatching { Json.decodeFromJsonElement<T>(element) }.getOrElse {
            throw JsonConfigError.InvalidType(field = key, expected = T::class.simpleName ?: "unknown")
        }
    }

    /**
     * Parses an optional `Map<String, String>` field from the JSON object.
     *
     * Each value in the object must be a JSON string; if any value is a non-string type an
     * [JsonConfigError.InvalidType] is thrown naming the offending nested key
     * (e.g. `"additionalParameters.badKey"`), matching iOS behaviour.
     *
     * @param key The JSON key for the map field.
     * @return The parsed map, or `null` if the key is absent.
     */
    fun additionalParameters(key: String): Map<String, String>? {
        val element = json[key] ?: return null
        val obj = runCatching { element.jsonObject }.getOrElse {
            throw JsonConfigError.InvalidType(field = key, expected = "JsonObject")
        }
        return obj.entries.associate { (entryKey, entryValue) ->
            if (entryValue !is JsonPrimitive || !entryValue.isString) {
                throw JsonConfigError.InvalidType(
                    field = "$key.$entryKey",
                    expected = "string"
                )
            }
            entryKey to entryValue.content
        }
    }

    /**
     * Parses a scope set from the JSON object, accepting either a comma-separated string or an array of strings.
     * If the field is a string, it is split on commas to create the set. If the field is an array, each element must be a string.
     * If the field is missing, a [JsonConfigError.MissingRequiredField] is thrown. If the field is present but not a string or array of strings, a [JsonConfigError.InvalidType] is thrown.
     * @param key The JSON key for the scopes field.
     * @return A mutable set of scopes.
     */
    fun scopeSet(key: String): MutableSet<String> {
        val element = json[key] ?: throw JsonConfigError.MissingRequiredField(key)
        return when (element) {
            is JsonArray -> runCatching {
                Json.decodeFromJsonElement<List<String>>(element)
            }.getOrElse {
                throw JsonConfigError.InvalidType(field = key, expected = "JsonArray of String")
            }.toMutableSet()
            else -> runCatching {
                Json.decodeFromJsonElement<String>(element)
            }.getOrElse {
                throw JsonConfigError.InvalidType(field = key, expected = "String or JsonArray")
            }.toScopeSet()
        }
    }

    /**
     * Parses a timeout value in milliseconds from the JSON object. If the field is missing, returns the provided default value.
      * If the field is present but not a long integer, a [JsonConfigError.InvalidType] is thrown.
      * @param default The default timeout value to return if the field is missing (default is 15,000 milliseconds).
      * @return The parsed timeout value in milliseconds, or the default if the field is missing.
     */
    fun timeoutMillis(default: Long = 15_000L): Long {
        val element = json[JsonConfigKey.TIMEOUT] ?: return default
        return runCatching { element.jsonPrimitive.long }.getOrElse {
            throw JsonConfigError.InvalidType(field = JsonConfigKey.TIMEOUT, expected = "Long")
        }
    }

    /**
     * Parses a log level from the JSON `log` field.
     *
     * Accepted values (case-insensitive): `"STANDARD"`, `"DEBUG"`, `"WARN"`, `"CONSOLE"`, `"NONE"`.
     *
     * @param default The log level to use when the field is absent. Defaults to `NONE`.
     * @throws JsonConfigError.InvalidType if the field is present but does not match a valid level.
     */
    fun logLevel(default: Logger = Logger.NONE): Logger {
        val levelStr = json[JsonConfigKey.LOG]?.jsonPrimitive?.content ?: return default
        return runCatching {
            when (levelStr.uppercase()) {
                "INFO", "DEBUG", "STANDARD" -> Logger.STANDARD
                "ERROR", "WARN" -> Logger.WARN
                "CONSOLE" -> Logger.CONSOLE
                "NONE" -> Logger.NONE
                else -> throw IllegalArgumentException("Unknown log level: $levelStr")
            }
        }.getOrElse {
            throw JsonConfigError.InvalidType(field = JsonConfigKey.LOG, expected = "STANDARD, WARN, CONSOLE, or NONE")
        }
    }
}

/**
 * Splits a comma-separated scope string into a mutable set of trimmed, non-blank scope values.
 *
 * Example: `"openid, profile, email"` → `mutableSetOf("openid", "profile", "email")`
 */
fun String.toScopeSet(): MutableSet<String> =
    split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()

/**
 * Converts a comma-separated scope string into a [kotlinx.serialization.json.JsonArray] of strings.
 *
 * Used when building a JSON config object where `scopes` must be a JSON array rather than a
 * comma-separated string (e.g. before passing config to a factory function).
 *
 * Example: `"openid, profile"` → `["openid", "profile"]`
 */
fun String.toScopesJsonArray() = buildJsonArray {
    split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
}