/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile.collector

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import android.webkit.WebSettings
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.id.DeviceIdentifier
import com.pingidentity.device.profile.DeviceProfileConfig
import com.pingidentity.logger.Logger
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit test class for [DeviceProfileCollector].
 *
 * This test class validates the functionality of the DeviceProfileCollector, which is responsible
 * for orchestrating the collection of device profile data including device identification,
 * metadata, and location information. The tests cover various configuration scenarios and
 * ensure proper handling of optional components.
 *
 * The test setup includes comprehensive mocking of Android system services and components
 * required for device profile collection, including camera, window manager, storage,
 * memory, connectivity, telephony, and location services.
 */
class DeviceProfileCollectorTest {

    private val mockContext = mockk<Context>()
    private val mockDeviceIdentifier = mockk<DeviceIdentifier>()
    private val mockLogger = mockk<Logger>()
    private val mockFusedLocationClient = mockk<FusedLocationProviderClient>()
    private val mockLocationTask = mockk<Task<Location>>()
    private val mockLocation = mockk<Location>()

    /**
     * Sets up the test environment before each test.
     *
     * Initializes the ContextProvider with a mock context and configures the device identifier
     * to return a test ID. Also sets up comprehensive mocks for all Android system services
     * that may be accessed during device profile collection.
     */
    @BeforeTest
    fun setup() {
        every { mockContext.applicationContext } returns mockContext
        ContextProvider.init(mockContext)
        coEvery { mockDeviceIdentifier.id.invoke() } returns "test-device-id-12345"
        mockkStatic(WebSettings::class)
        setupCameraMocks()
        setupWindowManagerMocks()
        setupStorageMocks()
        setupMemoryMocks()
        setupConnectivityConnectivityMocks()
        setupTelephonyCollectorMocks()
        setupLocationCollectionMocks()
        setupPackageManagerMocks()
    }

    /**
     * Cleans up all mocks after each test to prevent interference between tests.
     */
    @AfterTest
    fun tearDown() {
        unmockkStatic(WebSettings::class)
        unmockkAll()
    }

    /**
     * Tests that the DeviceProfileCollector has an empty key identifier.
     *
     * The collector serves as the root coordinator and doesn't need a specific key
     * since it orchestrates other collectors rather than collecting specific data itself.
     */
    @Test
    fun `collector has empty key`() {
        val config = DeviceProfileConfig().apply {
            deviceIdentifier = mockDeviceIdentifier
        }
        val collector = DeviceProfileCollector(config)

        assertEquals("", collector.key)
    }

    /**
     * Tests that the DeviceProfileCollector uses the correct serializer.
     *
     * Verifies that the collector is configured with the appropriate serializer
     * for converting DeviceProfileResult objects to JSON format.
     */
    @Test
    fun `collector has correct serializer`() {
        val config = DeviceProfileConfig().apply {
            deviceIdentifier = mockDeviceIdentifier
        }
        val collector = DeviceProfileCollector(config)

        assertNotNull(collector.serializer)
        assertEquals(DeviceProfileResult.serializer(), collector.serializer)
    }

    /**
     * Tests collection behavior when both metadata and location are disabled.
     *
     * When neither metadata nor location collection is enabled, the collector
     * should only return the device identifier without additional data.
     */
    @Test
    fun `collect returns identifier only when metadata and location disabled`() = runTest {
        val config = DeviceProfileConfig().apply {
            deviceIdentifier = mockDeviceIdentifier
            metadata = false
            location = false
        }
        val collector = DeviceProfileCollector(config)

        val result = collector.collect()

        assertEquals("test-device-id-12345", result.identifier)
        assertNull(result.metadata)
        assertNull(result.location)
    }

    /**
     * Tests collection behavior when metadata collection is enabled.
     *
     * When metadata collection is enabled, the result should include the device
     * identifier and metadata information, but no location data.
     */
    @Test
    fun `collect includes metadata when enabled`() = runTest {
        every { WebSettings.getDefaultUserAgent(mockContext) } returns "MockUserAgent/1.0"
        every { mockContext.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
        val config = DeviceProfileConfig().apply {
            deviceIdentifier = mockDeviceIdentifier
            metadata = true
            location = false
        }
        val collector = DeviceProfileCollector(config)

        val result = collector.collect()

        assertEquals("test-device-id-12345", result.identifier)
        assertNotNull(result.metadata)
        assertNull(result.location)
    }

    /**
     * Tests collection behavior when location collection is enabled.
     *
     * When location collection is enabled and permissions are granted,
     * the result should include the device identifier and location data,
     * but no metadata.
     */
    @Test
    fun `collect includes location when enabled`() = runTest {
        every { mockContext.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
        coEvery { mockLocationTask.await() } returns mockLocation

        val config = DeviceProfileConfig().apply {
            deviceIdentifier = mockDeviceIdentifier
            metadata = false
            location = true
        }
        val collector = DeviceProfileCollector(config)

        val result = collector.collect()

        assertEquals("test-device-id-12345", result.identifier)
        assertNull(result.metadata)
        assertNotNull(result.location)
    }

    /**
     * Tests collection behavior when both metadata and location are enabled.
     *
     * When both collection types are enabled, the result should include
     * the device identifier, metadata, and location information.
     */
    @Test
    fun `collect includes both metadata and location when both enabled`() = runTest {
        every { WebSettings.getDefaultUserAgent(mockContext) } returns "MockUserAgent/1.0"
        every { mockContext.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
        coEvery { mockLocationTask.await() } returns mockLocation
        val config = DeviceProfileConfig().apply {
            deviceIdentifier = mockDeviceIdentifier
            metadata = true
            location = true
        }
        val collector = DeviceProfileCollector(config)

        val result = collector.collect()

        assertEquals("test-device-id-12345", result.identifier)
        assertNotNull(result.metadata)
        assertNotNull(result.location)
    }

    /**
     * Tests that the collector properly configures loggers for LoggerAware collectors.
     *
     * Collectors that implement LoggerAware should receive the logger configured
     * in the DeviceProfileConfig during the collection process.
     */
    @Test
    fun `collect configures logger for LoggerAware collectors`() = runTest {
        val loggerAwareCollector = spyk<TestLoggerAwareCollector>()
        val config = DeviceProfileConfig().apply {
            deviceIdentifier = mockDeviceIdentifier
            logger = mockLogger
            collectors.add(loggerAwareCollector)
            metadata = false
            location = false
        }
        val collector = DeviceProfileCollector(config)

        collector.collect()

        assertEquals(mockLogger, loggerAwareCollector.logger)
    }

    /**
     * Tests that the collector handles non-LoggerAware collectors gracefully.
     *
     * Collectors that don't implement LoggerAware should still function normally
     * without causing errors during the collection process.
     */
    @Test
    fun `collect handles non-LoggerAware collectors gracefully`() = runTest {
        val regularCollector = TestRegularCollector()
        val config = DeviceProfileConfig().apply {
            deviceIdentifier = mockDeviceIdentifier
            logger = mockLogger
            collectors.add(regularCollector)
            metadata = false
            location = false
        }
        val collector = DeviceProfileCollector(config)

        val result = collector.collect()

        // Should complete without error
        assertEquals("test-device-id-12345", result.identifier)
    }

    /**
     * Tests DeviceProfileResult data class with all fields populated.
     *
     * Verifies that the DeviceProfileResult can be created with all optional
     * fields (metadata and location) and properly stores the provided values.
     */
    @Test
    fun `DeviceProfileResult serialization includes all fields`() {
        val mockMetadata = mockk<JsonElement>()
        val mockLocation = mockk<LocationInfo>()

        val result = DeviceProfileResult(
            identifier = "test-id",
            metadata = mockMetadata,
            location = mockLocation,
            version = "1.0"
        )

        assertEquals("test-id", result.identifier)
        assertEquals(mockMetadata, result.metadata)
        assertEquals(mockLocation, result.location)
        assertEquals("1.0", result.version)
    }

    /**
     * Tests DeviceProfileResult data class with optional fields as null.
     *
     * Verifies that the DeviceProfileResult can be created with only the required
     * identifier field, with optional metadata and location fields set to null.
     */
    @Test
    fun `DeviceProfileResult can be created with optional fields null`() {
        val result = DeviceProfileResult(identifier = "test-id", version = "1.0")

        assertEquals("test-id", result.identifier)
        assertNull(result.metadata)
        assertNull(result.location)
        assertEquals("1.0", result.version)
    }

    /**
     * Test helper class implementing LoggerAware interface.
     *
     * Used to verify that the collector properly configures loggers
     * for collectors that support logging functionality.
     */
    private class TestLoggerAwareCollector : DeviceCollector<String>, LoggerAware {
        override val key: String = "test-logger-aware"
        override val serializer = mockk<kotlinx.serialization.KSerializer<String>>()
        override lateinit var logger: Logger
        override suspend fun collect(): String = "test-result"
    }

    /**
     * Test helper class for regular collectors without logging support.
     *
     * Used to verify that the collector handles non-LoggerAware collectors
     * without causing errors or exceptions.
     */
    private class TestRegularCollector : DeviceCollector<String> {
        override val key: String = "test-regular"
        override val serializer = mockk<kotlinx.serialization.KSerializer<String>>()
        override suspend fun collect(): String = "test-result"
    }

    /**
     * Sets up mocks for camera-related functionality.
     *
     * Configures mock CameraManager and camera ID list to simulate
     * device camera capabilities during hardware profiling.
     */
    private fun setupCameraMocks() {
        val mockCameraManager = mockk<CameraManager>()
        val mockCameraIdList = arrayOf("camera1", "camera2")
        every { mockContext.getSystemService(Context.CAMERA_SERVICE) } returns mockCameraManager
        every { mockCameraManager.cameraIdList } returns mockCameraIdList
    }

    /**
     * Sets up mocks for window manager and display functionality.
     *
     * Configures mock WindowManager and Display objects to provide
     * screen dimension information during hardware profiling.
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
     * Sets up mocks for storage-related functionality.
     *
     * Configures mock Environment and StatFs objects to simulate
     * device storage information during hardware profiling.
     */
    private fun setupStorageMocks() {
        mockkStatic(Environment::class)
        mockkConstructor(StatFs::class)
        val mockFile = mockk<File>()
        every { mockFile.path } returns "/fake/path"
        every { Environment.getDataDirectory() } returns mockFile
    }

    /**
     * Sets up mocks for memory-related functionality.
     *
     * Configures mock ActivityManager to provide memory information
     * during hardware profiling operations.
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
     * Sets up mocks for network connectivity functionality.
     *
     * Configures mock ConnectivityManager, Network, and NetworkInfo objects
     * to simulate network connectivity state during network profiling.
     */
    private fun setupConnectivityConnectivityMocks() {
        val mockConnectivityManager = mockk<ConnectivityManager>()
        val mockNetwork = mockk<Network>()
        val mockNetworkCapabilities = mockk<NetworkCapabilities>()
        every {
            mockContext.getSystemService(Context.CONNECTIVITY_SERVICE)
        } returns mockConnectivityManager
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockNetworkCapabilities
        every { mockNetworkCapabilities.hasCapability(any()) } returns true
    }

    /**
     * Sets up mocks for telephony-related functionality.
     *
     * Configures mock TelephonyManager to provide network country ISO
     * and carrier information during telephony profiling.
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
     * Sets up mocks for location services functionality.
     *
     * Configures mock FusedLocationProviderClient, location tasks, and
     * location objects to simulate GPS location collection capabilities.
     */
    private fun setupLocationCollectionMocks() {
        mockkStatic(LocationServices::class)
        // Mock the file containing the await() extension function
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")

        every { LocationServices.getFusedLocationProviderClient(mockContext) } returns mockFusedLocationClient
        every { mockFusedLocationClient.lastLocation } returns mockLocationTask

        every { mockLocation.latitude } returns MOCK_LATITUDE
        every { mockLocation.longitude } returns MOCK_LONGITUDE
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

    companion object {
        /** Mock latitude coordinate for testing location functionality */
        private const val MOCK_LATITUDE = 49.2827
        /** Mock longitude coordinate for testing location functionality */
        private const val MOCK_LONGITUDE = -123.1207
    }
}
