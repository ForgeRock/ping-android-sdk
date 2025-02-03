/*
 * Copyright (c) 2024 - 2025 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import io.ktor.http.HttpHeaders
import io.ktor.http.headers
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

fun revokeResponse() = ByteReadChannel("")

val authorizeResponseHeaders = headers {
    append("Content-Type", "application/json; charset=utf-8")
    append(
        "Set-Cookie",
        "interactionId=038e8128-272a-4a15-b97b-379aa1447149; Max-Age=3600; Path=/; Expires=Wed, 27 Mar 9999 05:06:30 GMT; HttpOnly",
    )
    append(
        "Set-Cookie",
        " interactionToken=71c65504463355679fd247900441c36afb6be6c00d45aa169500b7cd753894d46d68feb4952ff0843ff4b287220a66cb3d58a3bc41e71724f111b034d0458aac8a5153859ed96825ef8c6a6400e7ae9de82a7353fc3c9886ba835853db8c0957ea4cd0a52d20d4fb50b4419dc9df33a53889f52abeb04f517b6c7c8efb0b58f0; Max-Age=3600; Path=/; Expires=Wed, 27 Mar 9999 05:06:30 GMT; HttpOnly",
    )
    append(
        "Set-Cookie",
        "skProxyApiEnvironmentId=us-west-2; Max-Age=900; Path=/; Expires=Wed, 27 Mar 9999 04:21:30 GMT; HttpOnly",
    )
}

fun authorizeResponse() =
    ByteReadChannel(
        "{\n" +
                "    \"_links\": {\n" +
                "        \"next\": {\n" +
                "            \"href\": \"http://auth.test-one-pingone.com/customHTMLTemplate\"\n" +
                "        }\n" +
                "    },\n" +
                "    \"interactionId\": \"008bccea-914b-49da-b2a1-5cd3f83f4372\",\n" +
                "    \"interactionToken\": \"2a0d9bcdbdeb5ea14ef34d680afc45f37a56e190e306a778f01d768b271bf1e976aaf4154b633381e1299b684d3a4a66d3e1c6d419a7d20657bf4f32c741d78f67d41e08eb0e5f1070edf780809b4ccea8830866bcedb388d8f5de13e89454d353bcca86d4dcd5d7872efc929f7e5199d8d127d1b2b45499c42856ce785d8664\",\n" +
                "    \"eventName\": \"continue\",\n" +
                "    \"isResponseCompatibleWithMobileAndWebSdks\": true,\n" +
                "    \"id\": \"cq77vwelou\",\n" +
                "    \"companyId\": \"0c6851ed-0f12-4c9a-a174-9b1bf8b438ae\",\n" +
                "    \"flowId\": \"ebac77c8fbf68d3dac68c5dd804a936f\",\n" +
                "    \"connectionId\": \"867ed4363b2bc21c860085ad2baa817d\",\n" +
                "    \"capabilityName\": \"customHTMLTemplate\",\n" +
                "    \"formData\": {\n" +
                "        \"value\": {\n" +
                "            \"username\": \"\",\n" +
                "            \"password\": \"\"\n" +
                "        }\n" +
                "    },\n" +
                "    \"form\": {\n" +
                "        \"name\": \"Username/Password Form\",\n" +
                "        \"description\": \"Test Description\",\n" +
                "        \"category\": \"CUSTOM_HTML\",\n" +
                "        \"components\": {\n" +
                "            \"fields\": [\n" +
                "                {\n" +
                "                    \"type\": \"TEXT\",\n" +
                "                    \"key\": \"username\",\n" +
                "                    \"label\": \"Username\"\n" +
                "                },\n" +
                "                {\n" +
                "                    \"type\": \"PASSWORD\",\n" +
                "                    \"key\": \"password\",\n" +
                "                    \"label\": \"Password\"\n" +
                "                },\n" +
                "                {\n" +
                "                    \"type\": \"SUBMIT_BUTTON\",\n" +
                "                    \"key\": \"SIGNON\",\n" +
                "                    \"label\": \"Sign On\"\n" +
                "                },\n" +
                "                {\n" +
                "                    \"type\": \"FLOW_BUTTON\",\n" +
                "                    \"key\": \"TROUBLE\",\n" +
                "                    \"label\": \"Having trouble signing on?\",\n" +
                "                    \"inputType\": \"ACTION\"" +
                "                },\n" +
                "                {\n" +
                "                    \"type\": \"FLOW_BUTTON\",\n" +
                "                    \"key\": \"REGISTER\",\n" +
                "                    \"label\": \"No account? Register now!\",\n" +
                "                    \"inputType\": \"ACTION\"" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}"
    )

val customHTMLTemplateHeaders = headers {
    append("Content-Type", "application/json; charset=utf-8")
    append(
        "Set-Cookie",
        " ST=session_token; Max-Age=3600; Path=/; Expires=Wed, 27 Mar 9999 05:06:30 GMT; HttpOnly"
    )
}

fun customHTMLTemplate() = ByteReadChannel(
    "{\n" +
            "    \"interactionId\": \"033e1338-c271-4dd7-8d74-fc2eacc135d8\",\n" +
            "    \"companyId\": \"94e3268d-847d-47aa-a45e-1ef8dd8f4df0\",\n" +
            "    \"connectionId\": \"26146c8065741406afb0899484e361a7\",\n" +
            "    \"connectorId\": \"pingOneAuthenticationConnector\",\n" +
            "    \"id\": \"5dtrjnrwox\",\n" +
            "    \"capabilityName\": \"returnSuccessResponseRedirect\",\n" +
            "    \"environment\": {\n" +
            "        \"id\": \"94e3268d-847d-47aa-a45e-1ef8dd8f4df0\"\n" +
            "    },\n" +
            "    \"session\": {\n" +
            "        \"id\": \"d0598645-c2f7-4b94-adc9-401a896eaffb\"\n" +
            "    },\n" +
            "    \"status\": \"COMPLETED\",\n" +
            "    \"authorizeResponse\": {\n" +
            "        \"code\": \"03dbd5a2-db72-437c-8728-fc33b860083c\"\n" +
            "    },\n" +
            "    \"success\": true,\n" +
            "    \"interactionToken\": \"5ad09feac8982d668c5f07d1eaf544bdf2309247146999c0139f7ebb955c24743b97a01e3bf67360121cd85d7a9e1d966c3f4b7e27f21206a5304d305951864cc34a37900f3326f8000c7bc731af9ba78a681eb14d4bf767172e8a7149e4df3e054b4245bdea5612e9ec0c0d8cb349b55dcf10db30de075dfc79f6c765046d99\"\n" +
            "}"
)

fun customHTMLTemplateWithInvalidPassword() = ByteReadChannel(
    "{\n" +
            "    \"interactionId\": \"00444ecd-0901-4b57-acc3-e1245971205b\",\n" +
            "    \"companyId\": \"0c6851ed-0f12-4c9a-a174-9b1bf8b438ae\",\n" +
            "    \"connectionId\": \"94141bf2f1b9b59a5f5365ff135e02bb\",\n" +
            "    \"connectorId\": \"pingOneSSOConnector\",\n" +
            "    \"id\": \"dnu7jt3sjz\",\n" +
            "    \"capabilityName\": \"checkPassword\",\n" +
            "    \"errorCategory\": \"NotSet\",\n" +
            "    \"code\": \" Invalid username and/or password\",\n" +
            "    \"cause\": null,\n" +
            "    \"expected\": true,\n" +
            "    \"message\": \" Invalid username and/or password\",\n" +
            "    \"httpResponseCode\": 400,\n" +
            "    \"details\": [\n" +
            "        {\n" +
            "            \"rawResponse\": {\n" +
            "                \"id\": \"b187c1c7-e9fe-4f72-a554-1b2876babafe\",\n" +
            "                \"code\": \"INVALID_DATA\",\n" +
            "                \"message\": \"The request could not be completed. One or more validation errors were in the request.\",\n" +
            "                \"details\": [\n" +
            "                    {\n" +
            "                        \"code\": \"INVALID_VALUE\",\n" +
            "                        \"target\": \"password\",\n" +
            "                        \"message\": \"The provided password did not match provisioned password\",\n" +
            "                        \"innerError\": {\n" +
            "                            \"failuresRemaining\": 4\n" +
            "                        }\n" +
            "                    }\n" +
            "                ]\n" +
            "            },\n" +
            "            \"statusCode\": 400\n" +
            "        }\n" +
            "    ],\n" +
            "    \"isResponseCompatibleWithMobileAndWebSdks\": true,\n" +
            "    \"correlationId\": \"b187c1c7-e9fe-4f72-a554-1b2876babafe\"\n" +
            "}"
)


fun tokeErrorResponse() =
    ByteReadChannel(
        "{\n" +
                "  \"error\" : \"Invalid Grant\"\n" +
                "}",
    )
