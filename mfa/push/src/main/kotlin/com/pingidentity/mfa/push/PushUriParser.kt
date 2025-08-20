/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import android.net.Uri
import com.pingidentity.mfa.commons.UriParser
import androidx.core.net.toUri

/**
 * Utility class for parsing Push URIs.
 */
object PushUriParser : UriParser() {

    private const val PUSHAUTH_SCHEME = "pushauth"

    // Required parameters
    private const val SHARED_SECRET_PARAM = "s"             // Shared secret used for signing
    private const val REG_ENDPOINT_PARAM = "r"              // Registration endpoint
    private const val AUTH_ENDPOINT_PARAM = "a"             // Authentication endpoint
    private const val CHALLENGE_PARAM = "c"                 // Challenge parameter to use for registration response
    private const val AM_LOAD_BALANCER_PARAM  = "l"         // AM Load Balancer cookie
    private const val MESSAGE_ID = "m"                      // Message id to use for registration response

    // Optional parameters
    private const val PUSH_RESOURCE_ID_PARAM = "pid"        // Push Resource ID parameter

    /**
     * Parse a Push URI string into a PushCredential.
     * Format: pushauth://push/issuer:accountName?r=regEndpoint&a=authEndpoint&s=sharedSecret&d=userId
     * Format: mfauth://push/issuer:accountName?r=regEndpoint&a=authEndpoint&s=sharedSecret&d=userId
     *
     * @param uri The URI string.
     * @return A PushCredential.
     * @throws IllegalArgumentException if the URI is invalid.
     */
    fun parse(uri: String): PushCredential {
        try {
            val parsedUri = uri.toUri()

            // Check scheme
            val scheme = parsedUri.scheme?.lowercase() ?: ""
            if (scheme != PUSHAUTH_SCHEME && scheme != MFAUTH_SCHEME) {
                throw IllegalArgumentException("Invalid URI scheme: $scheme, expected: $PUSHAUTH_SCHEME or $MFAUTH_SCHEME")
            }

            // Parse label (path without leading '/')
            val label = parsedUri.path?.removePrefix("/") ?: ""

            // Get issuer parameter and decode, it overrides the label issuer if provided
            var issuerParam = parsedUri.getQueryParameter(ISSUER_PARAM)
            if (!issuerParam.isNullOrEmpty()) {
                if (isBase64Encoded(issuerParam)) {
                    issuerParam = decodeBase64Url(issuerParam)
                }
            }

            // Extract issuer and accountName from label
            val (issuer, accountName) = parseLabelComponents(label, issuerParam)

            // If issuerParam is provided, use that exact format to preserve case
            val finalIssuer = if (!issuerParam.isNullOrEmpty()) issuerParam else issuer

            // Get registration and authentication endpoints and combine into serverEndpoint
            val regEndpoint = parsedUri.getQueryParameter(REG_ENDPOINT_PARAM)
                ?: throw IllegalArgumentException("Missing required parameter: $REG_ENDPOINT_PARAM")

            parsedUri.getQueryParameter(AUTH_ENDPOINT_PARAM)
                ?: throw IllegalArgumentException("Missing required parameter: $AUTH_ENDPOINT_PARAM")

            // Decode Base64 URLs if needed
            val decodedRegEndpoint = if (isBase64Encoded(regEndpoint)) decodeBase64Url(regEndpoint) else regEndpoint

            // Extract the server endpoint base URL (without query parameters)
            val serverEndpoint = extractServerEndpoint(decodedRegEndpoint)

            // Get shared secret (required)
            val sharedSecret = parsedUri.getQueryParameter(SHARED_SECRET_PARAM)
                ?: throw IllegalArgumentException("Missing required parameter: $SHARED_SECRET_PARAM")

            // The shared secret is already in the correct format (base64url)
            // and doesn't need further decoding
            val decodedSharedSecret = recodeBase64NoWrapUrlSafeValueToNoWrap(sharedSecret)

            // Get user ID (optional)
            val userIdParam = parsedUri.getQueryParameter(USER_ID_PARAM)
            val userId = if (userIdParam != null && isBase64Encoded(userIdParam)) {
                decodeBase64(userIdParam)
            } else {
                userIdParam
            }

            // Get resource ID (optional)
            val resourceIdParam = parsedUri.getQueryParameter(PUSH_RESOURCE_ID_PARAM)
            val resourceId = if (resourceIdParam != null && isBase64Encoded(resourceIdParam)) {
                decodeBase64(resourceIdParam)
            } else {
                resourceIdParam ?: ""
            }

            // Policies - might be base64-encoded
            val policiesParam = parsedUri.getQueryParameter(POLICIES_PARAM)
            val policies = if (policiesParam != null && isBase64Encoded(policiesParam)) {
                decodeBase64(policiesParam)
            } else {
                policiesParam ?: ""
            }

            // Parse other optional parameters
            val imageURLParam = parsedUri.getQueryParameter(IMAGE_URL_PARAM)
            val imageURL = if (imageURLParam != null && isBase64Encoded(imageURLParam)) {
                decodeBase64(imageURLParam)
            } else {
                imageURLParam
            }

            // Parse background color with proper formatting
            val backgroundColor = parsedUri.getQueryParameter(BACKGROUND_COLOR_PARAM)?.let {
                if (it.isNotEmpty() && !it.startsWith("#")) "#$it" else it
            }

            // Now try to parse the challenge, load balancer, and message id params
            // We don't throw if they're missing as they are required only for registration
            parsedUri.getQueryParameter(CHALLENGE_PARAM)
            parsedUri.getQueryParameter(AM_LOAD_BALANCER_PARAM)
            parsedUri.getQueryParameter(MESSAGE_ID)

            return PushCredential(
                userId = userId,
                resourceId = resourceId,
                issuer = finalIssuer,
                displayIssuer = finalIssuer,  // Default displayIssuer to issuer
                accountName = accountName,
                displayAccountName = accountName,  // Default displayAccountName to accountName
                serverEndpoint = serverEndpoint,
                sharedSecret = decodedSharedSecret,
                policies = policies,
                imageURL = imageURL,
                backgroundColor = backgroundColor
            )

        } catch (e: Exception) {
            if (e is IllegalArgumentException) {
                throw e
            } else {
                throw IllegalArgumentException("Invalid Push URI: $uri", e)
            }
        }
    }

    /**
     * Format a PushCredential into a URI string.
     *
     * @param credential The PushCredential to format.
     * @return A URI string.
     */
    fun format(credential: PushCredential): String {
        // Format the label part in a way that preserves the colon character
        val encodedLabel = if (credential.issuer.isNotEmpty()) {
            val encodedIssuer = Uri.encode(credential.issuer)
            val encodedAccount = Uri.encode(credential.accountName)
            "$encodedIssuer:$encodedAccount"
        } else {
            Uri.encode(credential.accountName)
        }

        // Create registration and authentication endpoints
        val regEndpoint = "${credential.serverEndpoint}?_action=register"
        val authEndpoint = "${credential.serverEndpoint}?_action=authenticate"

        // Encode the endpoints in Base64
        val encodedRegEndpoint = encodeBase64(regEndpoint)
        val encodedAuthEndpoint = encodeBase64(authEndpoint)

        val uriBuilder = StringBuilder()
            .append("$PUSHAUTH_SCHEME://push/")
            .append(encodedLabel)
            .append("?$REG_ENDPOINT_PARAM=").append(Uri.encode(encodedRegEndpoint))
            .append("&$AUTH_ENDPOINT_PARAM=").append(Uri.encode(encodedAuthEndpoint))
            .append("&$SHARED_SECRET_PARAM=").append(recodeBase64NoWrapValueToUrlSafeNoWrap(credential.sharedSecret))

        if (credential.issuer.isNotEmpty()) {
            uriBuilder.append("&$ISSUER_PARAM=").append(Uri.encode(credential.issuer))
        }

        // Add optional parameters if present
        if (credential.userId?.isNotEmpty() == true) {
            val encodedUserId = encodeBase64(credential.userId)
            uriBuilder.append("&$USER_ID_PARAM=").append(Uri.encode(encodedUserId))
        }

        if (credential.resourceId.isNotEmpty()) {
            val encodedResourceId = encodeBase64(credential.resourceId)
            uriBuilder.append("&$PUSH_RESOURCE_ID_PARAM=").append(Uri.encode(encodedResourceId))
        }

        // Add the new fields if present
        if (!credential.imageURL.isNullOrEmpty()) {
            val encodedImageURL = encodeBase64(credential.imageURL)
            uriBuilder.append("&$IMAGE_URL_PARAM=").append(Uri.encode(encodedImageURL))
        }

        // Format background color (removing # if present) when adding to URI
        if (!credential.backgroundColor.isNullOrEmpty()) {
            val colorValue = formatBackgroundColor(credential.backgroundColor)
            uriBuilder.append("&$BACKGROUND_COLOR_PARAM=").append(Uri.encode(colorValue))
        }

        // Add policies if present, encoding them in Base64 if they're JSON
        if (!credential.policies.isNullOrEmpty()) {
            val encodedPolicies = encodeBase64(credential.policies)
            uriBuilder.append("&$POLICIES_PARAM=").append(Uri.encode(encodedPolicies))
        }

        return uriBuilder.toString()
    }

    fun registrationParameters(uri: String): Map<String, String> {
        val parsedUri = uri.toUri()

        val messageIdParam = parsedUri.getQueryParameter(MESSAGE_ID)
            ?: throw IllegalArgumentException("Missing required parameter: $MESSAGE_ID")

        val amlbParam = parsedUri.getQueryParameter(AM_LOAD_BALANCER_PARAM)
            ?: throw IllegalArgumentException("Missing required parameter: $AM_LOAD_BALANCER_PARAM")
        val amlbParamDecoded = if (isBase64Encoded(amlbParam)) decodeBase64Url(amlbParam) else amlbParam

        val challengeParam = parsedUri.getQueryParameter(CHALLENGE_PARAM)
            ?: throw IllegalArgumentException("Missing required parameter: $CHALLENGE_PARAM")
        val challengeParamDecoded = recodeBase64NoWrapUrlSafeValueToNoWrap(challengeParam)

        // Return the parameters as a map
        return mapOf(
            PushConstants.KEY_ALMB_COOKIE to amlbParamDecoded,
            PushConstants.KEY_MESSAGE_ID to messageIdParam,
            PushConstants.KEY_CHALLENGE to challengeParamDecoded
        )
    }

    /**
     * Extract the base server endpoint from a full URL.
     * Removes query parameters from the URL.
     *
     * @param url The full URL with potential query parameters
     * @return The base server endpoint URL without query parameters
     */
    private fun extractServerEndpoint(url: String): String {
        // Handle URLs without using Uri parser to avoid encoding issues
        val queryIndex = url.indexOf('?')
        val baseUrl = if (queryIndex > 0) url.substring(0, queryIndex) else url

        // Since we're not using Uri.parse, we need to manually decode any percent-encoded characters
        return Uri.decode(baseUrl)
    }
}
