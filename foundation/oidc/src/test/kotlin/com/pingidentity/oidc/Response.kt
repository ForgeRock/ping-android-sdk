/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel

val headers = headersOf(HttpHeaders.ContentType, "application/json")

fun openIdConfigurationResponse() =
    ByteReadChannel(
        "{\n" +
            "  \"authorization_endpoint\" : \"http://auth.test-one-pingone.com/authorize\",\n" +
            "  \"token_endpoint\" : \"https://auth.test-one-pingone.com/token\",\n" +
            "  \"userinfo_endpoint\" : \"https://auth.test-one-pingone.com/userinfo\",\n" +
            "  \"end_session_endpoint\" : \"https://auth.test-one-pingone.com/signoff\",\n" +
            "  \"ping_end_idp_session_endpoint\" : \"https://auth.test-one-pingone.com/idp/signoff\",\n" +
            "  \"revocation_endpoint\" : \"https://auth.test-one-pingone.com/revoke\"\n" +
            "}",
    )

fun tokeResponse() =
    ByteReadChannel(
        "{\n" +
            "  \"access_token\" : \"Dummy AccessToken\",\n" +
            "  \"token_type\" : \"Dummy Token Type\",\n" +
            "  \"scope\" : \"openid email address\",\n" +
            "  \"refresh_token\" : \"Dummy RefreshToken\",\n" +
            "  \"expires_in\" : 1,\n" +
            "  \"id_token\" : \"Dummy IdToken\"\n" +
            "}",
    )

fun userinfoResponse() =
    ByteReadChannel(
        "{\n" +
            "  \"sub\" : \"test-sub\",\n" +
            "  \"name\" : \"test-name\",\n" +
            "  \"email\" : \"test-email\",\n" +
            "  \"phone_number\" : \"test-phone_number\",\n" +
            "  \"address\" : \"test-address\"\n" +
            "}",
    )

fun tokeErrorResponse() =
    ByteReadChannel(
        "{\n" +
            "  \"error\" : \"Invalid Grant\"\n" +
            "}",
    )
