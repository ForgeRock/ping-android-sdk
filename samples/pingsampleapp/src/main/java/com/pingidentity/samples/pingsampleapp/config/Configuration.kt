/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The type of SDK flow a [Configuration] targets.
 *
 * The [SerialName] values match the iOS `ConfigType` string encoding so the same
 * `ping_sdk_config.json` file works on both platforms.
 */
@Serializable
enum class ConfigType {
    @SerialName("Journey")
    JOURNEY,

    @SerialName("DaVinci")
    DAVINCI,

    @SerialName("OIDC (Web)")
    OIDC_WEB,

    @SerialName("Device Flow")
    DEVICE,
}

/**
 * A single SDK environment configuration entry.
 *
 * Instances are parsed from `assets/ping_sdk_config.json` (bundled defaults) or from
 * the `user_configurations` DataStore key (user-added configs). The JSON schema is
 * identical on iOS and Android.
 *
 * @param name Unique display name; used as the persistence key for per-type selection.
 * @param type The SDK flow this configuration targets.
 * @param clientId OAuth 2.0 / OIDC public client ID.
 * @param scopes OAuth 2.0 scopes to request. Must be non-empty.
 * @param redirectUri Redirect URI registered with the authorization server.
 * @param signOutUri Optional sign-out redirect URI. Defaults to [redirectUri] in the SDK.
 * @param discoveryEndpoint OIDC discovery endpoint URL. Must be parseable as a URI with a scheme.
 * @param deviceAuthorizationEndpoint Optional Device Authorization endpoint override
 *   (Device Flow only). Mapped via `OidcClientConfig.openIdOverride`.
 * @param environment Free-form label for the server environment (e.g. "AIC", "PingOne").
 * @param cookieName Session cookie name. Required when [type] is [ConfigType.JOURNEY].
 * @param serverUrl AM server base URL. Required when [type] is [ConfigType.JOURNEY].
 * @param realm AM realm. Optional for Journey; defaults to "root" inside the SDK.
 * @param acrValues ACR values hint. Used for DaVinci and OIDC Web flows.
 * @param par Whether to use Pushed Authorization Requests (RFC 9126). Defaults to `false` in SDK.
 */
@Serializable
data class Configuration(
    val name: String,
    val type: ConfigType,
    val clientId: String,
    val scopes: List<String>,
    val redirectUri: String,
    val signOutUri: String? = null,
    val discoveryEndpoint: String,
    val deviceAuthorizationEndpoint: String? = null,
    val environment: String,
    val cookieName: String? = null,
    val serverUrl: String? = null,
    val realm: String? = null,
    val acrValues: String? = null,
    val par: Boolean? = null,
)
