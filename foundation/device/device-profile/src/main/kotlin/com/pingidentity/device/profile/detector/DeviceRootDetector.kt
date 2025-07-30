package com.pingidentity.device.profile.detector

import android.content.Context
import com.pingidentity.device.root.BaseRootDetector
import kotlinx.serialization.Serializable

interface RootDetector<T : @Serializable BaseRootDetector> {
    val key: String
    fun isRooted(context: Context): Double
}

inline fun <reified T : @Serializable Any> RootDetector(
    key: String,
    noinline isRooted: () -> Double
): RootDetector<BaseRootDetector> {
    return object : RootDetector<BaseRootDetector> {
        override val key = key
        override fun isRooted(context: Context): Double = isRooted()
    }
}