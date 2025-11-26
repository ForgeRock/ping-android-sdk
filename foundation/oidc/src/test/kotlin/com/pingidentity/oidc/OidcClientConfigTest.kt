/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pingidentity.android.ContextProvider
import com.pingidentity.exception.ApiException
import com.pingidentity.logger.CONSOLE
import com.pingidentity.logger.Logger
import com.pingidentity.storage.EncryptedDataStoreStorageConfig
import com.pingidentity.storage.MemoryStorage
import com.pingidentity.storage.StorageDelegate
import com.pingidentity.testrail.TestRailCase
import com.pingidentity.testrail.TestRailWatcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OidcClientConfigTest {
    @JvmField
    @Rule
    val watcher: TestWatcher = TestRailWatcher

    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Application>() }

    @BeforeTest
    fun setUp() {
        ContextProvider.init(context)
    }

    @TestRailCase(22079)
    @Test
    fun `init should set openId when not initialized`() =
        runTest {
            val mockEngine =
                MockEngine { _ ->
                    respond(
                        content =
                            ByteReadChannel(
                                "{\n" +
                                        "  \"authorization_endpoint\" : \"https://auth.test-one-pingone.com/as/authorize\",\n" +
                                        "  \"token_endpoint\" : \"https://auth.test-one-pingone.com/as/token\",\n" +
                                        "  \"userinfo_endpoint\" : \"https://auth.test-one-pingone.com/as/userinfo\",\n" +
                                        "  \"end_session_endpoint\" : \"https://auth.test-one-pingone.com/as/signoff\",\n" +
                                        "  \"revocation_endpoint\" : \"https://auth.test-one-pingone.com/as/revoke\"\n" +
                                        "}",
                            ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val httpClient = HttpClient(mockEngine)
            val oidcClientConfig =
                OidcClientConfig().apply {
                    this.httpClient = httpClient
                    discoveryEndpoint = "http://localhost"
                    storage = { MemoryStorage() }
                }
            oidcClientConfig.init()

            assertEquals(
                "https://auth.test-one-pingone.com/as/authorize",
                oidcClientConfig.openId.authorizationEndpoint,
            )
            assertEquals(
                "https://auth.test-one-pingone.com/as/token",
                oidcClientConfig.openId.tokenEndpoint,
            )
            assertEquals(
                "https://auth.test-one-pingone.com/as/userinfo",
                oidcClientConfig.openId.userinfoEndpoint,
            )
            assertEquals(
                "https://auth.test-one-pingone.com/as/signoff",
                oidcClientConfig.openId.endSessionEndpoint,
            )
            assertEquals(
                "https://auth.test-one-pingone.com/as/revoke",
                oidcClientConfig.openId.revocationEndpoint,
            )
        }

    @TestRailCase(22118)
    @Test
    fun `init should throw exception when discovery fails`(): Unit =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel("Error"),
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            val httpClient = HttpClient(mockEngine)
            val oidcClientConfig =
                OidcClientConfig().apply {
                    this.httpClient = httpClient
                    discoveryEndpoint = "http://localhost"
                    storage = { MemoryStorage() }

                }

            assertFailsWith<ApiException> {
                oidcClientConfig.init()
            }
        }

    @TestRailCase(22080)
    @Test
    fun `plusAssign should copy all properties from other config`() {
        val oidcClientConfig = OidcClientConfig()
        val otherConfig =
            OidcClientConfig().apply {
                openId = OpenIdConfiguration()
                agent = mockk()
                logger = mockk()
                storage = { mockk<StorageDelegate<Token>>() }
                storage { fileName = "test" }
                discoveryEndpoint = "http://localhost"
                clientId = "clientId"
                scope("openid")
                redirectUri = "http://localhost/callback"
                loginHint = "loginHint"
                nonce = "nonce"
                display = "display"
                prompt = "prompt"
                uiLocales = "uiLocales"
                acrValues = "acrValues"
                additionalParameters = mapOf("param" to "value")
                httpClient = mockk()
            }

        oidcClientConfig += otherConfig

        assertEquals(otherConfig.openId, oidcClientConfig.openId)
        assertEquals(otherConfig.agent, oidcClientConfig.agent)
        assertEquals(otherConfig.logger, oidcClientConfig.logger)
        assertFalse(otherConfig.isTokenStorageInitialized())
        assertFalse(oidcClientConfig.isTokenStorageInitialized())
        assertEquals(otherConfig.storage, oidcClientConfig.storage)
        assertEquals(otherConfig.discoveryEndpoint, oidcClientConfig.discoveryEndpoint)
        assertEquals(otherConfig.clientId, oidcClientConfig.clientId)
        assertEquals(otherConfig.scopes, oidcClientConfig.scopes)
        assertEquals(otherConfig.redirectUri, oidcClientConfig.redirectUri)
        assertEquals(otherConfig.loginHint, oidcClientConfig.loginHint)
        assertEquals(otherConfig.nonce, oidcClientConfig.nonce)
        assertEquals(otherConfig.display, oidcClientConfig.display)
        assertEquals(otherConfig.prompt, oidcClientConfig.prompt)
        assertEquals(otherConfig.uiLocales, oidcClientConfig.uiLocales)
        assertEquals(otherConfig.acrValues, oidcClientConfig.acrValues)
        assertEquals(otherConfig.additionalParameters, oidcClientConfig.additionalParameters)
        assertEquals(otherConfig.httpClient, oidcClientConfig.httpClient)
    }

    @TestRailCase(22081)
    @Test
    fun `clone should create a new instance with same properties`() {
        val oidcClientConfig =
            OidcClientConfig().apply {
                openId = OpenIdConfiguration()
                refreshThreshold = 100
                agent = mockk()
                logger = mockk()
                storage = { mockk<StorageDelegate<Token>>() }
                discoveryEndpoint = "http://localhost"
                clientId = "clientId"
                scope("openid")
                redirectUri = "http://localhost/callback"
                signOutRedirectUri = "http://localhost/signout"
                loginHint = "loginHint"
                state = "state"
                nonce = "nonce"
                display = "display"
                prompt = "prompt"
                uiLocales = "uiLocales"
                acrValues = "acrValues"
                additionalParameters = mapOf("param" to "value")
                httpClient = mockk()
            }

        //Ensure there are 21 properties in the class for now.
        val clazz: KClass<OidcClientConfig> = OidcClientConfig::class
        assertEquals(clazz.memberProperties.size, 21)

        val clonedConfig = oidcClientConfig.clone()

        assertEquals(oidcClientConfig.openId, clonedConfig.openId)
        assertEquals(oidcClientConfig.refreshThreshold, clonedConfig.refreshThreshold)
        assertEquals(oidcClientConfig.agent, clonedConfig.agent)
        assertEquals(oidcClientConfig.logger, clonedConfig.logger)
        assertFalse(clonedConfig.isTokenStorageInitialized())
        assertFalse(oidcClientConfig.isTokenStorageInitialized())
        assertEquals(oidcClientConfig.discoveryEndpoint, clonedConfig.discoveryEndpoint)
        assertEquals(oidcClientConfig.clientId, clonedConfig.clientId)
        assertEquals(oidcClientConfig.scopes, clonedConfig.scopes)
        assertEquals(oidcClientConfig.redirectUri, clonedConfig.redirectUri)
        assertEquals(oidcClientConfig.signOutRedirectUri, clonedConfig.signOutRedirectUri)
        assertEquals(oidcClientConfig.loginHint, clonedConfig.loginHint)
        assertEquals(oidcClientConfig.state, clonedConfig.state)
        assertEquals(oidcClientConfig.nonce, clonedConfig.nonce)
        assertEquals(oidcClientConfig.display, clonedConfig.display)
        assertEquals(oidcClientConfig.prompt, clonedConfig.prompt)
        assertEquals(oidcClientConfig.uiLocales, clonedConfig.uiLocales)
        assertEquals(oidcClientConfig.acrValues, clonedConfig.acrValues)
        assertEquals(oidcClientConfig.additionalParameters, clonedConfig.additionalParameters)
        assertEquals(oidcClientConfig.httpClient, clonedConfig.httpClient)
    }

    @Test
    fun `clone should create a new instance with same properties after init`() = runTest {
        val oidcClientConfig =
            OidcClientConfig().apply {
                openId = OpenIdConfiguration()
                refreshThreshold = 100
                agent = mockk()
                logger = mockk()
                storage = { mockk<StorageDelegate<Token>>() }
                discoveryEndpoint = "http://localhost"
                clientId = "clientId"
                scope("openid")
                redirectUri = "http://localhost/callback"
                signOutRedirectUri = "http://localhost/signout"
                loginHint = "loginHint"
                state = "state"
                nonce = "nonce"
                display = "display"
                prompt = "prompt"
                uiLocales = "uiLocales"
                acrValues = "acrValues"
                additionalParameters = mapOf("param" to "value")
                httpClient = mockk()
            }
        oidcClientConfig.init()

        val clonedConfig = oidcClientConfig.clone()

        assertEquals(oidcClientConfig.openId, clonedConfig.openId)
        assertEquals(oidcClientConfig.refreshThreshold, clonedConfig.refreshThreshold)
        assertEquals(oidcClientConfig.agent, clonedConfig.agent)
        assertEquals(oidcClientConfig.logger, clonedConfig.logger)
        assertEquals(oidcClientConfig.tokenStorage, clonedConfig.tokenStorage)
        assertEquals(oidcClientConfig.discoveryEndpoint, clonedConfig.discoveryEndpoint)
        assertEquals(oidcClientConfig.clientId, clonedConfig.clientId)
        assertEquals(oidcClientConfig.scopes, clonedConfig.scopes)
        assertEquals(oidcClientConfig.redirectUri, clonedConfig.redirectUri)
        assertEquals(oidcClientConfig.signOutRedirectUri, clonedConfig.signOutRedirectUri)
        assertEquals(oidcClientConfig.loginHint, clonedConfig.loginHint)
        assertEquals(oidcClientConfig.state, clonedConfig.state)
        assertEquals(oidcClientConfig.nonce, clonedConfig.nonce)
        assertEquals(oidcClientConfig.display, clonedConfig.display)
        assertEquals(oidcClientConfig.prompt, clonedConfig.prompt)
        assertEquals(oidcClientConfig.uiLocales, clonedConfig.uiLocales)
        assertEquals(oidcClientConfig.acrValues, clonedConfig.acrValues)
        assertEquals(oidcClientConfig.additionalParameters, clonedConfig.additionalParameters)
        assertEquals(oidcClientConfig.httpClient, clonedConfig.httpClient)
    }

    @TestRailCase(22082)
    @Test
    fun `scope should add provided scope to scopes set`() {
        val oidcClientConfig = OidcClientConfig()
        val scope = "email"

        oidcClientConfig.scope(scope)

        assertTrue { oidcClientConfig.scopes.contains(scope) }
    }

    @TestRailCase(22083)
    @Test
    fun `httpclient should be initialized`() = runTest {
        val oidcClientConfig = OidcClientConfig().apply {
            storage = { mockk() }
            openId = mockk()
            logger = Logger.CONSOLE
        }
        oidcClientConfig.init()
        //Access the httpclient and make sure it is initialized
        oidcClientConfig.httpClient
    }

    @Test
    fun `storageOption should be customizable`() {
        val oidcClientConfig = OidcClientConfig()
        oidcClientConfig.storage {
            fileName = "custom_file"
        }

        val config = EncryptedDataStoreStorageConfig().apply(oidcClientConfig.storageOption)

        //Override the default file name
        Assert.assertEquals("custom_file", config.fileName)
        //Keep the default keyAlias
        Assert.assertEquals("com.pingidentity.sdk.v1.tokens", config.keyAlias)
        Assert.assertTrue(config.strongBoxPreferred)
    }
    @Test
    fun `storageOption should be accumulate customization`() {
        val oidcClientConfig = OidcClientConfig()
        oidcClientConfig.storage {
            fileName = "custom_file"
        }

        oidcClientConfig.storage {
            strongBoxPreferred = false
        }

        oidcClientConfig.storage {
            symmetricKeySize = 128
        }

        oidcClientConfig.storage {
            keyAlias = "custom_key_alias"
        }

        val config = EncryptedDataStoreStorageConfig().apply(oidcClientConfig.storageOption)

        //Override the default file name
        Assert.assertEquals("custom_file", config.fileName)
        //Keep the default keyAlias
        Assert.assertEquals("custom_key_alias", config.keyAlias)
        assertFalse(config.strongBoxPreferred)
        Assert.assertEquals(128, config.symmetricKeySize)
    }

}
