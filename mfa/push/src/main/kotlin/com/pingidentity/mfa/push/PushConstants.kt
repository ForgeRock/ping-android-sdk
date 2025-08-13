/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

/**
 * Constants used by Push module.
 * Contains constants for HTTP headers, content types, keys used in requests/responses,
 * and JWT-related constants.
 */
object PushConstants {

    // Constants for HTTP headers and content types
    const val ACCEPT_API_VERSION = "resource=1.0, protocol=1.0"
    const val APPLICATION_JSON = "application/json"
    const val HEADER_CONTENT_TYPE = "Content-Type"
    const val HEADER_ACCEPT_API_VERSION = "Accept-API-Version"
    const val HEADER_COOKIE = "Cookie"

    // Actions for push authentication
    const val ACTION_REGISTER = "pushauth_register"
    const val ACTION_AUTH = "pushauth_authenticate"
    const val ACTION_UPDATE = "pushauth_update"

    // Keys and values used in the request and response payloads
    const val KEY_MESSAGE_ID = "messageId"
    const val KEY_MECHANISM_UID = "mechanismUid"
    const val KEY_USERNAME = "username"
    const val KEY_RESPONSE = "response"
    const val KEY_DENY = "deny"
    const val KEY_CHALLENGE_RESPONSE = "challengeResponse"
    const val KEY_COMMUNICATION_TYPE = "communicationType"
    const val KEY_DEVICE_ID = "deviceId"
    const val KEY_DEVICE_NAME = "deviceName"
    const val KEY_DEVICE_TOKEN = "deviceToken"
    const val KEY_DEVICE_TYPE = "deviceType"
    const val KEY_ACTION = "action"
    const val KEY_ALMB_COOKIE = "amlbCookie"
    const val KEY_CHALLENGE = "challenge"
    const val KEY_JWT = "jwt"
    const val KEY_PAYLOAD = "payload"
    const val ANDROID = "android"
    const val GCM = "gcm"
    const val DEFAULT_DEVICE_NAME = "Android Device"
    const val KEY_TTL = "ttl"
    const val KEY_AMLB_COOKIE = "amlbCookie"
    const val KEY_MESSAGE = "message"
    const val KEY_CUSTOM_PAYLOAD = "customPayload"
    const val KEY_RAW_JWT = "rawJwt"
    const val KEY_CONTEXT_INFO = "contextInfo"
    const val KEY_NUMBERS_CHALLENGE = "numbersChallenge"
    const val KEY_MESSAGE_TEXT = "messageText"
    const val KEY_CREDENTIAL_ID = "credentialId"
    const val KEY_PUSH_TYPE = "pushType"
    const val KEY_USER_ID = "userId"
    const val KEY_ADDITIONAL_DATA = "additionalData"
    const val KEY_TIME_INTERVAL = "timeInterval"
    
    // JWT related constants
    const val JWT_ALGORITHM = "HmacSHA256"
    const val JWT_ALG = "alg"
    const val JWT_TYP = "typ"
    const val JWT_IAT = "iat"
    const val JWT_JTI = "jti"
    const val JWT_ALG_HS256 = "HS256"
    const val JWT_TYP_VALUE = "JWT"

    // Default time-to-live for push notifications in seconds
    const val DEFAULT_TTL_SECONDS = 120;

}