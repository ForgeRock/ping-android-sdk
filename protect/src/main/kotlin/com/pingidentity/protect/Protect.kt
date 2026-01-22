/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect

import com.pingidentity.android.ContextProvider
import com.pingidentity.signalssdk.sdk.GetDataCallback
import com.pingidentity.signalssdk.sdk.InitCallback
import com.pingidentity.signalssdk.sdk.POInitParams
import com.pingidentity.signalssdk.sdk.PingOneSignals
import com.pingidentity.utils.PingDsl
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The Protect Object provides methods to initialize the SDK, retrieve device signal data
 */
object Protect {

    private lateinit var protectConfig: ProtectConfig
    private var isInitialized: Boolean = false
    private var lock = Mutex()

    /**
     * Configures the Protect SDK with the provided configuration.
     * This method should be called before calling [initialize].
     *
     * @param config A lambda that configures the [ProtectConfig].
     */
    fun config(config: ProtectConfig.() -> Unit) {
        protectConfig = ProtectConfig().apply(config)
    }

    /**
     * Initializes the Protect SDK with the provided configuration.
     * This method should be called before using any other methods in the Protect SDK.
     *
     * @throws ProtectException if initialization fails.
     */
    suspend fun initialize(): Unit = lock.withLock {
        if (isInitialized) {
            return
        }
        return suspendCancellableCoroutine { init ->
            PingOneSignals.setInitCallback(
                object : InitCallback {
                    override fun onInitialized() {
                        isInitialized = true
                        init.resume(Unit)
                    }

                    override fun onError(
                        p0: String,
                        p1: String,
                        p2: String,
                    ) {
                        isInitialized = false
                        init.resumeWithException(ProtectException("PingOneSignals initialization failed $p0 $p1 $p2"))
                    }
                },
            )
            // The callback will not be invoked if the init is called before
            PingOneSignals.init(ContextProvider.context, POInitParams().apply {
                envId = protectConfig.envId
                deviceAttributesToIgnore = protectConfig.deviceAttributesToIgnore
                customHost = protectConfig.customHost
                isConsoleLogEnabled = protectConfig.isConsoleLogEnabled
                isLazyMetadata = protectConfig.isLazyMetadata
                isBehavioralDataCollection = protectConfig.isBehavioralDataCollection
            })
        }
    }

    /**
     * Retrieves the signal data from the Protect SDK.
     * This method should be called after [initialize] to get the data.
     *
     * @return A string containing the behavioral data.
     * @throws ProtectException if data retrieval fails.
     */
    suspend fun data(): String {
        return suspendCancellableCoroutine {
            PingOneSignals.getData(
                object : GetDataCallback {
                    override fun onSuccess(result: String) {
                        it.resume(result)
                    }

                    override fun onFailure(result: String) {
                        it.resumeWithException(ProtectException(result))
                    }
                }
            )
        }
    }

    /**
     * Pause behavioral data collection
     */
    fun pauseBehavioralData() {
        PingOneSignals.pauseBehavioralData()
    }

    /**
     * Resume behavioral data collection
     */
    fun resumeBehavioralData() {
        PingOneSignals.resumeBehavioralData()
    }

}

/**
 * Class to provide Signal SDK configuration attributes.
 */
@PingDsl
open class ProtectConfig {
    /**
     * The environment ID for the Protect SDK.
     */
    var envId: String? = null
    /**
     * A list of device attributes to ignore when collecting data.
     */
    var deviceAttributesToIgnore: List<String> = emptyList()
    /**
     * Custom host for the Protect SDK.
     */
    var customHost: String? = null
    /**
     * Whether to enable console logging for the Protect SDK.
     */
    var isConsoleLogEnabled: Boolean = false
    /**
     * Whether to use lazy metadata loading for the Protect SDK.
     */
    var isLazyMetadata: Boolean = false

    /**
     * Whether to enable behavioral data collection.
     */
    var isBehavioralDataCollection: Boolean = true

    var agentIdentification: Boolean = false
    var agentTimeout: Int = 0
    var agentPort: Int = 0
    var universalDeviceIdentification: Boolean = false
}



