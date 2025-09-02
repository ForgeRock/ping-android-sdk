/*
 * Copyright (c)  2025 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.device.profile

import android.app.ActivityManager
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.profile.collector.HardwareCollector
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.mockkConstructor
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for [HardwareCollector] to verify hardware information collection functionality.
 *
 * This test class covers various scenarios including:
 * - Successful hardware data collection with all components available
 * - Error handling when camera access fails
 * - Display metrics collection and error cases
 * - Storage and memory calculation verification
 * - CPU core count detection
 *
 * The tests use MockK to mock Android system services and Build properties
 * to isolate the hardware collection logic from platform dependencies.
 *
 * **Test Architecture:**
 * - Uses dependency injection with [AndroidBuildProvider] for testable Build property access
 * - Mocks Android system services (WindowManager, CameraManager, ActivityManager)
 * - Uses constructor mocking for StatFs to test storage calculations
 * - Isolates each hardware component for focused testing
 */
class HardwareCollectorTest {
    private val mockContext = mockk<Context>()
    private val mockWindowManager = mockk<WindowManager>()
    private val mockDisplay = mockk<Display>()
    private val mockCameraManager = mockk<CameraManager>()
    private val mockActivityManager = mockk<ActivityManager>()
    private val mockAndroidBuildProvider = mockk<AndroidBuildProvider>()
    private val mockFile = mockk<File>()

    // Use the mock provider instead of the default one for testability
    private val hardwareCollector = HardwareCollector(mockAndroidBuildProvider)

    /**
     * Sets up mock objects and stubs for each test case.
     *
     * Configures mocks for:
     * - Android Context and system services through [ContextProvider]
     * - Build properties for device information via static mocking
     * - File system and environment access for storage calculations
     * - StatFs constructor mocking for filesystem statistics
     * - Runtime for CPU core information
     */
    @BeforeTest
    fun setUp() {
        mockkObject(ContextProvider)
        mockkStatic(Build::class)
        mockkStatic(Environment::class)
        mockkStatic(Runtime::class)
        mockkConstructor(StatFs::class)

        // Setup basic context mocking
        every { ContextProvider.context } returns mockContext
    }

    /**
     * Cleans up all mocks after each test to prevent interference between tests.
     * This ensures test isolation and prevents mock state from leaking between test cases.
     */
    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Verifies that the hardware collector uses the correct key identifier.
     *
     * The key is used to identify this collector's data in the device profile.
     */
    @Test
    fun `Hardware collector has correct key`() {
        assertEquals("hardware", hardwareCollector.key)
    }

    /**
     * Verifies successful hardware information collection when all components are available.
     *
     * Tests the complete flow of gathering:
     * - Display metrics (width and height in pixels)
     * - Camera information (number of available cameras)
     * - Storage capacity calculation (bytes to GB conversion)
     * - Memory information (bytes to MB conversion)
     * - CPU core count from Runtime
     *
     * This represents the happy path where all Android system services are accessible
     * and return valid data.
     */
    @Test
    fun `Hardware collector returns complete info when all data is available`() = runTest {
        // Mock Build properties through the provider
        every { mockAndroidBuildProvider.getHardware() } returns "test_hardware"
        every { mockAndroidBuildProvider.getManufacturer() } returns "TestManufacturer"

        // Mock display collection
        every { mockContext.getSystemService(Context.WINDOW_SERVICE) } returns mockWindowManager
        every { mockWindowManager.defaultDisplay } returns mockDisplay
        every { mockDisplay.getMetrics(any()) } answers {
            val metrics = firstArg<DisplayMetrics>()
            metrics.widthPixels = 1080
            metrics.heightPixels = 1920
        }

        // Mock camera collection
        every { mockContext.getSystemService(Context.CAMERA_SERVICE) } returns mockCameraManager
        every { mockCameraManager.cameraIdList } returns arrayOf("0", "1")

        // Mock storage info
        every { Environment.getDataDirectory() } returns mockFile
        every { mockFile.path } returns "/data"
        every { anyConstructed<StatFs>().blockSizeLong } returns 4096L
        every { anyConstructed<StatFs>().blockCountLong } returns 26214400L // ~100GB

        // Mock memory info
        every { mockContext.getSystemService(Context.ACTIVITY_SERVICE) } returns mockActivityManager
        every { mockActivityManager.getMemoryInfo(any()) } answers {
            val memInfo = firstArg<ActivityManager.MemoryInfo>()
            memInfo.totalMem = 8589934592L // 8GB in bytes
        }

        val result = hardwareCollector.collect()

        assertNotNull(result)
        assertEquals("test_hardware", result.hardware)
        assertEquals("TestManufacturer", result.manufacturer)
        assertEquals(100L, result.storage) // 100GB
        assertEquals(8192L, result.memory) // 8GB in MB
        assertEquals(16, result.cpu) // Default system CPU count
        assertNotNull(result.display)
        assertEquals(1080, result.display!!["width"])
        assertEquals(1920, result.display!!["height"])
        assertNotNull(result.camera)
        assertEquals(2, result.camera!!["noOfCameras"])
    }

    /**
     * Verifies hardware collection when Build properties are null.
     *
     * Tests fallback behavior for hardware and manufacturer fields when
     * the AndroidBuildProvider returns null values. This can happen on
     * devices with incomplete Build information or in testing environments.
     *
     * Expected fallbacks:
     * - hardware: "HARDWARE" (from Build.HARDWARE fallback)
     * - manufacturer: "MANUFACTURER" (from Build.MANUFACTURER fallback)
     */
    @Test
    fun `Hardware collector handles null Build properties gracefully`() = runTest {
        // Mock null Build properties through the provider
        every { mockAndroidBuildProvider.getHardware() } returns null
        every { mockAndroidBuildProvider.getManufacturer() } returns null

        // Mock other components with minimal setup
        setupMinimalMocks()

        val result = hardwareCollector.collect()

        assertNotNull(result)
        assertEquals("HARDWARE", result.hardware)
        assertEquals("MANUFACTURER", result.manufacturer)
    }

    /**
     * Verifies hardware collection when camera access fails.
     *
     * Tests error handling for [CameraAccessException] scenarios which can occur when:
     * - Camera permission is denied
     * - Camera hardware is in use by another application
     * - Camera service is unavailable
     *
     * When camera access fails, the camera field should be null while other
     * hardware information collection continues normally.
     */
    @Test
    fun `Hardware collector handles camera access exception`() = runTest {
        // Setup basic mocks
        every { mockAndroidBuildProvider.getHardware() } returns "test_hardware"
        every { mockAndroidBuildProvider.getManufacturer() } returns "TestManufacturer"

        // Mock display collection (successful)
        every { mockContext.getSystemService(Context.WINDOW_SERVICE) } returns mockWindowManager
        every { mockWindowManager.defaultDisplay } returns mockDisplay
        every { mockDisplay.getMetrics(any()) } answers {
            val metrics = firstArg<DisplayMetrics>()
            metrics.widthPixels = 1080
            metrics.heightPixels = 1920
        }

        // Mock camera collection (failure)
        every { mockContext.getSystemService(Context.CAMERA_SERVICE) } returns mockCameraManager
        every { mockCameraManager.cameraIdList } throws CameraAccessException(CameraAccessException.CAMERA_ERROR)

        setupStorageAndMemoryMocks()

        val result = hardwareCollector.collect()

        assertNotNull(result)
        assertEquals("test_hardware", result.hardware)
        assertEquals("TestManufacturer", result.manufacturer)
        assertNotNull(result.display)
        assertNull(result.camera) // Should be null due to camera access exception
    }

    /**
     * Verifies hardware collection when display access fails.
     *
     * Tests error handling for display metrics collection failures.
     * Note: The current implementation catches [CameraAccessException] for display errors,
     * which appears to be incorrect but is tested as-is for current behavior verification.
     *
     * When display access fails, the display field should be null while other
     * hardware information collection continues normally.
     */
    @Test
    fun `Hardware collector handles display access exception`() = runTest {
        // Setup basic mocks
        every { mockAndroidBuildProvider.getHardware() } returns "test_hardware"
        every { mockAndroidBuildProvider.getManufacturer() } returns "TestManufacturer"

        // Mock display collection (failure) - Note: The code catches CameraAccessException, which seems incorrect
        every { mockContext.getSystemService(Context.WINDOW_SERVICE) } returns mockWindowManager
        every { mockWindowManager.defaultDisplay } returns mockDisplay
        every { mockDisplay.getMetrics(any()) } throws CameraAccessException(CameraAccessException.CAMERA_ERROR)

        // Mock camera collection (successful)
        every { mockContext.getSystemService(Context.CAMERA_SERVICE) } returns mockCameraManager
        every { mockCameraManager.cameraIdList } returns arrayOf("0")

        setupStorageAndMemoryMocks()

        val result = hardwareCollector.collect()

        assertNotNull(result)
        assertEquals("test_hardware", result.hardware)
        assertEquals("TestManufacturer", result.manufacturer)
        assertNull(result.display) // Should be null due to display access exception
        assertNotNull(result.camera)
        assertEquals(1, result.camera!!["noOfCameras"])
    }

    /**
     * Verifies that storage calculation works correctly with different block sizes.
     *
     * Tests the storage information gathering and conversion logic:
     * - Uses [StatFs] to read filesystem statistics from the data directory
     * - Calculates total storage as: blockCount × blockSize
     * - Converts bytes to gigabytes: totalBytes ÷ (1024³)
     *
     * Test case: 13,107,200 blocks × 4,096 bytes/block = ~50GB
     */
    @Test
    fun `Storage calculation converts bytes to GB correctly`() = runTest {
        every { mockAndroidBuildProvider.getHardware() } returns "test_hardware"
        every { mockAndroidBuildProvider.getManufacturer() } returns "TestManufacturer"

        setupDisplayAndCameraMocks()

        // Mock storage with specific values for calculation verification
        every { Environment.getDataDirectory() } returns mockFile
        every { mockFile.path } returns "/data"
        every { anyConstructed<StatFs>().blockSizeLong } returns 4096L // 4KB blocks
        every { anyConstructed<StatFs>().blockCountLong } returns 13107200L // Should result in ~50GB

        setupMemoryMocks()

        val result = hardwareCollector.collect()

        assertNotNull(result)
        assertEquals(50L, result.storage) // (13107200 * 4096) / (1024^3) = ~50GB
    }

    /**
     * Verifies that memory calculation converts bytes to MB correctly.
     *
     * Tests the memory information gathering and conversion logic:
     * - Uses [ActivityManager.getMemoryInfo] to get system memory information
     * - Reads total memory in bytes from [ActivityManager.MemoryInfo.totalMem]
     * - Converts bytes to megabytes: totalBytes ÷ (1024²)
     *
     * Test case: 4,294,967,296 bytes (4GB) should convert to 4,096 MB
     */
    @Test
    fun `Memory calculation converts bytes to MB correctly`() = runTest {
        every { mockAndroidBuildProvider.getHardware() } returns "test_hardware"
        every { mockAndroidBuildProvider.getManufacturer() } returns "TestManufacturer"

        setupDisplayAndCameraMocks()
        setupStorageMocks()

        // Mock memory with specific values for calculation verification
        every { mockContext.getSystemService(Context.ACTIVITY_SERVICE) } returns mockActivityManager
        every { mockActivityManager.getMemoryInfo(any()) } answers {
            val memInfo = firstArg<ActivityManager.MemoryInfo>()
            memInfo.totalMem = 4294967296L // 4GB in bytes
        }

        val result = hardwareCollector.collect()

        assertNotNull(result)
        assertEquals(4096L, result.memory) // 4GB in MB
        assertEquals(16, result.cpu) // Default system CPU count
    }

    // Helper methods for setting up common mock scenarios

    /**
     * Sets up minimal mocks required for basic hardware collection testing.
     * Includes display, camera, storage, and memory mocks with default values.
     */
    private fun setupMinimalMocks() {
        setupDisplayAndCameraMocks()
        setupStorageAndMemoryMocks()
    }

    /**
     * Sets up mocks for display and camera collection with standard test values.
     *
     * Display: 800×600 pixels (standard test resolution)
     * Camera: Single camera with ID "0"
     */
    private fun setupDisplayAndCameraMocks() {
        // Mock display
        every { mockContext.getSystemService(Context.WINDOW_SERVICE) } returns mockWindowManager
        every { mockWindowManager.defaultDisplay } returns mockDisplay
        every { mockDisplay.getMetrics(any()) } answers {
            val metrics = firstArg<DisplayMetrics>()
            metrics.widthPixels = 800
            metrics.heightPixels = 600
        }

        // Mock camera
        every { mockContext.getSystemService(Context.CAMERA_SERVICE) } returns mockCameraManager
        every { mockCameraManager.cameraIdList } returns arrayOf("0")
    }

    /**
     * Sets up mocks for storage calculation testing.
     *
     * Configures StatFs to return test values that result in ~50GB storage:
     * - Block size: 4,096 bytes (4KB)
     * - Block count: 13,107,200 blocks
     * - Total: ~50GB
     */
    private fun setupStorageMocks() {
        every { Environment.getDataDirectory() } returns mockFile
        every { mockFile.path } returns "/data"
        every { anyConstructed<StatFs>().blockSizeLong } returns 4096L
        every { anyConstructed<StatFs>().blockCountLong } returns 13107200L // ~50GB
    }

    /**
     * Sets up mocks for memory calculation testing.
     *
     * Configures ActivityManager to return 2GB of total system memory
     * for memory conversion testing.
     */
    private fun setupMemoryMocks() {
        every { mockContext.getSystemService(Context.ACTIVITY_SERVICE) } returns mockActivityManager
        every { mockActivityManager.getMemoryInfo(any()) } answers {
            val memInfo = firstArg<ActivityManager.MemoryInfo>()
            memInfo.totalMem = 2147483648L // 2GB in bytes
        }
    }

    /**
     * Convenience method that sets up both storage and memory mocks
     * for tests that need both components.
     */
    private fun setupStorageAndMemoryMocks() {
        setupStorageMocks()
        setupMemoryMocks()
    }
}