/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel

val headers = headersOf(HttpHeaders.ContentType, "application/json")

fun openIdConfigurationResponse() =
    ByteReadChannel(
        """
        {
          "authorization_endpoint" : "http://auth.test-one-pingone.com/authorize",
          "token_endpoint" : "https://auth.test-one-pingone.com/access_token",
          "userinfo_endpoint" : "https://auth.test-one-pingone.com/userinfo",
          "end_session_endpoint" : "https://auth.test-one-pingone.com/signoff",
          "revocation_endpoint" : "https://auth.test-one-pingone.com/revoke"
        }
        """,
    )

fun tokeResponse() =
    ByteReadChannel(
        """
        {
          "access_token" : "Dummy AccessToken",
          "token_type" : "Dummy Token Type",
          "scope" : "openid email address",
          "refresh_token" : "Dummy RefreshToken",
          "expires_in" : 1,
          "id_token" : "Dummy IdToken"
        }
        """,
    )

fun userinfoResponse() =
    ByteReadChannel(
        """
        {
          "sub" : "test-sub",
          "name" : "test-name",
          "email" : "test-email",
          "phone_number" : "test-phone_number",
          "address" : "test-address"
        }
        """,
    )

fun revokeResponse() = ByteReadChannel("")

val authorizeResponseHeaders = headers {
    append(
        "location",
        "org.forgerock.demo:/oauth2redirect?code=Ivdz6QgAAE-45UdTOSs6WYtvpIg&iss=https%3A%2F%2Fopenam-sdks.forgeblocks.com%3A443%2Fam%2Foauth2%2Falpha&client_id=AndroidTest"
    )
}

val authenticateHeader = headers {
    append("Content-Type", "application/json; charset=utf-8")
}

fun authenticate() = ByteReadChannel(
    """{
    "authId": "authIdValue",
    "callbacks": [
        {
            "type": "NameCallback",
            "output": [
                {
                    "name": "prompt",
                    "value": "User Name"
                }
            ],
            "input": [
                {
                    "name": "IDToken1",
                    "value": ""
                }
            ],
            "_id": 0
        },
        {
            "type": "PasswordCallback",
            "output": [
                {
                    "name": "prompt",
                    "value": "Password"
                }
            ],
            "input": [
                {
                    "name": "IDToken2",
                    "value": ""
                }
            ],
            "_id": 1
        }
    ]
}"""
)

fun sessionResponse() =
    ByteReadChannel(
        """{"tokenId":"Dummy Session Token","successUrl":"/enduser/?realm=/alpha","realm":"/alpha"}"""
    )

fun tokeErrorResponse() =
    ByteReadChannel(
        "{\n" +
                "  \"error\" : \"Invalid Grant\"\n" +
                "}",
    )
