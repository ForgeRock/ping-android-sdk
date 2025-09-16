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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.id.DeviceIdentifier
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
import java.io.File
import java.security.KeyStore
import java.security.PublicKey
import java.security.cert.Certificate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class DeviceProfileCallbackTest {

    private val mockContext = mockk<Context>()

    @BeforeTest
    fun setup() {
        every { mockContext.applicationContext } returns mockContext
        ContextProvider.init(mockContext)
        setupCameraMocks()
        setupWindowManagerMocks()
        setupStorageMocks()
        setupMemoryMocks()
        setupConnectivityConnectivityMocks()
        setupTelephonyCollectorMocks()
        setupLocationCollectionMocks()
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(Environment::class)
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
        unmockkStatic(LocationServices::class)
        unmockkStatic(KeyStore::class)
    }

    @Test
    fun `collect returns config with custom collector`() = runTest {
        val callback = DeviceProfileCallback()
        callback.collect {
            deviceIdentifier = object : DeviceIdentifier {
                override val id: suspend () -> String = {
                    "test-device-id"
                }
            }
            logger = Logger.WARN

            metadata {
                add(DummyDeviceCollector())
                add(DeviceCollector("test") {
                    Dummy2("testName2", "testValue2")
                })
                add(Dummy2DeviceCollector)
                add(DeviceCollector<Dummy2>("test3") {
                    null
                })
            }
            metadata {
                add(PlatformCollector)
                add(HardwareCollector())
            }
        }
    }

    @Test
    fun `collect returns config with default`() = runTest {
        setupKeyStoreMocks()
        val callback = DeviceProfileCallback()
        callback.collect()
    }

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

    private fun setupCameraMocks() {
        val mockCameraManager = mockk<CameraManager>()
        val mockCameraIdList = arrayOf("camera1", "camera2")
        every { mockContext.getSystemService(Context.CAMERA_SERVICE) } returns mockCameraManager
        every { mockCameraManager.cameraIdList } returns mockCameraIdList
    }

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

    private fun setupStorageMocks() {
        mockkStatic(Environment::class)
        mockkConstructor(StatFs::class)
        val mockFile = mockk<File>()
        every { mockFile.path } returns "/fake/path"
        every { Environment.getDataDirectory() } returns mockFile
    }

    private fun setupMemoryMocks() {
        val mockActivityManager = mockk<ActivityManager>()
        every { mockContext.getSystemService(Context.ACTIVITY_SERVICE) } returns mockActivityManager
        every { mockActivityManager.getMemoryInfo(any()) } answers {
            val memoryInfo = it.invocation.args[0] as ActivityManager.MemoryInfo
            memoryInfo.totalMem = 4096
        }
    }

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

    private fun setupTelephonyCollectorMocks() {
        val mockTelephonyManager = mockk<TelephonyManager>()
        every {
            mockContext.getSystemService(Context.TELEPHONY_SERVICE)
        } returns mockTelephonyManager
        every { mockTelephonyManager.networkCountryIso } returns "Canada"
        every { mockTelephonyManager.networkOperatorName } returns "Telus"
    }

    private fun setupLocationCollectionMocks() {
        val mockLocationClient = mockk<FusedLocationProviderClient>()
        mockkStatic(LocationServices::class)
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        every { LocationServices.getFusedLocationProviderClient(mockContext) } returns mockLocationClient
        every { mockContext.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
    }
}

class DummyDeviceCollector : DeviceCollector<Dummy> {
    override val key: String = "dummyCollector"
    override val serializer = Dummy.serializer()
    override suspend fun collect(): Dummy {
        return Dummy("testName", "testValue")
    }
}

val Dummy2DeviceCollector = DeviceCollector("dummy2") {
    Dummy2("testName2", "testValue2")
}

@Serializable
data class Dummy(var name: String, var value: String)

@Serializable
data class Dummy2(var name: String, var value: String)