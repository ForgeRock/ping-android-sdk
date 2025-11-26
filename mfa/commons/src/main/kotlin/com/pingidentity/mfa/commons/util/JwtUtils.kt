/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.util

import android.util.Base64
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Utility class for JWT-related operations.
 * This class provides methods for generating and validating JWTs.
 */
object JwtUtils {

    /**
     * Generate a JWT for authentication.
     *
     * @param base64Secret The base64-encoded secret key.
     * @param claims The claims to include in the JWT.
     * @return The JWT string.
     * @throws IllegalArgumentException If the secret is null or empty.
     * @throws JOSEException If there is an error signing the JWT.
     */
    fun generateJwt(base64Secret: String, claims: Map<String, Any>): String {
        // Check shared secret
        if (base64Secret.isEmpty()) {
            throw IllegalArgumentException("Secret cannot be empty")
        }

        try {
            // Prepare JWT with claims
            val claimBuilder = JWTClaimsSet.Builder()
            for ((key, value) in claims) {
                claimBuilder.claim(key, value)
            }

            // Apply the HMAC protection
            val header = JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT)
                .build()

            val signedJWT = SignedJWT(header, claimBuilder.build())

            // Create HMAC signer
            val secret = Base64.decode(base64Secret, Base64.NO_WRAP)
            val signer: JWSSigner = MACSigner(secret)

            // Sign JWT
            signedJWT.sign(signer)
            return signedJWT.serialize()
        } catch (e: JOSEException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Check if a string is a valid JWT with the required fields.
     *
     * @param jwt The JWT string to validate.
     * @param requiredFields A map of field names to check in the payload.
     * @return True if the JWT is valid and contains the required fields, false otherwise.
     */
    fun isValidJwt(jwt: String, requiredFields: List<String> = emptyList()): Boolean {
        try {
            // Parse the JWT manually - split into parts
            val parts = jwt.split(".")
            if (parts.size != 3) {
                return false
            }

            // Decode the claims part (second part of the JWT)
            val claimsJson = String(Base64.decode(parts[1], Base64.URL_SAFE), Charsets.UTF_8)
            
            // Use JsonObject to handle mixed data types
            val jsonParser = Json { ignoreUnknownKeys = true }
            val claimsJsonObject = jsonParser.parseToJsonElement(claimsJson).jsonObject
            
            // Check for required fields if provided
            if (requiredFields.isNotEmpty()) {
                for (field in requiredFields) {
                    if (!claimsJsonObject.containsKey(field)) {
                        return false
                    }
                }
            }
            
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Parse a JWT string and extract its payload claims.
     *
     * @param jwt The JWT string to parse.
     * @return A map of the payload claims.
     */
    fun parseJwtClaims(jwt: String): Map<String, Any> {
        try {
            // Parse the JWT manually - split into parts
            val parts = jwt.split(".")
            if (parts.size != 3) {
                throw IllegalArgumentException("Invalid JWT format")
            }

            // Decode the claims part (second part of the JWT)
            val claimsJson = String(Base64.decode(parts[1], Base64.URL_SAFE), Charsets.UTF_8)
            
            // Use JsonObject to handle mixed data types
            val jsonParser = Json { ignoreUnknownKeys = true }
            val claimsJsonObject = jsonParser.parseToJsonElement(claimsJson).jsonObject
            
            // Convert JsonObject to Map<String, Any>
            val result = mutableMapOf<String, Any>()
            for (key in claimsJsonObject.keys) {
                val element = claimsJsonObject[key]
                val value = when {
                    element == null -> null
                    element is JsonPrimitive -> {
                        val content = element.toString().trim('"')
                        content.toIntOrNull() ?:
                        content.toLongOrNull() ?:
                        content.toDoubleOrNull() ?:
                        content.toBooleanStrictOrNull() ?:
                        content
                    }
                    else -> element.toString()
                }
                if (value != null) {
                    result[key] = value
                }
            }
            
            return result
        } catch (e: Exception) {
            throw e
        }
    }
}