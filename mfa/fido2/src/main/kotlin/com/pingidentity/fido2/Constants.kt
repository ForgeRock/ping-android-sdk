/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.fido2

/**
 * Constants used throughout the FIDO2 module.
 */
object Constants {
    // Actions
    const val ACTION_REGISTER = "REGISTER"
    const val ACTION_AUTHENTICATE = "AUTHENTICATE"

    // Event Types
    const val EVENT_TYPE_SUBMIT = "submit"

    // JSON Fields
    const val FIELD_KEY = "key"
    const val FIELD_LABEL = "label"
    const val FIELD_TRIGGER = "trigger"
    const val FIELD_REQUIRED = "required"
    const val FIELD_DATA = "data"
    const val FIELD_ACTION = "action"
    const val FIELD_RESPONSE = "response"
    const val FIELD_RAW_ID = "rawId"
    const val FIELD_CLIENT_DATA_JSON = "clientDataJSON"
    const val FIELD_ATTESTATION_OBJECT = "attestationObject"
    const val FIELD_AUTHENTICATOR_DATA = "authenticatorData"
    const val FIELD_SIGNATURE = "signature"
    const val FIELD_USER_HANDLE = "userHandle"
    const val FIELD_CHALLENGE = "challenge"
    const val FIELD_TIMEOUT = "timeout"
    const val FIELD_USER_VERIFICATION = "userVerification"
    const val FIELD_RP_ID = "rpId"
    const val FIELD_ALLOW_CREDENTIALS = "allowCredentials"
    const val FIELD_ATTESTATION = "attestation"
    const val FIELD_RP = "rp"
    const val FIELD_USER = "user"
    const val FIELD_PUB_KEY_CRED_PARAMS = "pubKeyCredParams"
    const val FIELD_EXCLUDE_CREDENTIALS = "excludeCredentials"
    const val FIELD_AUTHENTICATOR_SELECTION = "authenticatorSelection"
    const val FIELD_TYPE = "type"
    const val FIELD_ID = "id"
    const val FIELD_ALG = "alg"
    const val FIELD_NAME = "name"
    const val FIELD_DISPLAY_NAME = "displayName"
    const val FIELD_AUTHENTICATOR_ATTACHMENT = "authenticatorAttachment"
    const val FIELD_REQUIRE_RESIDENT_KEY = "requireResidentKey"
    const val FIELD_RESIDENT_KEY = "residentKey"
    const val FIELD_SUPPORTS_JSON_RESPONSE = "supportsJsonResponse"
    const val FIELD_ASSERTION_VALUE = "assertionValue"
    const val FIELD_ATTESTATION_VALUE = "attestationValue"

    // Private/Internal Fields (with underscore prefix)
    const val FIELD_RELYING_PARTY_ID = "_relyingPartyId"
    const val FIELD_ALLOW_CREDENTIALS_INTERNAL = "_allowCredentials"
    const val FIELD_PUB_KEY_CRED_PARAMS_INTERNAL = "_pubKeyCredParams"
    const val FIELD_EXCLUDE_CREDENTIALS_INTERNAL = "_excludeCredentials"
    const val FIELD_AUTHENTICATOR_SELECTION_INTERNAL = "_authenticatorSelection"

    // Standard Field Names
    const val FIELD_RELYING_PARTY_NAME = "relyingPartyName"
    const val FIELD_USER_ID = "userId"
    const val FIELD_USER_NAME = "userName"
    const val FIELD_ATTESTATION_PREFERENCE = "attestationPreference"
    const val FIELD_PUBLIC_KEY_CREDENTIAL_CREATION_OPTIONS = "publicKeyCredentialCreationOptions"
    const val FIELD_PUBLIC_KEY_CREDENTIAL_REQUEST_OPTIONS = "publicKeyCredentialRequestOptions"

    // Default Values
    const val DEFAULT_TIMEOUT = 60000
    const val DEFAULT_ATTESTATION = "none"
    const val DEFAULT_USER_VERIFICATION = "required"
    const val DEFAULT_RESIDENT_KEY_REQUIRED = "required"
    const val RESIDENT_KEY_DISCOURAGED = "discouraged"
    const val DEFAULT_RELYING_PARTY_ID = "credential-manager-test.example.com"

    // Separators
    const val DATA_SEPARATOR = "::"
    const val INT_SEPARATOR = ","

    // Callback IDs
    const val WEB_AUTHN_OUTCOME = "webAuthnOutcome"

    // Error Types
    const val ERROR_UNSUPPORTED = "unsupported"
    const val ERROR_NOT_ALLOWED = "NotAllowedError"
    const val ERROR_UNKNOWN = "UnknownError"
    const val ERROR_PREFIX = "ERROR::"

    // Authenticator Types
    const val AUTHENTICATOR_PLATFORM = "platform"
}

