/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for OathUriParser class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class OathUriParserTest {

    @Test
    fun `test parse basic TOTP URI`() {
        val uri = "otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"
        val credential = OathUriParser.parse(uri)
        
        assertNotNull(credential)
        assertEquals("Example", credential.issuer)
        assertEquals("alice@example.com", credential.accountName)
        assertEquals(OathType.TOTP, credential.oathType)
        assertEquals(OathAlgorithm.SHA1, credential.oathAlgorithm)
        assertEquals(6, credential.digits)
        assertEquals(30, credential.period)
        assertEquals(0L, credential.counter)
    }

    @Test
    fun `test parse with base64 encoded userId and resourceId`() {
        // Parameters uid and oid are Base64-encoded
        val uri = "otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example" +
                "&uid=YWxpY2VAdGVzdC5jb20=&oid=ZGV2aWNlLTEyMzQ1"
        
        val credential = OathUriParser.parse(uri)

        assertEquals("Example", credential.issuer)
        assertEquals("alice@example.com", credential.accountName)
        assertEquals("alice@test.com", credential.userId) // Decoded from Base64
        assertEquals("device-12345", credential.resourceId) // Decoded from Base64
    }

    @Test
    fun `test format with userId and resourceId base64 encoding`() {
        val credential = OathCredential(
            issuer = "Example",
            displayIssuer = "Example",
            accountName = "alice@example.com",
            displayAccountName = "alice@example.com",
            oathType = OathType.TOTP,
            secret = "JBSWY3DPEHPK3PXP",
            userId = "alice@test.com",
            resourceId = "device-12345"
        )
        
        val uri = OathUriParser.format(credential)
        
        // Parse the URI back to verify encoding/decoding worked correctly
        val parsedCredential = OathUriParser.parse(uri)
        
        assertEquals("Example", parsedCredential.issuer)
        assertEquals("alice@example.com", parsedCredential.accountName)
        assertEquals("alice@test.com", parsedCredential.userId)
        assertEquals("device-12345", parsedCredential.resourceId)
    }

    @Test
    fun `test parse and format with mfauth scheme`() {
        val uri = "mfauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=RXhhbXBsZQ" +
                "&uid=YWxpY2VAdGVzdC5jb20=&oid=ZGV2aWNlLTEyMzQ1"
        
        val credential = OathUriParser.parse(uri)
        val formattedUri = OathUriParser.format(credential)
        
        // Format should use otpauth scheme by default
        assertEquals(true, formattedUri.startsWith("otpauth://"))
        
        // Parse the formatted URI back to verify
        val reparsedCredential = OathUriParser.parse(formattedUri)
        
        assertEquals("alice@test.com", reparsedCredential.userId)
        assertEquals("device-12345", reparsedCredential.resourceId)
        assertEquals("Example", reparsedCredential.issuer)
    }

    @Test
    fun `test parse URI with background color parameter`() {
        val uri = "otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&b=FF5500"
        val credential = OathUriParser.parse(uri)
        
        assertEquals("#FF5500", credential.backgroundColor)
        
        val formattedUri = OathUriParser.format(credential)
        val reparsedCredential = OathUriParser.parse(formattedUri)
        
        assertEquals("#FF5500", reparsedCredential.backgroundColor)
    }

    @Test
    fun `test parse URI with background color parameter with hash`() {
        val uri = "otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&b=%23FF5500"
        val credential = OathUriParser.parse(uri)
        
        assertEquals("#FF5500", credential.backgroundColor)
        
        val formattedUri = OathUriParser.format(credential)
        val reparsedCredential = OathUriParser.parse(formattedUri)
        
        assertEquals("#FF5500", reparsedCredential.backgroundColor)
    }

    @Test
    fun `verify parsing and formatting of URI with Base64 encoded parameters`() {
        // Using standard Java Base64 for test data preparation
        val encodedUserId = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("test-user@example.com".toByteArray())
        val encodedResourceId = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("resource-123456".toByteArray())
        
        // Create a URI with encoded parameters
        val uriWithEncodedParams = "otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&algorithm=SHA1&digits=6&period=30&userId=$encodedUserId&resourceId=$encodedResourceId&backgroundColor=%23ff0000"
        
        println("Original URI with encoded parameters: $uriWithEncodedParams")
        
        // Parse the URI
        val credential = OathUriParser.parse(uriWithEncodedParams)
        
        // Print the credential details
        println("Parsed credential: $credential")
        
        // Format the credential back to a URI
        val formattedUri = OathUriParser.format(credential)
        println("Reformatted URI: $formattedUri")
        
        // Verify the reformatted URI can be parsed again
        val reparsedCredential = OathUriParser.parse(formattedUri)
        println("Reparsed credential: $reparsedCredential")
        
        // Check that both credentials are equal
        assert(credential.userId == reparsedCredential.userId) { 
            "User IDs don't match. Original: ${credential.userId}, Reparsed: ${reparsedCredential.userId}" 
        }
        assert(credential.resourceId == reparsedCredential.resourceId) { 
            "Resource IDs don't match. Original: ${credential.resourceId}, Reparsed: ${reparsedCredential.resourceId}" 
        }
    }
    
}
