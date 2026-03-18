/*
 * Copyright (c) 2025-2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import android.net.Uri
import com.pingidentity.mfa.commons.UriParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

/**
 * Utility class for parsing OATH URIs.
 */
object OathUriParser : UriParser() {
    
    private const val OTPAUTH_SCHEME = "otpauth"
    private const val TOTP = "totp"
    private const val HOTP = "hotp"
    private const val SECRET_PARAM = "secret"
    private const val ALGORITHM_PARAM = "algorithm"
    private const val DIGITS_PARAM = "digits"
    private const val PERIOD_PARAM = "period"
    private const val COUNTER_PARAM = "counter"
    
    // Additional parameters based on AM implementation of OATH
    private const val OATH_RESOURCE_ID_PARAM = "oid"        // OATH Resource ID parameter
    
    // Default values for optional parameters
    private const val DEFAULT_ALGORITHM = "SHA1"
    private const val DEFAULT_DIGITS = 6
    private const val DEFAULT_PERIOD = 30
    private const val DEFAULT_COUNTER = 0L
    
    /**
     * Parse an OATH URI string into an OathCredential.
     * Format: otpauth://totp/Issuer:AccountName?secret=SECRET&issuer=Issuer&algorithm=SHA1&digits=6&period=30
     * Format: otpauth://hotp/Issuer:AccountName?secret=SECRET&issuer=Issuer&algorithm=SHA1&digits=6&counter=0
     *
     * @param uri The URI string.
     * @return An OathCredential.
     * @throws IllegalArgumentException if the URI is invalid.
     */
    suspend fun parse(uri: String): OathCredential = withContext(Dispatchers.IO) {
        try {
            val parsedUri = uri.toUri()
            
            // Check scheme
            val scheme = parsedUri.scheme?.lowercase() ?: ""
            if (scheme != OTPAUTH_SCHEME && scheme != MFAUTH_SCHEME) {
                throw IllegalArgumentException("Invalid URI scheme: $scheme, expected: $OTPAUTH_SCHEME or $MFAUTH_SCHEME")
            }
            
            // Get type
            val type = when (val typeStr = parsedUri.authority?.lowercase()) {
                TOTP -> OathType.TOTP
                HOTP -> OathType.HOTP
                else -> throw IllegalArgumentException("Invalid OATH type: $typeStr, expected: $TOTP or $HOTP")
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
            
            // Get required parameters
            val secret = parsedUri.getQueryParameter(SECRET_PARAM)
                ?: throw IllegalArgumentException("Missing required parameter: $SECRET_PARAM")

            // Get optional parameters or use defaults
            val algorithmStr = parsedUri.getQueryParameter(ALGORITHM_PARAM)?.uppercase() ?: DEFAULT_ALGORITHM
            val algorithm = OathAlgorithm.fromString(algorithmStr)
            val digits = parsedUri.getQueryParameter(DIGITS_PARAM)?.toIntOrNull() ?: DEFAULT_DIGITS
            
            // Validate digits parameter (RFC 4226/6238 specifies 6 or 8 digits)
            if (digits != 6 && digits != 8) {
                throw IllegalArgumentException(
                    "Invalid digits value: $digits. Digits must be 6 or 8."
                )
            }
            
            val period = parsedUri.getQueryParameter(PERIOD_PARAM)?.toIntOrNull() ?: DEFAULT_PERIOD
            
            // Validate period for TOTP
            if (type == OathType.TOTP && period <= 0) {
                throw IllegalArgumentException(
                    "Invalid period value: $period. Period must be greater than 0."
                )
            }
            
            val counter = parsedUri.getQueryParameter(COUNTER_PARAM)?.toLongOrNull() ?: DEFAULT_COUNTER
            
            // Validate counter for HOTP
            if (type == OathType.HOTP && counter < 0) {
                throw IllegalArgumentException(
                    "Invalid counter value: $counter. Counter must be non-negative."
                )
            }
            
            // Parse the additional parameters and decode base64-encoded values
            
            // User ID - might be base64-encoded
            val userIdParam = parsedUri.getQueryParameter(USER_ID_PARAM_OATH)
            val userId = if (userIdParam != null && isBase64Encoded(userIdParam)) {
                decodeBase64(userIdParam)
            } else {
                userIdParam
            }

            // Resource ID - might be base64-encoded
            val resourceIdParam = parsedUri.getQueryParameter(OATH_RESOURCE_ID_PARAM)
            val resourceId = if (resourceIdParam != null && isBase64Encoded(resourceIdParam)) {
                decodeBase64(resourceIdParam)
            } else {
                resourceIdParam
            }

            // Policies - might be base64-encoded
            val policiesParam = parsedUri.getQueryParameter(POLICIES_PARAM)
            val policies = if (policiesParam != null && isBase64Encoded(policiesParam)) {
                decodeBase64(policiesParam)
            } else {
                policiesParam ?: ""
            }

            val imageURL = parsedUri.getQueryParameter(IMAGE_URL_PARAM)
            
            // Parse background color with proper formatting
            val backgroundColor = parsedUri.getQueryParameter(BACKGROUND_COLOR_PARAM)?.let {
                if (it.isNotEmpty() && !it.startsWith("#")) "#$it" else it
            }
            
            OathCredential(
                userId = userId,
                resourceId = resourceId,
                issuer = issuer,
                displayIssuer = issuer,  // Default displayIssuer to issuer
                accountName = accountName,
                displayAccountName = accountName,  // Default displayAccountName to accountName
                oathType = type,
                secret = secret,
                oathAlgorithm = algorithm,
                digits = digits,
                period = period,
                counter = counter,
                policies = policies,
                imageURL = imageURL,
                backgroundColor = backgroundColor
            )
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            if (e is IllegalArgumentException) {
                throw e
            } else {
                throw IllegalArgumentException("Invalid OATH URI: $uri", e)
            }
        }
    }
    
    /**
     * Format an OathCredential into a URI string.
     *
     * @param credential The OathCredential to format.
     * @return A URI string.
     */
    suspend fun format(credential: OathCredential): String = withContext(Dispatchers.IO) {
        val typeStr = if (credential.oathType == OathType.TOTP) TOTP else HOTP
        
        // Format the label part in a way that preserves the colon character
        val encodedLabel = if (credential.issuer.isNotEmpty()) {
            val encodedIssuer = Uri.encode(credential.issuer)
            val encodedAccount = Uri.encode(credential.accountName)
            "$encodedIssuer:$encodedAccount"
        } else {
            Uri.encode(credential.accountName)
        }
        
        val uriBuilder = StringBuilder()
            .append("$OTPAUTH_SCHEME://$typeStr/")
            .append(encodedLabel)
            .append("?$SECRET_PARAM=").append(Uri.encode(credential.secret))
        
        if (credential.issuer.isNotEmpty()) {
            uriBuilder.append("&$ISSUER_PARAM=").append(Uri.encode(credential.issuer))
        }
        
        if (credential.oathAlgorithm != OathAlgorithm.SHA1) {
            uriBuilder.append("&$ALGORITHM_PARAM=").append(Uri.encode(credential.oathAlgorithm.name))
        }
        
        if (credential.digits != 6) {
            uriBuilder.append("&$DIGITS_PARAM=").append(credential.digits)
        }
        
        if (credential.oathType == OathType.TOTP && credential.period != 30) {
            uriBuilder.append("&$PERIOD_PARAM=").append(credential.period)
        } else if (credential.oathType == OathType.HOTP) {
            uriBuilder.append("&$COUNTER_PARAM=").append(credential.counter)
        }
        
        // Add the additional parameters if they are present
        credential.userId?.takeIf { it.isNotEmpty() }?.let { userId ->
            // Base64 encode the userId parameter
            val encodedUserId = encodeBase64(userId)
            uriBuilder.append("&$USER_ID_PARAM_OATH=").append(Uri.encode(encodedUserId))
        }
        
        credential.resourceId?.takeIf { it.isNotEmpty() }?.let { resourceId ->
            // Base64 encode the resourceId parameter
            val encodedResourceId = encodeBase64(resourceId)
            uriBuilder.append("&$OATH_RESOURCE_ID_PARAM=").append(Uri.encode(encodedResourceId))
        }
        
        credential.imageURL?.takeIf { it.isNotEmpty() }?.let { imageUrl ->
            uriBuilder.append("&$IMAGE_URL_PARAM=").append(Uri.encode(imageUrl))
        }
        
        credential.backgroundColor?.takeIf { it.isNotEmpty() }?.let { backgroundColor ->
            // Remove # prefix if present for the parameter
            val bgColor = formatBackgroundColor(backgroundColor)
            uriBuilder.append("&$BACKGROUND_COLOR_PARAM=").append(Uri.encode(bgColor))
        }
        uriBuilder.toString()
    }
}
