/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.oath.storage

import android.content.Context
import com.pingidentity.mfa.commons.exception.MfaStorageException
import com.pingidentity.mfa.commons.storage.SQLMfaStorage
import com.pingidentity.mfa.oath.OathAlgorithm
import com.pingidentity.mfa.oath.OathCredential
import com.pingidentity.mfa.oath.OathStorage
import com.pingidentity.mfa.oath.OathType
import java.util.Date

/**
 * SQLite-based implementation of [OathStorage].
 * This class extends the base [SQLMfaStorage] class with OATH-specific functionality.
 */
class SQLOathStorage private constructor(
    context: Context,
    databaseName: String,
    encryptionEnabled: Boolean,
    secretKey: String?,
    blockStorePreferred: Boolean
) : SQLMfaStorage(context, databaseName, encryptionEnabled, secretKey, blockStorePreferred), OathStorage {

    /**
     * Secondary constructor for backward compatibility.
     */
    constructor(
        context: Context,
        databaseName: String = DEFAULT_DATABASE_NAME,
        encryptionEnabled: Boolean = true
    ) : this(context, databaseName, encryptionEnabled, null, false)

    /**
     * Builder-style DSL constructor for SQLOathStorage.
     */
    constructor(block: Builder.() -> Unit) : this(
        Builder().apply(block).build()
    )

    /**
     * Internal constructor to support creation from Builder.
     */
    private constructor(builder: Builder) : this(
        builder.context ?: throw IllegalArgumentException("Context is required"),
        builder.databaseName ?: DEFAULT_DATABASE_NAME,
        builder.encryptionEnabled,
        builder.secretKey,
        builder.blockStorePreferred
    )

    companion object {
        private const val DEFAULT_DATABASE_NAME = "pingidentity_mfa.db"
        
        // OATH credential specific columns
        private const val OATH_COLUMN_ID = "id"
        private const val OATH_COLUMN_USER_ID = "user_id"
        private const val OATH_COLUMN_RESOURCE_ID = "resource_id"
        private const val OATH_COLUMN_ISSUER = "issuer"
        private const val OATH_COLUMN_DISPLAY_ISSUER = "display_issuer"
        private const val OATH_COLUMN_ACCOUNT_NAME = "account_name"
        private const val OATH_COLUMN_DISPLAY_ACCOUNT_NAME = "display_account_name"
        private const val OATH_COLUMN_TYPE = "type"
        private const val OATH_COLUMN_SECRET = "secret"
        private const val OATH_COLUMN_ALGORITHM = "algorithm"
        private const val OATH_COLUMN_DIGITS = "digits"
        private const val OATH_COLUMN_PERIOD = "period"
        private const val OATH_COLUMN_COUNTER = "counter"
        private const val OATH_COLUMN_CREATED_AT = "created_at"
        private const val OATH_COLUMN_IMAGE_URL = "image_url"
        private const val OATH_COLUMN_BACKGROUND_COLOR = "background_color"
        private const val OATH_COLUMN_POLICIES = "policies"
        private const val OATH_COLUMN_LOCKING_POLICY = "locking_policy"
        private const val OATH_COLUMN_IS_LOCKED = "is_locked"
        
        // Table name for OATH credentials
        private const val TABLE_PREFIX = "mfa_"
        private const val OATH_TABLE = "${TABLE_PREFIX}oath_data"
    }
    
    /**
     * Builder class for configuring SQLOathStorage.
     */
    class Builder {
        var context: Context? = null
        var databaseName: String? = null
        var encryptionEnabled: Boolean = true
        var secretKey: String? = null
        var blockStorePreferred: Boolean = true
        
        /**
         * Build the SQLOathStorage instance.
         */
        internal fun build(): Builder = this
    }
    
    init {
        // Register the OATH table
        registerCredentialTable(OathStorage.CREDENTIAL_TYPE_OATH, OATH_TABLE) { db ->
            // Create OATH table
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $OATH_TABLE (" +
                        "$OATH_COLUMN_ID TEXT PRIMARY KEY, " +
                        "$OATH_COLUMN_USER_ID TEXT, " +
                        "$OATH_COLUMN_RESOURCE_ID TEXT, " +
                        "$OATH_COLUMN_ISSUER TEXT NOT NULL, " +
                        "$OATH_COLUMN_DISPLAY_ISSUER TEXT NOT NULL, " +
                        "$OATH_COLUMN_ACCOUNT_NAME TEXT NOT NULL, " +
                        "$OATH_COLUMN_DISPLAY_ACCOUNT_NAME TEXT NOT NULL, " +
                        "$OATH_COLUMN_TYPE TEXT NOT NULL, " + // Will store 'TOTP' or 'HOTP'
                        "$OATH_COLUMN_SECRET TEXT NOT NULL, " +
                        "$OATH_COLUMN_ALGORITHM TEXT NOT NULL, " + // Will store 'SHA1', 'SHA256', or 'SHA512'
                        "$OATH_COLUMN_DIGITS INTEGER NOT NULL DEFAULT 6, " +
                        "$OATH_COLUMN_PERIOD INTEGER DEFAULT 30, " +
                        "$OATH_COLUMN_COUNTER INTEGER DEFAULT 0, " +
                        "$OATH_COLUMN_CREATED_AT INTEGER NOT NULL, " + // Will store timestamp
                        "$OATH_COLUMN_IMAGE_URL TEXT, " +
                        "$OATH_COLUMN_BACKGROUND_COLOR TEXT, " +
                        "$OATH_COLUMN_POLICIES TEXT, " +
                        "$OATH_COLUMN_LOCKING_POLICY TEXT, " +
                        "$OATH_COLUMN_IS_LOCKED INTEGER NOT NULL DEFAULT 0)" // SQLite doesn't have a boolean type
            )
            
            // Create OATH indexes
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_oath_issuer ON $OATH_TABLE ($OATH_COLUMN_ISSUER)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_oath_user_id ON $OATH_TABLE ($OATH_COLUMN_USER_ID)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_oath_resource_id ON $OATH_TABLE ($OATH_COLUMN_RESOURCE_ID)")
        }
    }
    
    /**
     * Store an OATH credential.
     *
     * @param credential The OATH credential to be stored.
     * @throws MfaStorageException if the credential cannot be stored.
     */
    @Throws(MfaStorageException::class)
    override fun storeOathCredential(credential: OathCredential) {
        // Convert the boolean to an integer (0 or 1)
        val isLockedAsInt = if (credential.isLocked) 1L else 0L
        
        val data = mapOf(
            OATH_COLUMN_ID to credential.id,
            OATH_COLUMN_USER_ID to credential.userId,
            OATH_COLUMN_RESOURCE_ID to credential.resourceId,
            OATH_COLUMN_ISSUER to credential.issuer,
            OATH_COLUMN_DISPLAY_ISSUER to credential.displayIssuer,
            OATH_COLUMN_ACCOUNT_NAME to credential.accountName,
            OATH_COLUMN_DISPLAY_ACCOUNT_NAME to credential.displayAccountName,
            OATH_COLUMN_TYPE to credential.oathType.name,
            OATH_COLUMN_SECRET to credential.secret,
            OATH_COLUMN_ALGORITHM to credential.oathAlgorithm.name,
            OATH_COLUMN_DIGITS to credential.digits.toLong(),
            OATH_COLUMN_PERIOD to credential.period.toLong(),
            OATH_COLUMN_COUNTER to credential.counter,
            OATH_COLUMN_CREATED_AT to credential.createdAt.time,
            OATH_COLUMN_IMAGE_URL to credential.imageURL,
            OATH_COLUMN_BACKGROUND_COLOR to credential.backgroundColor,
            OATH_COLUMN_POLICIES to credential.policies,
            OATH_COLUMN_LOCKING_POLICY to credential.lockingPolicy,
            OATH_COLUMN_IS_LOCKED to isLockedAsInt
        )

        storeCredential(OathStorage.CREDENTIAL_TYPE_OATH, credential.id, data)
    }
    
    /**
     * Retrieve an OATH credential by its ID.
     *
     * @param credentialId The ID of the credential to retrieve.
     * @return The OATH credential, or null if not found.
     * @throws MfaStorageException if the credential cannot be retrieved.
     */
    @Throws(MfaStorageException::class)
    override fun retrieveOathCredential(credentialId: String): OathCredential? {
        val data = retrieveCredential(OathStorage.CREDENTIAL_TYPE_OATH, credentialId) ?: return null
        return createOathCredentialFromData(data)
    }
    
    /**
     * Get all OATH credentials.
     *
     * @return A list of all OATH credentials.
     * @throws MfaStorageException if the credentials cannot be retrieved.
     */
    @Throws(MfaStorageException::class)
    override fun getAllOathCredentials(): List<OathCredential> {
        val dataList = retrieveAllCredentials(OathStorage.CREDENTIAL_TYPE_OATH)
        return dataList.mapNotNull { createOathCredentialFromData(it) }
    }
    
    /**
     * Remove an OATH credential by its ID.
     *
     * @param credentialId The ID of the credential to remove.
     * @return true if the credential was successfully removed, false if it didn't exist.
     * @throws MfaStorageException if the credential cannot be removed.
     */
    @Throws(MfaStorageException::class)
    override fun removeOathCredential(credentialId: String): Boolean {
        return deleteCredential(OathStorage.CREDENTIAL_TYPE_OATH, credentialId)
    }
    
    /**
     * Clear all OATH credentials from the storage.
     *
     * @throws MfaStorageException if the credentials cannot be cleared.
     */
    @Throws(MfaStorageException::class)
    override fun clearOathCredentials() {
        clearCredentials(OathStorage.CREDENTIAL_TYPE_OATH)
    }
    
    /**
     * Create an OATH credential from a data map.
     *
     * @param data The data map to create the credential from.
     * @return The created OATH credential.
     */
    private fun createOathCredentialFromData(data: Map<String, Any?>): OathCredential? {
        try {
            val id = data[OATH_COLUMN_ID] as String
            val userId = data[OATH_COLUMN_USER_ID] as String
            val resourceId = data[OATH_COLUMN_RESOURCE_ID] as String
            val issuer = data[OATH_COLUMN_ISSUER] as String
            val displayIssuer = data[OATH_COLUMN_DISPLAY_ISSUER] as String
            val accountName = data[OATH_COLUMN_ACCOUNT_NAME] as String
            val displayAccountName = data[OATH_COLUMN_DISPLAY_ACCOUNT_NAME] as String
            val typeStr = data[OATH_COLUMN_TYPE] as String
            val secret = data[OATH_COLUMN_SECRET] as String
            val algorithmStr = data[OATH_COLUMN_ALGORITHM] as String
            val digits = (data[OATH_COLUMN_DIGITS] as Long).toInt()
            val period = (data[OATH_COLUMN_PERIOD] as Long).toInt()
            val counter = data[OATH_COLUMN_COUNTER] as Long
            val createdAt = Date(data[OATH_COLUMN_CREATED_AT] as Long)
            val imageURL = data[OATH_COLUMN_IMAGE_URL] as String?
            val backgroundColor = data[OATH_COLUMN_BACKGROUND_COLOR] as String?
            val policies = data[OATH_COLUMN_POLICIES] as String?
            val lockingPolicy = data[OATH_COLUMN_LOCKING_POLICY] as String?
            val isLocked = (data[OATH_COLUMN_IS_LOCKED] as Long) == 1L
            
            // Parse enum values
            val type = OathType.valueOf(typeStr)
            val algorithm = OathAlgorithm.valueOf(algorithmStr)
            
            // Create and return the credential
            return OathCredential(
                id = id,
                userId = userId,
                resourceId = resourceId,
                issuer = issuer,
                displayIssuer = displayIssuer,
                accountName = accountName,
                displayAccountName = displayAccountName,
                oathType = type,
                secret = secret,
                oathAlgorithm = algorithm,
                digits = digits,
                period = period,
                counter = counter,
                createdAt = createdAt,
                imageURL = imageURL,
                backgroundColor = backgroundColor,
                policies = policies,
                lockingPolicy = lockingPolicy,
                isLocked = isLocked
            )
        } catch (e: Exception) {
            throw MfaStorageException("Error creating OATH credential from data: ${e.message}")
        }
    }
}