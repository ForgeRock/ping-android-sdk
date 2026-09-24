/*
 * Copyright (c) 2026 - 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor
import com.pingidentity.journey.Journey
import com.pingidentity.samples.pingsampleapp.config.journey
import kotlinx.coroutines.delay

private const val TAG = "RestoreCredentialBackupAgent"
private const val JOURNEY_POLL_INTERVAL_MS = 200L
private const val JOURNEY_READY_TIMEOUT_MS = 5_000L

/**
 * Tier 1 (background) Restore Credentials sign-in.
 *
 * Declared as `android:backupAgent` in AndroidManifest.xml (this app already has
 * `android:allowBackup="true"`). [onRestoreFinished] fires once Android's Backup & Restore
 * transfer completes on a new device, before the user opens the app for the first time -
 * this attempts the same silent "RestoreCredential" journey sign-in used for Tier 2 (see
 * `journey/RestoreCredentialSignIn.kt`), synchronously, so the sign-in completes before the
 * system considers restoration finished.
 *
 * The app had no `BackupAgent` before this, so the manifest also declares
 * `android:fullBackupOnly="true"` - the key-value callbacks below are no-ops.
 */
class RestoreCredentialBackupAgent : BackupAgent() {

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) {
        // No-op: this agent only supports full backup (android:fullBackupOnly="true").
    }

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ) {
        // No-op: this agent only supports full backup (android:fullBackupOnly="true").
    }

    override fun onRestoreFinished() {
        super.onRestoreFinished()
        // Runs synchronously so the restore credential sign-in completes before the system
        // considers the app's restoration finished.
        /*
        runBlocking {
            val journeyInstance = awaitJourney()
            if (journeyInstance == null) {
                Log.d(TAG, "Journey not configured, skipping restore credential sign-in")
                return@runBlocking
            }
            journeyInstance.attemptRestoreCredentialSignIn()
        }
         */
    }

    /**
     * [PingSampleApplication.onCreate] loads `journey` asynchronously, and this callback can
     * fire before that finishes - poll briefly for it to become available rather than treating
     * a still-loading config as "no journey configured".
     */
    private suspend fun awaitJourney(): Journey? {
        var waited = 0L
        while (journey == null && waited < JOURNEY_READY_TIMEOUT_MS) {
            delay(JOURNEY_POLL_INTERVAL_MS)
            waited += JOURNEY_POLL_INTERVAL_MS
        }
        return journey
    }
}
