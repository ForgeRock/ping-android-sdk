/*
 * Copyright (c) 2024 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.config

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ConfigurationDefaults"
private const val ASSET_CONFIG = "ping_sdk_config.json"
private const val ASSET_SOURCE = "ping_sdk_config_source.txt"
private const val CACHE_FILE = "ping_sdk_config.json"
private const val CONNECT_TIMEOUT_MS = 5_000
private const val READ_TIMEOUT_MS = 10_000

/**
 * Loads and caches the default [Configuration] entries using a three-tier strategy:
 *
 * 1. **Remote** — if `assets/ping_sdk_config_source.txt` contains a non-blank URL,
 *    fetch the JSON from that URL and write it to `filesDir/ping_sdk_config.json`.
 * 2. **Local cache** (`filesDir/ping_sdk_config.json`) — used when offline or when
 *    the remote fetch fails.
 * 3. **Bundled assets** (`assets/ping_sdk_config.json`) — last-resort fallback.
 *
 * Call [load] once at application startup. After that, [entries] and [isDefault] are
 * safe to use from any thread.
 */
internal object ConfigurationDefaults {

    lateinit var entries: List<Configuration>
        private set

    /**
     * Loads configurations using the three-tier strategy (remote → cache → assets).
     *
     * Remote fetch errors are non-fatal: a warning is logged and the next tier is tried.
     * A parse error on the remote or cached JSON is also non-fatal; the bundled asset is used
     * as the ultimate fallback and its parse errors are fatal (developer error in source control).
     *
     * @throws ConfigurationException if the bundled asset itself is missing or invalid.
     */
    fun load(context: Context): List<Configuration> {
        val appContext = context.applicationContext
        val remoteUrl = readSourceUrl(appContext)

        if (remoteUrl != null) {
            val fetched = fetchRemote(remoteUrl)
            if (fetched != null) {
                val parsed = tryParse(fetched, source = "remote $remoteUrl")
                if (parsed != null) {
                    writeCacheFile(appContext, fetched)
                    entries = parsed
                    return parsed
                }
            }
        }

        val cacheFile = cacheFile(appContext)
        if (cacheFile.exists()) {
            val parsed = tryParse(cacheFile.readText(), source = "cache ${cacheFile.path}")
            if (parsed != null) {
                entries = parsed
                return parsed
            }
        }

        // Bundled fallback — errors here are fatal (bad source-controlled file).
        val json = appContext.assets.open(ASSET_CONFIG).use { it.bufferedReader().readText() }
        val loaded = ConfigurationParser.parse(json)
        entries = loaded
        return loaded
    }

    fun isDefault(config: Configuration): Boolean = entries.any { it.name == config.name }

    // -------------------------------------------------------------------------
    // Internal helpers — internal so they can be unit-tested without Android
    // -------------------------------------------------------------------------

    internal fun parseSourceUrl(text: String): String? =
        text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith('#') }
            .takeIf { !it.isNullOrBlank() }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun readSourceUrl(context: Context): String? = try {
        context.assets.open(ASSET_SOURCE).use { it.bufferedReader().readText() }
            .let { parseSourceUrl(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read $ASSET_SOURCE: ${e.message}")
        null
    }

    private fun fetchRemote(urlString: String): String? = try {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.connect()
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            connection.inputStream.use { it.bufferedReader().readText() }
        } else {
            Log.w(TAG, "Remote config fetch returned HTTP ${connection.responseCode}")
            null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Remote config fetch failed: ${e.message}")
        null
    }

    private fun tryParse(json: String, source: String): List<Configuration>? = try {
        ConfigurationParser.parse(json)
    } catch (e: ConfigurationException) {
        Log.w(TAG, "Could not parse configuration from $source: ${e.message}")
        null
    }

    private fun writeCacheFile(context: Context, json: String) {
        try {
            cacheFile(context).writeText(json)
        } catch (e: Exception) {
            Log.w(TAG, "Could not write config cache: ${e.message}")
        }
    }

    private fun cacheFile(context: Context): File =
        File(context.applicationContext.filesDir, CACHE_FILE)
}
