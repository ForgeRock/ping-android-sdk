/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.util.Base64

/**
 * Unit tests for PushUriParser class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PushUriParserTest {

    @Test
    fun `test parse basic Push URI`() {
        val uri = "pushauth://push/forgerock:user?a=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&r=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy&s=b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E"
        val credential = PushUriParser.parse(uri)
        
        assertNotNull(credential)
        assertEquals("forgerock", credential.issuer)
        assertEquals("user", credential.accountName)
        assertEquals("http://dev.openam.example.com:8081/openam/json/dev/push/sns/message", credential.serverEndpoint)
        assertEquals("b3uYLkQ7dRPjBaIzV0t/aijoXRgMq+NP5AwVAvRfa/E=", credential.sharedSecret)
    }

    @Test
    fun `test parse real Push URI`() {
        val uri = "pushauth://push/ForgeRock:stoyan@forgerock.com?a=aHR0cHM6Ly9vcGVuYW0tc2Rrcy5mb3JnZWJsb2Nrcy5jb206NDQzL2FtL2pzb24vYWxwaGEvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&r=aHR0cHM6Ly9vcGVuYW0tc2Rrcy5mb3JnZWJsb2Nrcy5jb206NDQzL2FtL2pzb24vYWxwaGEvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy&b=032b75&s=oSWY2AY0tHrGUivojn-iahvGC77YDKcA2x6ChSDzwAo&c=jaKZUQlypvRCEugWMVvUWcpNfUFW4pSiB9sVBcKZLis&l=YW1sYmNvb2tpZT0wMQ&m=REGISTER:9a8e9525-f598-4a7d-a759-1ff86f130cb31755365762960&issuer=Rm9yZ2VSb2Nr"
        val credential = PushUriParser.parse(uri)

        // Verify essential properties
        assertNotNull(credential)
        assertEquals("ForgeRock", credential.issuer)
        assertEquals("stoyan@forgerock.com", credential.accountName)
        assertEquals("oSWY2AY0tHrGUivojn+iahvGC77YDKcA2x6ChSDzwAo=", credential.sharedSecret)

        // Verify server endpoint contains expected base URL
        val serverEndpoint = credential.serverEndpoint
        assert(serverEndpoint.contains("openam-sdks.forgeblocks.com:443/am/json/alpha/push/sns/message"))

        // Also try to get registration parameters
        val regParams = PushUriParser.registrationParameters(uri)
        assertEquals("REGISTER:9a8e9525-f598-4a7d-a759-1ff86f130cb31755365762960", regParams["messageId"])
        assertEquals("jaKZUQlypvRCEugWMVvUWcpNfUFW4pSiB9sVBcKZLis=", regParams["challenge"])
        assertEquals("amlbcookie=01", regParams["amlbCookie"])
    }

    @Test
    fun `test parse real MFA URI`() {
        val uri = "mfauth://totp/ForgeRock:rodrigo@forgerock.com?a=aHR0cHM6Ly9vcGVuYW0tc2Rrcy5mb3JnZWJsb2Nrcy5jb206NDQzL2FtL2pzb24vYWxwaGEvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&image=https://img.favpng.com/9/25/24/computer-icons-instagram-logo-sticker-png-favpng-LZmXr3KPyVbr8LkxNML458QV3.jpg&r=aHR0cHM6Ly9vcGVuYW0tc2Rrcy5mb3JnZWJsb2Nrcy5jb206NDQzL2FtL2pzb24vYWxwaGEvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy&b=032b75&period=30&s=uo2Cl3tmuZF6v_U_n6x7sedgtvtTSoNJCOxmu1rP1WI&c=j_ho1QgRBsE0zeDpGdt9s4loRxrLwpRuOcNqKNQCLOo&digits=8&secret=RY7IQKMGXDI7KTOHD45PUQ6UMM======&l=YW1sYmNvb2tpZT0wMQ&m=REGISTER:c07f99ef-8c65-420f-bd3b-6b835a4868b01755815432333&issuer=Rm9yZ2VSb2Nr"
        val credential = PushUriParser.parse(uri)

        // Verify essential properties
        assertNotNull(credential)
        assertEquals("ForgeRock", credential.issuer)
        assertEquals("rodrigo@forgerock.com", credential.accountName)
        assertEquals("uo2Cl3tmuZF6v/U/n6x7sedgtvtTSoNJCOxmu1rP1WI=", credential.sharedSecret)
        assertEquals("https://img.favpng.com/9/25/24/computer-icons-instagram-logo-sticker-png-favpng-LZmXr3KPyVbr8LkxNML458QV3.jpg", credential.imageURL) // TOTP secret

        // Verify server endpoint contains expected base URL
        val serverEndpoint = credential.serverEndpoint
        assert(serverEndpoint.contains("openam-sdks.forgeblocks.com:443/am/json/alpha/push/sns/message"))

        // Also try to get registration parameters
        val regParams = PushUriParser.registrationParameters(uri)
        assertEquals("REGISTER:c07f99ef-8c65-420f-bd3b-6b835a4868b01755815432333", regParams["messageId"])
        assertEquals("j/ho1QgRBsE0zeDpGdt9s4loRxrLwpRuOcNqKNQCLOo=", regParams["challenge"])
        assertEquals("amlbcookie=01", regParams["amlbCookie"])
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun `test parse invalid scheme URI`() {
        val uri = "invalidscheme://push/forgerock:user?a=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&r=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy&s=b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E"
        PushUriParser.parse(uri) // Should throw IllegalArgumentException
    }

    @Test
    fun `test parse with base64 encoded userId and resourceId`() {
        val uri = "pushauth://push/forgerock:user?a=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&r=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy&s=b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E&d=dXNlcjM&pid=NTgxZGQzYzgtM2M2OS00OWFjLWIwMWEtMDc0NDUwYjIyNmM1"
        
        val credential = PushUriParser.parse(uri)

        assertEquals("forgerock", credential.issuer)
        assertEquals("user", credential.accountName)
        assertEquals("user3", credential.userId) // Decoded from Base64
        assertEquals("581dd3c8-3c69-49ac-b01a-074450b226c5", credential.resourceId) // Decoded from Base64
    }

    @Test
    fun `test format with userId and resourceId base64 encoding`() {
        val credential = PushCredential(
            issuer = "ForgeRock",
            displayIssuer = "ForgeRock",
            accountName = "user",
            displayAccountName = "user",
            serverEndpoint = "http://dev.openam.example.com:8081/openam/json/dev/push/sns/message",
            sharedSecret = "b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E",
            userId = "user3",
            resourceId = "581dd3c8-3c69-49ac-b01a-074450b226c5"
        )
        
        val uri = PushUriParser.format(credential)
        
        // Parse the URI back to verify encoding/decoding worked correctly
        val parsedCredential = PushUriParser.parse(uri)
        
        assertEquals("ForgeRock", parsedCredential.issuer)
        assertEquals("user", parsedCredential.accountName)
        assertEquals("user3", parsedCredential.userId)
        assertEquals("581dd3c8-3c69-49ac-b01a-074450b226c5", parsedCredential.resourceId)
    }

    @Test
    fun `test parse and format with mfauth scheme`() {
        val uri = "mfauth://push/forgerock:user?a=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&r=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy&s=b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E&issuer=Rm9yZ2VSb2Nr"
        
        val credential = PushUriParser.parse(uri)
        val formattedUri = PushUriParser.format(credential)
        
        // Format should use pushauth scheme by default
        assertEquals(true, formattedUri.startsWith("pushauth://"))
        
        // Parse the formatted URI back to verify
        val reparsedCredential = PushUriParser.parse(formattedUri)
        
        assertEquals("ForgeRock", reparsedCredential.issuer)
        assertEquals("user", reparsedCredential.accountName)
    }

    @Test
    fun `test parse URI with background color parameter`() {
        val uri = "pushauth://push/forgerock:user?a=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&b=519387&r=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy&s=b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E"
        val credential = PushUriParser.parse(uri)
        
        assertEquals("#519387", credential.backgroundColor)
        
        val formattedUri = PushUriParser.format(credential)
        val reparsedCredential = PushUriParser.parse(formattedUri)
        
        assertEquals("#519387", reparsedCredential.backgroundColor)
    }

    @Test
    fun `test parse URI with background color parameter with hash`() {
        val uri = "pushauth://push/forgerock:user?a=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&b=%23519387&r=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy&s=b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E"
        val credential = PushUriParser.parse(uri)
        
        assertEquals("#519387", credential.backgroundColor)
        
        val formattedUri = PushUriParser.format(credential)
        val reparsedCredential = PushUriParser.parse(formattedUri)
        
        assertEquals("#519387", reparsedCredential.backgroundColor)
    }

    @Test
    fun `test parse URI with image URL parameter`() {
        val uri = "pushauth://push/forgerock:user?a=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&b=519387&image=aHR0cDovL2Zvcmdlcm9jay5jb20vbG9nby5qcGc&r=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy&s=b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E&c=9giiBAdUHjqpo0XE4YdZ7pRlv0hrQYwDz8Z1wwLLbkg&l=YW1sYmNvb2tpZT0wMQ&m=REGISTER:8be951c6-af83-438d-8f74-421bd18650421570561063169&issuer=Rm9yZ2VSb2Nr"
        val credential = PushUriParser.parse(uri)
        
        assertEquals("http://forgerock.com/logo.jpg", credential.imageURL)
        
        val formattedUri = PushUriParser.format(credential)
        val reparsedCredential = PushUriParser.parse(formattedUri)
        
        assertEquals("http://forgerock.com/logo.jpg", reparsedCredential.imageURL)
    }

    @Test
    fun `verify parsing and formatting of URI with Base64 encoded parameters`() {
        // Using standard Java Base64 for test data preparation
        val encodedUserId = Base64.encodeToString("test-user@example.com".toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        val encodedResourceId = Base64.encodeToString("resource-123456".toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        
        // Create a URI with encoded parameters
        val uriWithEncodedParams = "pushauth://push/ForgeRock:john.doe?a=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&r=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy&s=b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E&d=$encodedUserId&pid=$encodedResourceId&b=%23ff0000"

        // Parse the URI
        val credential = PushUriParser.parse(uriWithEncodedParams)
        
        // Format the credential back to a URI
        val formattedUri = PushUriParser.format(credential)

        // Verify the reformatted URI can be parsed again
        val reparsedCredential = PushUriParser.parse(formattedUri)

        // Check that credentials match
        assertEquals("test-user@example.com", credential.userId)
        assertEquals("resource-123456", credential.resourceId)
        assertEquals("#ff0000", credential.backgroundColor)
        assertEquals("ForgeRock", credential.issuer)
        assertEquals("john.doe", credential.accountName)

        // Check that reparsed credentials match original
        assertEquals(credential.userId, reparsedCredential.userId)
        assertEquals(credential.resourceId, reparsedCredential.resourceId)
        assertEquals(credential.backgroundColor, reparsedCredential.backgroundColor)
        assertEquals(credential.issuer, reparsedCredential.issuer)
        assertEquals(credential.accountName, reparsedCredential.accountName)
        assertEquals(credential.serverEndpoint, reparsedCredential.serverEndpoint)
        assertEquals(credential.sharedSecret, reparsedCredential.sharedSecret)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun `test parse URI with missing registration endpoint`() {
        val uri = "pushauth://push/forgerock:user?a=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&s=b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E"
        PushUriParser.parse(uri) // Should throw IllegalArgumentException
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun `test parse URI with missing authentication endpoint`() {
        val uri = "pushauth://push/forgerock:user?r=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy&s=b3uYLkQ7dRPjBaIzV0t_aijoXRgMq-NP5AwVAvRfa_E"
        PushUriParser.parse(uri) // Should throw IllegalArgumentException
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun `test parse URI with missing shared secret`() {
        val uri = "pushauth://push/forgerock:user?a=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPWF1dGhlbnRpY2F0ZQ&r=aHR0cDovL2Rldi5vcGVuYW0uZXhhbXBsZS5jb206ODA4MS9vcGVuYW0vanNvbi9kZXYvcHVzaC9zbnMvbWVzc2FnZT9fYWN0aW9uPXJlZ2lzdGVy"
        PushUriParser.parse(uri) // Should throw IllegalArgumentException
    }
}
