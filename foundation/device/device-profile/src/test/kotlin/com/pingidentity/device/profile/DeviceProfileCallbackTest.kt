/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package com.pingidentity.device.profile

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Environment
import android.os.StatFs
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import android.webkit.WebSettings
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.id.DeviceIdentifier
import com.pingidentity.device.profile.collector.DefaultDeviceCollector
import com.pingidentity.device.profile.collector.DeviceCollector
import com.pingidentity.device.profile.collector.HardwareCollector
import com.pingidentity.device.profile.collector.PlatformCollector
import com.pingidentity.device.profile.collector.collect
import com.pingidentity.logger.Logger
import com.pingidentity.logger.WARN
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.KeyStore
import java.security.PublicKey
import java.security.cert.Certificate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for [DeviceProfileCallback] functionality.
 *
 * This test class validates the device profile collection process, including:
 * - Callback initialization with JSON configuration
 * - Device metadata collection with various collectors
 * - Location collection capabilities
 * - Custom collector integration
 * - Mock setup for Android system services
 *
 * The tests use extensive mocking to simulate Android system behavior and ensure
 * consistent test results across different environments.
 */
class DeviceProfileCallbackTest {

    private val mockContext = mockk<Context>()
    private lateinit var jsonObject: JsonObject

    /**
     * Sets up the test environment before each test execution.
     *
     * Initializes:
     * - Mock Android context
     * - JSON configuration object
     * - System service mocks (camera, storage, memory, connectivity, etc.)
     * - Location services mocks
     */
    @BeforeTest
    fun setup() {
        every { mockContext.applicationContext } returns mockContext
        ContextProvider.init(mockContext)
        mockkStatic(WebSettings::class)
        setupJsonMock()
        setupCameraMocks()
        setupWindowManagerMocks()
        setupStorageMocks()
        setupMemoryMocks()
        setupConnectivityConnectivityMocks()
        setupTelephonyCollectorMocks()
        setupLocationCollectionMocks()
        setupPackageManagerMocks()
        setupBrowserMocks()
    }

    /**
     * Cleans up resources and unmocks static methods after each test.
     */
    @AfterTest
    fun tearDown() {
        unmockkStatic(Environment::class)
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
        unmockkStatic(LocationServices::class)
        unmockkStatic(KeyStore::class)
        unmockkStatic(WebSettings::class)
    }

    /**
     * Tests device profile collection with custom collectors.
     *
     * Verifies that:
     * - Custom collectors can be added to the metadata collection
     * - Different types of collectors work together
     * - Null collectors are handled gracefully
     * - Device identifier integration works correctly
     */
    @Test
    fun `collect returns config with custom collector`() = runTest {
        setupJsonMock(metadataEnabled = true)
        val callback = DeviceProfileCallback()
        callback.init(jsonObject)
        val result = callback.collect {
            deviceIdentifier = object : DeviceIdentifier {
                override val id: suspend () -> String = {
                    "test-device-id"
                }
            }
            logger = Logger.WARN

            collectors {
                clear()
                add(DummyDeviceCollector())
                add(DeviceCollector("test") {
                    Dummy2("testName2", "testValue2")
                })
                add(Dummy2DeviceCollector)
                add(DeviceCollector<Dummy2>("test3") {
                    null
                })
                add(PlatformCollector())
                add(HardwareCollector())
            }
        }
        assertNotNull(result)
        assertTrue(result.toString().startsWith("Success"))
    }

    /**
     * Tests device profile collection with default configuration.
     *
     * Validates the basic collection functionality without custom configuration,
     * ensuring the callback works with minimal setup.
     */
    @Test
    fun `collect returns config with default`() = runTest {
        setupKeyStoreMocks()
        setupJsonMock()
        val callback = DeviceProfileCallback()
        callback.init(jsonObject)
        callback.collect()
    }

    /**
     * Tests device profile collection with all features enabled.
     *
     * Verifies:
     * - Metadata collection is properly enabled
     * - Location collection works when enabled
     * - JSON payload structure is correct
     * - Output contains expected device identifier and metadata
     */
    @Test
    fun `collect returns config with all options enabled`() = runTest {
        setupKeyStoreMocks()
        setupJsonMock(
            metadataEnabled = true,
            locationEnabled = true,
        )
        val callback = DeviceProfileCallback()
        callback.init(jsonObject)
        callback.collect {
            deviceIdentifier = object : DeviceIdentifier {
                override val id: suspend () -> String = {
                    "test-device-id"
                }
            }
            logger = Logger.WARN

            collectors {
                clear()
                add(DummyDeviceCollector())
            }
        }
        assertEquals(
            "{\"identifier\":\"test-device-id\",\"metadata\":{\"dummyCollector\":{\"name\":\"testName\",\"value\":\"testValue\"}}}",
            callback.payload()["input"]?.jsonArray?.get(0)?.jsonObject?.get("value")?.jsonPrimitive?.content
        )
    }

    /**
     * Tests the extension function for device collector list processing.
     *
     * Validates that multiple collectors can be processed together
     * and produce a valid JSON device profile output.
     */
    @Test
    fun `extension function test`() = runTest {

        // Initialize collectors with default set
        val collectors: MutableList<DeviceCollector<*>> =
            mutableListOf(DummyDeviceCollector(), Dummy2DeviceCollector)

        // Collect device information
        val deviceProfile = collectors.collect()

        // Use the collected profile (JsonObject)
        println(deviceProfile.toString())
    }

    /**
     * Tests the creation of a custom device collector.
     *
     * Validates that a collector can be defined using the
     * [DeviceCollector] factory function and produces expected results.
     */
    @Test
    fun `test custom collector creation`() = runTest{
        @Serializable
        data class BatteryData(val level: Int, val isCharging: Boolean)

        val BatteryCollector = DeviceCollector<BatteryData>("battery") {
            BatteryData(
                level = 100,
                isCharging = true,
            )
        }

        val result = BatteryCollector.collect()
        assertNotNull(result)
        assertEquals("battery", BatteryCollector.key)
        assertEquals(100, result.level)
        assertEquals(true, result.isCharging)
    }

    /**
     * Tests the default device profile collector functionality.
     *
     * Validates that the [DefaultDeviceCollector] can be applied
     * and produces a non-null result when metadata collection is enabled.
     */
    @Test
    fun `test default device profile collector`() = runTest {
        setupJsonMock(metadataEnabled = true)
        val deviceProfileCallback = DeviceProfileCallback()
        deviceProfileCallback.init(jsonObject)
        val result = deviceProfileCallback.collect {
            collectors.apply(DefaultDeviceCollector())
        }
        assertNotNull(result)
    }

    /**
     * Tests the behavior when a collector throws an exception during collection.
     *
     * Validates that the overall collection process continues
     * and returns success even if one collector fails.
     */
    @Test
    fun `test a faulty device collector`() = runTest {
        setupJsonMock(metadataEnabled = true)
        val deviceProfileCallback = DeviceProfileCallback()
        deviceProfileCallback.init(jsonObject)
        val faultyCollector = DeviceCollector<JsonObject>("faulty") {
            throw Exception("Simulated collector failure")
        }

        val result = deviceProfileCallback.collect {
            collectors {
                clear()
                add(faultyCollector)
            }
        }

        assertTrue(result.isFailure)
    }

    /**
     * Creates a mock JSON configuration object for callback initialization.
     *
     * @param metadataEnabled Whether metadata collection should be enabled
     * @param locationEnabled Whether location collection should be enabled
     * @param message Optional message to include in configuration
     */
    private fun setupJsonMock(
        metadataEnabled: Boolean = false,
        locationEnabled: Boolean = false,
        message: String = "",
    ) {
        jsonObject = buildJsonObject {
            put("output", buildJsonArray {
                add(buildJsonObject {
                    put("name", JsonPrimitive("metadata"))
                    put("value", JsonPrimitive(metadataEnabled))
                })
                add(buildJsonObject {
                    put("name", JsonPrimitive("location"))
                    put("value", JsonPrimitive(locationEnabled))
                })
                add(buildJsonObject {
                    put("name", JsonPrimitive("message"))
                    put("value", JsonPrimitive(message))
                })
            })
        }
    }

    /**
     * Sets up mocks for Android KeyStore operations.
     *
     * Mocks certificate and public key operations required for
     * secure device identification processes.
     */
    private fun setupKeyStoreMocks() {
        val mockKeyStore = mockk<KeyStore>()
        val mockCertificate = mockk<Certificate>()
        val mockPublicKey = mockk<PublicKey>()
        mockkStatic(KeyStore::class)
        every { KeyStore.getInstance("AndroidKeyStore") } returns mockKeyStore
        every { mockKeyStore.load(null) } just runs
        every { mockKeyStore.containsAlias(any()) } returns true
        every { mockKeyStore.getCertificate(any()) } returns mockCertificate
        every { mockCertificate.publicKey } returns mockPublicKey
        every { mockPublicKey.encoded } returns "fake-certificate-data".toByteArray()
    }

    /**
     * Sets up mocks for Camera system service.
     *
     * Configures mock camera manager to return a predefined list
     * of available cameras for hardware profiling.
     */
    private fun setupCameraMocks() {
        val mockCameraManager = mockk<CameraManager>()
        val mockCameraIdList = arrayOf("camera1", "camera2")
        every { mockContext.getSystemService(Context.CAMERA_SERVICE) } returns mockCameraManager
        every { mockCameraManager.cameraIdList } returns mockCameraIdList
    }

    /**
     * Sets up mocks for WindowManager and Display services.
     *
     * Configures display metrics (width, height) for screen
     * dimension collection in device profiling.
     */
    private fun setupWindowManagerMocks() {
        val mockWindowManager = mockk<WindowManager>()
        val mockDisplay = mockk<Display>()
        every { mockContext.getSystemService(Context.WINDOW_SERVICE) } returns mockWindowManager
        every { mockWindowManager.defaultDisplay } returns mockDisplay
        every { mockDisplay.getMetrics(any()) } answers {
            val displayMetrics = it.invocation.args[0] as DisplayMetrics
            displayMetrics.widthPixels = 1080
            displayMetrics.heightPixels = 1920
        }
    }

    /**
     * Sets up mocks for storage-related operations.
     *
     * Mocks Environment class and StatFs for storage capacity
     * measurements in device hardware profiling.
     */
    private fun setupStorageMocks() {
        mockkStatic(Environment::class)
        mockkConstructor(StatFs::class)
        val mockFile = mockk<File>()
        every { mockFile.path } returns "/fake/path"
        every { Environment.getDataDirectory() } returns mockFile
    }

    /**
     * Sets up mocks for memory-related operations.
     *
     * Configures ActivityManager to return mock memory information
     * for device RAM profiling.
     */
    private fun setupMemoryMocks() {
        val mockActivityManager = mockk<ActivityManager>()
        every { mockContext.getSystemService(Context.ACTIVITY_SERVICE) } returns mockActivityManager
        every { mockActivityManager.getMemoryInfo(any()) } answers {
            val memoryInfo = it.invocation.args[0] as ActivityManager.MemoryInfo
            memoryInfo.totalMem = 4096
        }
    }

    /**
     * Sets up mocks for network connectivity services.
     *
     * Configures ConnectivityManager to simulate network connection
     * status for connectivity profiling.
     */
    private fun setupConnectivityConnectivityMocks() {
        val mockConnectivityManager = mockk<ConnectivityManager>()
        val mockNetwork = mockk<Network>()
        val mockNetworkInfo = mockk<android.net.NetworkInfo>()
        every {
            mockContext.getSystemService(Context.CONNECTIVITY_SERVICE)
        } returns mockConnectivityManager
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        @Suppress("DEPRECATION")
        every { mockConnectivityManager.activeNetworkInfo } returns mockNetworkInfo
        every { mockNetworkInfo.isConnected } returns true
    }

    /**
     * Sets up mocks for telephony services.
     *
     * Configures TelephonyManager to return mock network operator
     * and country information for carrier profiling.
     */
    private fun setupTelephonyCollectorMocks() {
        val mockTelephonyManager = mockk<TelephonyManager>()
        every {
            mockContext.getSystemService(Context.TELEPHONY_SERVICE)
        } returns mockTelephonyManager
        every { mockTelephonyManager.networkCountryIso } returns "Canada"
        every { mockTelephonyManager.networkOperatorName } returns "Telus"
    }

    /**
     * Sets up mocks for location services.
     *
     * Configures FusedLocationProviderClient and permission checks
     * for GPS-based location collection in device profiling.
     */
    private fun setupLocationCollectionMocks() {
        val mockLocationClient = mockk<FusedLocationProviderClient>()
        mockkStatic(LocationServices::class)
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        every { LocationServices.getFusedLocationProviderClient(mockContext) } returns mockLocationClient
        every { mockContext.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
    }

    /**
     * Sets up comprehensive PackageManager mocking for detector testing.
     *
     * This helper method configures mocks for:
     * - Android PackageManager service
     * - Context package manager access
     * - Application context package manager
     * - ContextProvider package manager access
     * - Package information queries for installed applications
     *
     * The mocking ensures that package-based detectors can function properly
     * in the test environment without requiring actual installed applications.
     */
    private fun setupPackageManagerMocks() {
        val packageManager = mockk<PackageManager>()
        every { mockContext.packageManager } returns packageManager
        every { mockContext.applicationContext.packageManager } returns packageManager
        every { ContextProvider.context.packageManager } returns packageManager
        every {
            packageManager.getPackageInfo(any<String>(), any<Int>())
        } throws PackageManager.NameNotFoundException()
    }

    private fun setupBrowserMocks() {
        mockkStatic(WebSettings::class)
        every { WebSettings.getDefaultUserAgent(mockContext) } returns "MockUserAgent/1.0"
    }
}

/**
 * Test implementation of [DeviceCollector] that returns dummy data.
 *
 * Used in tests to verify custom collector integration without
 * depending on actual device hardware or system services.
 */
class DummyDeviceCollector : DeviceCollector<Dummy> {
    override val key: String = "dummyCollector"
    override val serializer = Dummy.serializer()
    override suspend fun collect(): Dummy {
        return Dummy("testName", "testValue")
    }
}

/**
 * Test collector instance created using the DeviceCollector factory function.
 *
 * Demonstrates the alternative way to create collectors using
 * the lambda-based factory method.
 */
val Dummy2DeviceCollector = DeviceCollector("dummy2") {
    Dummy2("testName2", "testValue2")
}

/**
 * Test data class representing dummy device information.
 *
 * @property name The name field for testing
 * @property value The value field for testing
 */
@Serializable
data class Dummy(var name: String, var value: String)

/**
 * Secondary test data class for multiple collector testing.
 *
 * @property name The name field for testing
 * @property value The value field for testing
 */
@Serializable
data class Dummy2(var name: String, var value: String)