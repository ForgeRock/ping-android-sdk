package com.pingidentity.device.profile.collector

import android.os.Build
import com.pingidentity.android.ContextProvider
import com.pingidentity.device.root.scan
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.TimeZone

class PlatformCollector: DeviceCollector<Platform> {
    override val key: String
        get() = PLATFORM_COLLECTOR_KEY

    override suspend fun collect(): Platform? {
        return Platform(
            platform = "Android",
            version = Build.VERSION.SDK_INT,
            device = Build.DEVICE ?: "",
            model = Build.MODEL ?: "",
            brand = Build.BRAND ?: "",
            //@Contextual val locale: Locale? = null,//getCurrentLocale(),
            timeZone = TimeZone.getDefault().id ?: "",
            jailBreakScore = isRooted(),
        )
    }

    override val serializer: KSerializer<Platform>
        get() = Platform.serializer()
}

@Serializable
data class Platform(
    val platform: String,
    val version: Int,
    val device: String,
    val model: String,
    val brand: String,
    //@Contextual val locale: Locale? = null,//getCurrentLocale(),
    val timeZone: String,
    val jailBreakScore: Double,
    )

private fun getCurrentLocale(): Locale {
    return ContextProvider.context.resources.configuration.getLocales().get(0)
}

private suspend fun isRooted() = scan()

private const val PLATFORM_COLLECTOR_KEY = "platform"