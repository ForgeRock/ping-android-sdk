/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons

import android.net.Uri
import android.util.Base64

/**
 * Base class for URI parsers with common functionality.
 */
abstract class UriParser {
    
    protected companion object {
        // Common parameters
        const val MFAUTH_SCHEME = "mfauth"            // Alternative scheme
        const val ISSUER_PARAM = "issuer"             // The IDP that issued the URI
        const val USER_ID_PARAM = "d"                 // User ID parameter (used in Push)
        const val USER_ID_PARAM_OATH = "uid"         // User ID parameter (used in OATH)
        const val IMAGE_URL_PARAM = "image"           // Image URL
        const val BACKGROUND_COLOR_PARAM = "b"        // Background color
        const val POLICIES_PARAM = "policies"         // Policies parameter
    }
    
    /**
     * Parse the issuer and accountName from the label component of a URI.
     *
     * @param label The label from the URI path (Issuer:AccountName)
     * @param issuerParam The issuer from the URI query parameter (optional)
     * @return A Pair of issuer and accountName.
     */
    protected fun parseLabelComponents(label: String, issuerParam: String?): Pair<String, String> {
        // Try to split label into issuer and accountName
        val labelComponents = label.split(":", limit = 2)
        
        return when {
            labelComponents.size == 2 -> {
                // Label has the form "Issuer:AccountName"
                // Verify that issuerParam matches the label issuer, if both are present
                val labelIssuer = labelComponents[0]
                if (issuerParam != null && labelIssuer.isNotEmpty() && 
                    !issuerParam.equals(labelIssuer, ignoreCase = true)) {
                    throw IllegalArgumentException("Issuer parameter ($issuerParam) doesn't match label issuer ($labelIssuer)")
                }
                
                // Use the label issuer if it exists, otherwise use the parameter
                val issuer = if (labelIssuer.isNotEmpty()) labelIssuer else (issuerParam ?: "")
                val accountName = labelComponents[1]
                
                issuer to accountName
            }
            label.isNotEmpty() -> {
                // Label doesn't have a colon, treat the whole label as accountName
                val issuer = issuerParam ?: ""
                
                issuer to label
            }
            else -> {
                // No label, use empty strings or issuerParam
                val issuer = issuerParam ?: ""
                
                issuer to ""
            }
        }
    }
    
    /**
     * Check if a string is Base64 encoded.
     *
     * @param value The string to check.
     * @return True if the string is Base64 encoded, false otherwise.
     */
    protected fun isBase64Encoded(value: String): Boolean {
        return try {
            Base64.decode(value, Base64.DEFAULT)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Decode a Base64 encoded string.
     *
     * @param value The Base64 encoded string.
     * @return The decoded string.
     */
    protected fun decodeBase64(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE)
        return String(bytes)
    }
    
    /**
     * Encode a string as Base64.
     *
     * @param value The string to encode.
     * @return The Base64 encoded string.
     */
    protected fun encodeBase64(value: String): String {
        val bytes = value.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }
    
    /**
     * Format the background color for URI inclusion.
     * Removes # prefix if present.
     *
     * @param backgroundColor The background color to format.
     * @return The formatted background color value.
     */
    protected fun formatBackgroundColor(backgroundColor: String?): String? {
        return backgroundColor?.let {
            if (it.startsWith("#")) it.substring(1) else it
        }
    }
}
