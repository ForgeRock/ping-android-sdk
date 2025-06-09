/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import android.net.Uri
import com.pingidentity.mfa.commons.UriParser

/**
 * Utility class for parsing Push URIs.
 */
object PushUriParser : UriParser() {

    private const val PUSHAUTH_SCHEME = "pushauth"
    
    // Required parameters
    private const val SHARED_SECRET_PARAM = "s"             // Shared secret used for signing
    private const val REG_ENDPOINT_PARAM = "r"              // Registration endpoint
    private const val AUTH_ENDPOINT_PARAM = "a"             // Authentication endpoint

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
    @JvmStatic
    fun parse(uri: String): PushCredential {
        try {
            val parsedUri = Uri.parse(uri)
            
            // Check scheme
            val scheme = parsedUri.scheme?.lowercase() ?: ""
            if (scheme != PUSHAUTH_SCHEME && scheme != MFAUTH_SCHEME) {
                throw IllegalArgumentException("Invalid URI scheme: $scheme, expected: $PUSHAUTH_SCHEME or $MFAUTH_SCHEME")
            }
            
            // Parse label (path without leading '/')
            val label = parsedUri.path?.removePrefix("/") ?: ""
            
            // Get issuer parameter and decode it if needed for MFAUTH scheme
            var issuerParam = parsedUri.getQueryParameter(ISSUER_PARAM)
            if (!issuerParam.isNullOrEmpty() && scheme == MFAUTH_SCHEME) {
                if (isBase64Encoded(issuerParam)) {
                    issuerParam = decodeBase64(issuerParam)
                }
            }
            
            // Extract issuer and accountName from label
            val (issuer, accountName) = parseLabelComponents(label, issuerParam)
            
            // If issuerParam is provided, use that exact format to preserve case
            val finalIssuer = if (!issuerParam.isNullOrEmpty()) issuerParam else issuer
            
            // Get registration and authentication endpoints and combine into serverEndpoint
            val regEndpoint = parsedUri.getQueryParameter(REG_ENDPOINT_PARAM)
                ?: throw IllegalArgumentException("Missing required parameter: $REG_ENDPOINT_PARAM")
                
            val authEndpoint = parsedUri.getQueryParameter(AUTH_ENDPOINT_PARAM)
                ?: throw IllegalArgumentException("Missing required parameter: $AUTH_ENDPOINT_PARAM")

            // Decode Base64 URLs if needed
            val decodedRegEndpoint = if (isBase64Encoded(regEndpoint)) decodeBase64(regEndpoint) else regEndpoint
                
            // Extract the server endpoint base URL (without query parameters)
            val serverEndpoint = extractServerEndpoint(decodedRegEndpoint)
                
            // Get shared secret (required)
            val sharedSecret = parsedUri.getQueryParameter(SHARED_SECRET_PARAM)
                ?: throw IllegalArgumentException("Missing required parameter: $SHARED_SECRET_PARAM")
                
            // Decode Base64 shared secret if needed - 
            // The shared secret is often already in the correct format (base64url)
            // and doesn't need further decoding
            val decodedSharedSecret = sharedSecret
            
            // Get user ID (optional)
            val userIdParam = parsedUri.getQueryParameter(USER_ID_PARAM)
            val userId = if (userIdParam != null && isBase64Encoded(userIdParam)) {
                decodeBase64(userIdParam)
            } else {
                userIdParam ?: ""
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
                backgroundColor = backgroundColor,
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
    @JvmStatic
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
        // Don't encode the shared secret - it should already be in the correct format
        
        val uriBuilder = StringBuilder()
            .append("$PUSHAUTH_SCHEME://push/")
            .append(encodedLabel)
            .append("?$REG_ENDPOINT_PARAM=").append(Uri.encode(encodedRegEndpoint))
            .append("&$AUTH_ENDPOINT_PARAM=").append(Uri.encode(encodedAuthEndpoint))
            .append("&$SHARED_SECRET_PARAM=").append(Uri.encode(credential.sharedSecret))
            
        if (credential.issuer.isNotEmpty()) {
            uriBuilder.append("&$ISSUER_PARAM=").append(Uri.encode(credential.issuer))
        }
        
        // Add optional parameters if present
        if (credential.userId.isNotEmpty()) {
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
        return android.net.Uri.decode(baseUrl)
    }
}
