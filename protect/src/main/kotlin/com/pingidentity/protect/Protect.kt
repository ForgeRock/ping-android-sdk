/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.protect

import com.pingidentity.android.ContextProvider
import com.pingidentity.orchestrate.Module
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

object Protect {

    private lateinit var protectConfig: ProtectConfig
    private var isInitialized: Boolean = false
    private var lock = Mutex()

    /**
     * Configures the Protect SDK with the provided configuration.
     * This method should be called before calling [init].
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
    suspend fun init(): Unit = lock.withLock {
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
                        init.resumeWithException(ProtectException("PingOneSignals failed $p0 $p1 $p2"))
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
     * This method should be called after [init] to get the data.
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

@PingDsl
open class ProtectConfig {
    var envId: String? = null
    var deviceAttributesToIgnore: List<String> = emptyList()
    var customHost: String? = null
    var isConsoleLogEnabled: Boolean = false
    var isLazyMetadata: Boolean = false
    var isBehavioralDataCollection: Boolean = true

}

@PingDsl
class ProtectLifecycleConfig : ProtectConfig() {
    var pauseBehavioralDataOnSuccess: Boolean = false
    var resumeBehavioralDataOnStart: Boolean = false
}

val ProtectLifecycle =
    Module.of(::ProtectLifecycleConfig) {
        init {
            Protect.config {
                envId = config.envId
                deviceAttributesToIgnore = config.deviceAttributesToIgnore
                customHost = config.customHost
                isConsoleLogEnabled = config.isConsoleLogEnabled
                isLazyMetadata = config.isLazyMetadata
                isBehavioralDataCollection = config.isBehavioralDataCollection
            }
            Protect.init()
        }

        start {
            if (config.resumeBehavioralDataOnStart) {
                Protect.resumeBehavioralData()
            }
            it
        }

        success {
            if (config.pauseBehavioralDataOnSuccess) {
                Protect.pauseBehavioralData()
            }
            it
        }
    }