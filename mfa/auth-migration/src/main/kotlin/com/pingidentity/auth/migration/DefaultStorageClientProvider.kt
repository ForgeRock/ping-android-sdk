/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.auth.migration

import android.content.Context
import org.forgerock.android.auth.DefaultStorageClient
import org.forgerock.android.auth.StorageClient

/** SharedPreferences file name for storing account data from the ForgeRock Authenticator. */
private const val AUTH_DATA_ACCOUNT = "org.forgerock.android.authenticator.DATA.ACCOUNT"

/** SharedPreferences file name for storing mechanism data from the ForgeRock Authenticator. */
private const val AUTH_DATA_MECHANISM = "org.forgerock.android.authenticator.DATA.MECHANISM"

/** SharedPreferences file name for storing push notification data from the ForgeRock Authenticator. */
private const val AUTH_DATA_NOTIFICATIONS = "org.forgerock.android.authenticator.DATA.NOTIFICATIONS"

/** SharedPreferences file name for storing the device token from the ForgeRock Authenticator. */
private const val AUTH_DATA_DEVICE_TOKEN = "org.forgerock.android.authenticator.DATA.DEVICE_TOKEN"

/**
 * A [StorageClientProvider] that reads data from the default [DefaultStorageClient] used by the
 * ForgeRock Authenticator SDK.
 *
 * This provider is intended for migration scenarios where authenticator data needs to be
 * transferred from the legacy ForgeRock Authenticator storage into the unified SDK storage.
 *
 * @param context The Android [Context] used to access shared preferences.
 * @param storageClient The [StorageClient] to read legacy authenticator data from.
 *   Defaults to a new [DefaultStorageClient] instance.
 */
class DefaultStorageClientProvider(
    context: Context,
    storageClient: StorageClient = DefaultStorageClient(context),
) : StorageClientProvider(context, storageClient) {

    /**
     * Cleans up all legacy ForgeRock Authenticator SharedPreferences files after migration.
     *
     * This override deletes the SharedPreferences files directly (rather than clearing individual
     * entries) to improve performance during cleanup.
     *
     * @param context The Android [Context] used to delete shared preferences.
     * @param backup A callback invoked before deletion, allowing the caller to back up data prior
     *   to removal.
     */
    override suspend fun cleanUp(context: Context, backup: (context: Context) -> Unit) {

        backup(context)
        context.deleteSharedPreferences(AUTH_DATA_ACCOUNT)
        context.deleteSharedPreferences(AUTH_DATA_MECHANISM)
        context.deleteSharedPreferences(AUTH_DATA_NOTIFICATIONS)
        context.deleteSharedPreferences(AUTH_DATA_DEVICE_TOKEN)
    }
}
