/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.push.storage

import android.content.ContentValues
import android.content.Context
import com.pingidentity.android.ContextProvider
import com.pingidentity.logger.Logger
import com.pingidentity.mfa.commons.exception.MfaStorageException
import com.pingidentity.mfa.push.PushCredential
import com.pingidentity.mfa.push.PushDeviceToken
import com.pingidentity.mfa.push.PushNotification
import com.pingidentity.mfa.push.PushPlatform
import com.pingidentity.mfa.push.storage.PushStorage
import com.pingidentity.mfa.push.PushType
import com.pingidentity.storage.sqlite.passphrase.KeyStorePassphraseProvider
import com.pingidentity.storage.sqlite.passphrase.PassphraseProvider
import com.pingidentity.storage.sqlite.SQLiteStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.sqlcipher.Cursor
import java.util.Date
import kotlin.coroutines.coroutineContext

/**
 * SQLite-based implementation of [PushStorage].
 * This class directly extends [SQLiteStorage] with Push-specific functionality.
 */
class SQLPushStorage private constructor(
    context: Context,
    databaseName: String,
    databaseVersion: Int = 1,
    passphraseProvider: PassphraseProvider,
    override val logger: Logger = Logger.logger
) : SQLiteStorage(
    context = context,
    databaseName = databaseName,
    databaseVersion = databaseVersion,
    passphraseProvider = passphraseProvider,
    logger = logger
), PushStorage {

    /**
     * Builder-style DSL constructor for SQLPushStorage.
     */
    constructor(block: Builder.() -> Unit) : this(
        Builder().apply(block)
    )

    /**
     * Internal constructor to support creation from Builder.
     */
    private constructor(builder: Builder) : this(
        builder.context,
        builder.databaseName,
        builder.databaseVersion,
        builder.passphraseProvider,
        builder.logger
    )

    companion object {
        private const val DEFAULT_DATABASE_NAME = "pingidentity_mfa.db"
        
        // Push credential specific columns
        private const val PUSH_COLUMN_ID = "id"
        private const val PUSH_COLUMN_USER_ID = "user_id"
        private const val PUSH_COLUMN_RESOURCE_ID = "resource_id"
        private const val PUSH_COLUMN_ISSUER = "issuer"
        private const val PUSH_COLUMN_DISPLAY_ISSUER = "display_issuer"
        private const val PUSH_COLUMN_ACCOUNT_NAME = "account_name"
        private const val PUSH_COLUMN_DISPLAY_ACCOUNT_NAME = "display_account_name"
        private const val PUSH_COLUMN_SERVER_ENDPOINT = "server_endpoint"
        private const val PUSH_COLUMN_SHARED_SECRET = "shared_secret"
        private const val PUSH_COLUMN_PLATFORM = "platform"
        private const val PUSH_COLUMN_CREATED_AT = "created_at"
        private const val PUSH_COLUMN_IMAGE_URL = "image_url"
        private const val PUSH_COLUMN_BACKGROUND_COLOR = "background_color"
        private const val PUSH_COLUMN_POLICIES = "policies"
        private const val PUSH_COLUMN_LOCKING_POLICY = "locking_policy"
        private const val PUSH_COLUMN_IS_LOCKED = "is_locked"
        
        // Push notification specific columns
        private const val NOTIFICATION_COLUMN_ID = "id"
        private const val NOTIFICATION_COLUMN_CREDENTIAL_ID = "credential_id"
        private const val NOTIFICATION_COLUMN_MESSAGE_ID = "message_id"
        private const val NOTIFICATION_COLUMN_MESSAGE_TEXT = "message_text"
        private const val NOTIFICATION_COLUMN_CUSTOM_PAYLOAD = "custom_payload"
        private const val NOTIFICATION_COLUMN_CHALLENGE = "challenge"
        private const val NOTIFICATION_COLUMN_NUMBERS_CHALLENGE = "numbers_challenge"
        private const val NOTIFICATION_COLUMN_AMLB_COOKIE = "amlb_cookie"
        private const val NOTIFICATION_COLUMN_CONTEXT_INFO = "context_info"
        private const val NOTIFICATION_COLUMN_PUSH_TYPE = "push_type"
        private const val NOTIFICATION_COLUMN_TTL = "ttl"
        private const val NOTIFICATION_COLUMN_PENDING = "pending"
        private const val NOTIFICATION_COLUMN_APPROVED = "approved"
        private const val NOTIFICATION_COLUMN_CREATED_AT = "created_at"
        private const val NOTIFICATION_COLUMN_SENT_AT = "sent_at"
        private const val NOTIFICATION_COLUMN_RESPONDED_AT = "responded_at"
        private const val NOTIFICATION_COLUMN_ADDITIONAL_DATA = "additional_data"

        // Constants for the device token table
        private const val TOKEN_COLUMN_ID = "id"
        private const val TOKEN_COLUMN_TOKEN_ID = "token_id"
        private const val TOKEN_COLUMN_CREATED_AT = "created_at"
        private const val TOKEN_COLUMN_IS_CURRENT = "is_current"

        // Table names for Push data
        private const val TABLE_PREFIX = "mfa_"
        private const val PUSH_TABLE = "${TABLE_PREFIX}push_credentials"
        private const val NOTIFICATION_TABLE = "${TABLE_PREFIX}push_notifications"
        private const val DEVICE_TOKEN_TABLE = "${TABLE_PREFIX}push_device_tokens"
    }
    
    /**
     * Builder class for configuring SQLPushStorage.
     */
    class Builder {
        var context: Context = ContextProvider.context
        var databaseName: String = DEFAULT_DATABASE_NAME
        var databaseVersion: Int = 1
        var initialPassphrase: String? = null // Default is null, in case developer does not want to supply their own passphrase
        var passphraseProvider: PassphraseProvider = KeyStorePassphraseProvider(context, initialPassphrase)
        var logger: Logger = Logger.logger
    }

    init {
        // Register the Push table creator
        registerTableCreator { db ->
            // Create Push device tokens table
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS $DEVICE_TOKEN_TABLE (
                    $TOKEN_COLUMN_ID TEXT PRIMARY KEY,
                    $TOKEN_COLUMN_TOKEN_ID TEXT NOT NULL,
                    $TOKEN_COLUMN_CREATED_AT INTEGER NOT NULL,
                    $TOKEN_COLUMN_IS_CURRENT INTEGER DEFAULT 1
                )"""
            )
            
            // Create Push credentials table
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS $PUSH_TABLE (
                    $PUSH_COLUMN_ID TEXT PRIMARY KEY,
                    $PUSH_COLUMN_USER_ID TEXT,
                    $PUSH_COLUMN_RESOURCE_ID TEXT,
                    $PUSH_COLUMN_ISSUER TEXT NOT NULL,
                    $PUSH_COLUMN_DISPLAY_ISSUER TEXT NOT NULL,
                    $PUSH_COLUMN_ACCOUNT_NAME TEXT NOT NULL,
                    $PUSH_COLUMN_DISPLAY_ACCOUNT_NAME TEXT NOT NULL,
                    $PUSH_COLUMN_SERVER_ENDPOINT TEXT NOT NULL,
                    $PUSH_COLUMN_SHARED_SECRET TEXT NOT NULL,
                    $PUSH_COLUMN_PLATFORM TEXT NOT NULL,
                    $PUSH_COLUMN_CREATED_AT INTEGER NOT NULL,
                    $PUSH_COLUMN_IMAGE_URL TEXT,
                    $PUSH_COLUMN_BACKGROUND_COLOR TEXT,
                    $PUSH_COLUMN_POLICIES TEXT,
                    $PUSH_COLUMN_LOCKING_POLICY TEXT,
                    $PUSH_COLUMN_IS_LOCKED INTEGER DEFAULT 0
                )"""
            )
            
            // Create Push notifications table
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS $NOTIFICATION_TABLE (
                    $NOTIFICATION_COLUMN_ID TEXT PRIMARY KEY,
                    $NOTIFICATION_COLUMN_CREDENTIAL_ID TEXT NOT NULL,
                    $NOTIFICATION_COLUMN_MESSAGE_ID TEXT,
                    $NOTIFICATION_COLUMN_MESSAGE_TEXT TEXT,
                    $NOTIFICATION_COLUMN_CUSTOM_PAYLOAD TEXT,
                    $NOTIFICATION_COLUMN_CHALLENGE TEXT,
                    $NOTIFICATION_COLUMN_NUMBERS_CHALLENGE TEXT,
                    $NOTIFICATION_COLUMN_AMLB_COOKIE TEXT,
                    $NOTIFICATION_COLUMN_CONTEXT_INFO TEXT,
                    $NOTIFICATION_COLUMN_PUSH_TYPE TEXT,
                    $NOTIFICATION_COLUMN_TTL INTEGER,
                    $NOTIFICATION_COLUMN_PENDING INTEGER DEFAULT 0,
                    $NOTIFICATION_COLUMN_APPROVED INTEGER DEFAULT 0,
                    $NOTIFICATION_COLUMN_CREATED_AT INTEGER NOT NULL,
                    $NOTIFICATION_COLUMN_SENT_AT INTEGER,
                    $NOTIFICATION_COLUMN_RESPONDED_AT INTEGER,
                    $NOTIFICATION_COLUMN_ADDITIONAL_DATA TEXT,
                    FOREIGN KEY ($NOTIFICATION_COLUMN_CREDENTIAL_ID) REFERENCES $PUSH_TABLE($PUSH_COLUMN_ID) ON DELETE CASCADE
                )"""
            )
            
            // Create Push indexes
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_push_issuer ON $PUSH_TABLE ($PUSH_COLUMN_ISSUER)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_push_user_id ON $PUSH_TABLE ($PUSH_COLUMN_USER_ID)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_push_resource_id ON $PUSH_TABLE ($PUSH_COLUMN_RESOURCE_ID)")
            
            // Create Notification indexes
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_notification_credential_id ON $NOTIFICATION_TABLE ($NOTIFICATION_COLUMN_CREDENTIAL_ID)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_notification_message_id ON $NOTIFICATION_TABLE ($NOTIFICATION_COLUMN_MESSAGE_ID)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_notification_status ON $NOTIFICATION_TABLE ($NOTIFICATION_COLUMN_PENDING, $NOTIFICATION_COLUMN_APPROVED)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_notification_created_at ON $NOTIFICATION_TABLE ($NOTIFICATION_COLUMN_CREATED_AT)")
        }

        logger.d("Push SQL storage created")
    }

    /**
     * Initialize the Push storage.
     * This method initializes the database and tables.
     *
     * @throws MfaStorageException if initialization fails.
     */
    override suspend fun initialize() {
        try {
            // Initialize the database
            initializeDatabase()
            logger.d("Push storage initialized")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to initialize Push storage", e)
        }
    }
    
    /**
     * Clear all data from the storage.
     * This method clears all Push credential and notification tables.
     *
     * @throws MfaStorageException if the storage cannot be cleared.
     */
    override suspend fun clear() {
        try {
            clearPushCredentials()
            clearPushNotifications()
            clearPushDeviceTokens()
            logger.d("Cleared all data from Push storage")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to clear Push storage: ${e.message}", e)
            throw MfaStorageException("Failed to clear Push storage", e)
        }
    }
    
    /**
     * Close the Push storage.
     * This method closes the database connection.
     */
    override suspend fun close() {
        try {
            closeDatabase() 
            logger.d("Push SQL storage closed")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Error closing Push storage: ${e.message}", e)
        }
    }

    /**
     * Store a push credential.
     *
     * @param credential The Push credential to be stored.
     * @throws MfaStorageException if the credential cannot be stored.
     */
    override suspend fun storePushCredential(credential: PushCredential) {
        try {
            // Convert the boolean to an integer (0 or 1)
            val isLockedAsInt = if (credential.isLocked) 1L else 0L
            
            val data = mapOf(
                PUSH_COLUMN_ID to credential.id,
                PUSH_COLUMN_USER_ID to credential.userId,
                PUSH_COLUMN_RESOURCE_ID to credential.resourceId,
                PUSH_COLUMN_ISSUER to credential.issuer,
                PUSH_COLUMN_DISPLAY_ISSUER to credential.displayIssuer,
                PUSH_COLUMN_ACCOUNT_NAME to credential.accountName,
                PUSH_COLUMN_DISPLAY_ACCOUNT_NAME to credential.displayAccountName,
                PUSH_COLUMN_SERVER_ENDPOINT to credential.serverEndpoint,
                PUSH_COLUMN_SHARED_SECRET to credential.sharedSecret,
                PUSH_COLUMN_PLATFORM to credential.platform,
                PUSH_COLUMN_CREATED_AT to credential.createdAt.time,
                PUSH_COLUMN_IMAGE_URL to credential.imageURL,
                PUSH_COLUMN_BACKGROUND_COLOR to credential.backgroundColor,
                PUSH_COLUMN_POLICIES to credential.policies,
                PUSH_COLUMN_LOCKING_POLICY to credential.lockingPolicy,
                PUSH_COLUMN_IS_LOCKED to isLockedAsInt
            )

            storePushCredentialData(credential.id, data)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to store Push credential with ID ${credential.id}", e)
        }
    }
    
    /**
     * Store Push credential data in the database.
     *
     * @param credentialId The ID of the credential.
     * @param data The credential data.
     * @throws MfaStorageException if the credential cannot be stored.
     */
    private suspend fun storePushCredentialData(credentialId: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            // Begin a transaction to ensure data consistency
            beginTransaction()
            
            try {
                // Insert or update the credential
                val existingCredential = retrievePushCredentialData(credentialId)
                if (existingCredential != null) {
                    // Update existing credential
                    val whereClause = "$PUSH_COLUMN_ID = ?"
                    val whereArgs = arrayOf(credentialId)
                    database.update(PUSH_TABLE, mapToContentValues(data), whereClause, whereArgs)
                    logger.d("Updated Push credential with ID: $credentialId")
                } else {
                    // Insert new credential
                    database.insert(PUSH_TABLE, null, mapToContentValues(data))
                    logger.d("Inserted new Push credential with ID: $credentialId")
                }
                
                // Mark the transaction as successful
                setTransactionSuccessful()
            } finally {
                // End the transaction
                endTransaction()
            }
            
            logger.d("Stored Push credential with ID: $credentialId")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to store Push credential with ID $credentialId: ${e.message}", e)
            throw MfaStorageException("Failed to store Push credential with ID $credentialId", e)
        }
    }
    
    /**
     * Retrieve all stored push credentials.
     *
     * @return A list of all Push credentials.
     * @throws MfaStorageException if the credentials cannot be retrieved.
     */
    override suspend fun getAllPushCredentials(): List<PushCredential> {
        try {
            val dataList = retrieveAllPushCredentialsData()
            return dataList.mapNotNull { createPushCredentialFromData(it) }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to retrieve all Push credentials", e)
        }
    }
    
    /**
     * Retrieve all Push credential data from the database.
     *
     * @return A list of credential data.
     * @throws MfaStorageException if the credentials cannot be retrieved.
     */
    private suspend fun retrieveAllPushCredentialsData(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            // Use the query method from the parent class with the correct format
            val sql = "SELECT * FROM $PUSH_TABLE ORDER BY $PUSH_COLUMN_CREATED_AT DESC"
            return@withContext query(sql, null) { cursor ->
                extractDataFromCursor(cursor)
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to retrieve all Push credentials: ${e.message}", e)
            throw MfaStorageException("Failed to retrieve all Push credentials", e)
        }
    }
    
    /**
     * Retrieve a specific push credential by ID.
     *
     * @param credentialId The ID of the credential to retrieve.
     * @return The Push credential, or null if not found.
     * @throws MfaStorageException if the credential cannot be retrieved.
     */
    override suspend fun retrievePushCredential(credentialId: String): PushCredential? {
        try {
            val data = retrievePushCredentialData(credentialId) ?: return null
            return createPushCredentialFromData(data)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to retrieve Push credential with ID $credentialId", e)
        }
    }

    /**
     * Retrieve Push credential data from the database.
     *
     * @param credentialId The ID of the credential to retrieve.
     * @return The credential data, or null if not found.
     * @throws MfaStorageException if the credential cannot be retrieved.
     */
    private suspend fun retrievePushCredentialData(credentialId: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        checkDatabase()

        try {
            // Use the query method from the parent class with the correct format
            val sql = "SELECT * FROM $PUSH_TABLE WHERE $PUSH_COLUMN_ID = ?"
            val args = arrayOf(credentialId)
            val results = query(sql, args) { cursor ->
                extractDataFromCursor(cursor)
            }
            
            return@withContext results.firstOrNull()
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to retrieve Push credential with ID $credentialId: ${e.message}", e)
            throw MfaStorageException("Failed to retrieve Push credential with ID $credentialId", e)
        }
    }
    
    /**
     * Remove a push credential by its ID.
     *
     * @param credentialId The ID of the credential to remove.
     * @return true if the credential was successfully removed, false if it didn't exist.
     * @throws MfaStorageException if the credential cannot be removed.
     */
    override suspend fun removePushCredential(credentialId: String): Boolean {
        try {
            return deletePushCredential(credentialId)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to remove Push credential with ID $credentialId", e)
        }
    }
    
    /**
     * Delete a Push credential from the database.
     *
     * @param credentialId The ID of the credential to delete.
     * @return true if the credential was successfully deleted, false if it didn't exist.
     * @throws MfaStorageException if the credential cannot be deleted.
     */
    private suspend fun deletePushCredential(credentialId: String): Boolean = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            // Delete associated notifications first
            removePushNotificationsForCredential(credentialId)
            
            // Now delete the credential
            val whereClause = "$PUSH_COLUMN_ID = ?"
            val whereArgs = arrayOf(credentialId)
            
            val deletedRows = database.delete(
                PUSH_TABLE,
                whereClause,
                whereArgs
            )
            
            val wasDeleted = deletedRows > 0
            if (wasDeleted) {
                logger.d("Deleted Push credential with ID: $credentialId")
            } else {
                logger.d("No Push credential found with ID: $credentialId")
            }
            
            return@withContext wasDeleted
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to delete Push credential with ID $credentialId: ${e.message}", e)
            throw MfaStorageException("Failed to delete Push credential with ID $credentialId", e)
        }
    }
    
    /**
     * Clear all Push credentials from the storage.
     *
     * @throws MfaStorageException if the credentials cannot be cleared.
     */
    override suspend fun clearPushCredentials() {
        try {
            clearAllPushCredentials()
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to clear Push credentials", e)
        }
    }
    
    /**
     * Clear all Push credentials from the database.
     *
     * @throws MfaStorageException if the credentials cannot be cleared.
     */
    private suspend fun clearAllPushCredentials() = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            // Delete notifications first (due to foreign key constraint)
            clearPushNotifications()
            
            // Then delete credentials
            database.delete(PUSH_TABLE, null, null)
            logger.d("Cleared all Push credentials")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to clear Push credentials: ${e.message}", e)
            throw MfaStorageException("Failed to clear Push credentials", e)
        }
    }
    
    /**
     * Store a push notification.
     *
     * @param notification The Push notification to be stored.
     * @throws MfaStorageException if the notification cannot be stored.
     */
    override suspend fun storePushNotification(notification: PushNotification) {
        try {
            val data = mapOf(
                NOTIFICATION_COLUMN_ID to notification.id,
                NOTIFICATION_COLUMN_CREDENTIAL_ID to notification.credentialId,
                NOTIFICATION_COLUMN_MESSAGE_ID to notification.messageId,
                NOTIFICATION_COLUMN_MESSAGE_TEXT to notification.messageText,
                NOTIFICATION_COLUMN_CUSTOM_PAYLOAD to notification.customPayload,
                NOTIFICATION_COLUMN_CHALLENGE to notification.challenge,
                NOTIFICATION_COLUMN_NUMBERS_CHALLENGE to notification.numbersChallenge,
                NOTIFICATION_COLUMN_AMLB_COOKIE to notification.loadBalancer,
                NOTIFICATION_COLUMN_CONTEXT_INFO to notification.contextInfo,
                NOTIFICATION_COLUMN_PUSH_TYPE to notification.pushType.name,
                NOTIFICATION_COLUMN_TTL to notification.ttl,
                NOTIFICATION_COLUMN_PENDING to notification.pending,
                NOTIFICATION_COLUMN_APPROVED to notification.approved,
                NOTIFICATION_COLUMN_CREATED_AT to notification.createdAt.time,
                NOTIFICATION_COLUMN_SENT_AT to notification.sentAt?.time,
                NOTIFICATION_COLUMN_RESPONDED_AT to notification.respondedAt?.time,
                NOTIFICATION_COLUMN_ADDITIONAL_DATA to notification.additionalData?.let { Json.encodeToString(PushNotification.AdditionalDataSerializer, it) }
            )

            storePushNotificationData(notification.id, data)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to store Push notification with ID ${notification.id}", e)
        }
    }
    
    /**
     * Store Push notification data in the database.
     *
     * @param notificationId The ID of the notification.
     * @param data The notification data.
     * @throws MfaStorageException if the notification cannot be stored.
     */
    private suspend fun storePushNotificationData(notificationId: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            // Begin a transaction to ensure data consistency
            beginTransaction()
            
            try {
                // Insert or update the notification
                val existingNotification = retrievePushNotificationData(notificationId)
                if (existingNotification != null) {
                    // Update existing notification
                    val whereClause = "$NOTIFICATION_COLUMN_ID = ?"
                    val whereArgs = arrayOf(notificationId)
                    database.update(NOTIFICATION_TABLE, mapToContentValues(data), whereClause, whereArgs)
                    logger.d("Updated Push notification with ID: $notificationId")
                } else {
                    // Insert new notification
                    database.insert(NOTIFICATION_TABLE, null, mapToContentValues(data))
                    logger.d("Inserted new Push notification with ID: $notificationId")
                }
                
                // Mark the transaction as successful
                setTransactionSuccessful()
            } finally {
                // End the transaction
                endTransaction()
            }

            logger.d("Stored Push notification with ID: $notificationId")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to store Push notification with ID $notificationId: ${e.message}", e)
            throw MfaStorageException("Failed to store Push notification with ID $notificationId", e)
        }
    }
    
    /**
     * Retrieve all stored push notifications.
     *
     * @return A list of all Push notifications.
     * @throws MfaStorageException if the notifications cannot be retrieved.
     */
    override suspend fun getAllPushNotifications(): List<PushNotification> {
        try {
            val dataList = retrieveAllPushNotificationsData()
            return dataList.mapNotNull { createPushNotificationFromData(it) }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to retrieve all Push notifications", e)
        }
    }
    
    /**
     * Retrieve all Push notification data from the database.
     *
     * @return A list of notification data.
     * @throws MfaStorageException if the notifications cannot be retrieved.
     */
    private suspend fun retrieveAllPushNotificationsData(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            // Use the query method from the parent class with the correct format
            val sql = "SELECT * FROM $NOTIFICATION_TABLE ORDER BY $NOTIFICATION_COLUMN_CREATED_AT DESC"
            return@withContext query(sql, null) { cursor ->
                extractDataFromCursor(cursor)
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to retrieve all Push notifications: ${e.message}", e)
            throw MfaStorageException("Failed to retrieve all Push notifications", e)
        }
    }
    
    /**
     * Retrieve all pending push notifications.
     *
     * @return A list of pending Push notifications that have not expired.
     * @throws MfaStorageException if the notifications cannot be retrieved.
     */
    override suspend fun getPendingPushNotifications(): List<PushNotification> {
        try {
            val dataList = retrievePendingPushNotificationsData()
            return dataList
                .mapNotNull { createPushNotificationFromData(it) }
                .filterNot { it.expired }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to retrieve pending Push notifications", e)
        }
    }
    
    /**
     * Retrieve pending Push notification data from the database.
     *
     * @return A list of notification data.
     * @throws MfaStorageException if the notifications cannot be retrieved.
     */
    private suspend fun retrievePendingPushNotificationsData(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            // Use the query method from the parent class with the correct format
            val sql = "SELECT * FROM $NOTIFICATION_TABLE WHERE $NOTIFICATION_COLUMN_PENDING = ? ORDER BY $NOTIFICATION_COLUMN_CREATED_AT DESC"
            val args = arrayOf("1") // 1 for true in SQLite boolean representation
            return@withContext query(sql, args) { cursor ->
                extractDataFromCursor(cursor)
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to retrieve pending Push notifications: ${e.message}", e)
            throw MfaStorageException("Failed to retrieve pending Push notifications", e)
        }
    }
    
    /**
     * Retrieve a specific push notification by ID.
     *
     * @param notificationId The ID of the notification to retrieve.
     * @return The Push notification, or null if not found.
     * @throws MfaStorageException if the notification cannot be retrieved.
     */
    override suspend fun retrievePushNotification(notificationId: String): PushNotification? {
        try {
            val data = retrievePushNotificationData(notificationId) ?: return null
            return createPushNotificationFromData(data)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to retrieve Push notification with ID $notificationId", e)
        }
    }
    
    /**
     * Retrieve Push notification data from the database.
     *
     * @param notificationId The ID of the notification to retrieve.
     * @return The notification data, or null if not found.
     * @throws MfaStorageException if the notification cannot be retrieved.
     */
    private suspend fun retrievePushNotificationData(notificationId: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            // Use the query method from the parent class with the correct format
            val sql = "SELECT * FROM $NOTIFICATION_TABLE WHERE $NOTIFICATION_COLUMN_ID = ?"
            val args = arrayOf(notificationId)
            val results = query(sql, args) { cursor ->
                extractDataFromCursor(cursor)
            }
            
            return@withContext results.firstOrNull()
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to retrieve Push notification with ID $notificationId: ${e.message}", e)
            throw MfaStorageException("Failed to retrieve Push notification with ID $notificationId", e)
        }
    }
    
    /**
     * Retrieve a push notification by message ID.
     *
     * @param messageId The message ID of the notification to retrieve.
     * @return The Push notification, or null if not found.
     * @throws MfaStorageException if the notification cannot be retrieved.
     */
    override suspend fun getNotificationByMessageId(messageId: String): PushNotification? {
        try {
            val data = retrievePushNotificationByMessageId(messageId) ?: return null
            return createPushNotificationFromData(data)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to retrieve Push notification with message ID $messageId", e)
        }
    }

    /**
     * Retrieve Push notification data from the database by message ID.
     *
     * @param messageId The message ID of the notification to retrieve.
     * @return The notification data, or null if not found.
     * @throws MfaStorageException if the notification cannot be retrieved.
     */
    private suspend fun retrievePushNotificationByMessageId(messageId: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        checkDatabase()

        try {
            // Return null if messageId is empty or blank
            if (messageId.isBlank()) {
                return@withContext null
            }

            // Use the query method from the parent class with the correct format
            val sql = "SELECT * FROM $NOTIFICATION_TABLE WHERE $NOTIFICATION_COLUMN_MESSAGE_ID = ? LIMIT 1"
            val args = arrayOf(messageId)
            val results = query(sql, args) { cursor ->
                extractDataFromCursor(cursor)
            }

            return@withContext results.firstOrNull()
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to retrieve Push notification with message ID $messageId: ${e.message}", e)
            throw MfaStorageException("Failed to retrieve Push notification with message ID $messageId", e)
        }
    }

    /**
     * Update a push notification.
     *
     * @param notification The Push notification to update.
     * @throws MfaStorageException if the notification cannot be updated.
     */
    override suspend fun updatePushNotification(notification: PushNotification) {
        try {
            storePushNotification(notification)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to update Push notification with ID ${notification.id}", e)
        }
    }
    
    /**
     * Remove a push notification by its ID.
     *
     * @param notificationId The ID of the notification to remove.
     * @return true if the notification was successfully removed, false if it didn't exist.
     * @throws MfaStorageException if the notification cannot be removed.
     */
    override suspend fun removePushNotification(notificationId: String): Boolean {
        try {
            return deletePushNotification(notificationId)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to remove Push notification with ID $notificationId", e)
        }
    }
    
    /**
     * Delete a Push notification from the database.
     *
     * @param notificationId The ID of the notification to delete.
     * @return true if the notification was successfully deleted, false if it didn't exist.
     * @throws MfaStorageException if the notification cannot be deleted.
     */
    private suspend fun deletePushNotification(notificationId: String): Boolean = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            val whereClause = "$NOTIFICATION_COLUMN_ID = ?"
            val whereArgs = arrayOf(notificationId)
            
            val deletedRows = database.delete(
                NOTIFICATION_TABLE,
                whereClause,
                whereArgs
            )
            
            val wasDeleted = deletedRows > 0
            if (wasDeleted) {
                logger.d("Deleted Push notification with ID: $notificationId")
            } else {
                logger.d("No Push notification found with ID: $notificationId")
            }
            
            return@withContext wasDeleted
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to delete Push notification with ID $notificationId: ${e.message}", e)
            throw MfaStorageException("Failed to delete Push notification with ID $notificationId", e)
        }
    }
    
    /**
     * Remove all push notifications associated with a credential.
     *
     * @param credentialId The ID of the credential.
     * @return The number of notifications removed.
     * @throws MfaStorageException if the notifications cannot be removed.
     */
    override suspend fun removePushNotificationsForCredential(credentialId: String): Int {
        try {
            return deletePushNotificationsForCredential(credentialId)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to remove Push notifications for credential ID $credentialId", e)
        }
    }
    
    /**
     * Delete all Push notifications associated with a credential from the database.
     *
     * @param credentialId The ID of the credential.
     * @return The number of notifications deleted.
     * @throws MfaStorageException if the notifications cannot be deleted.
     */
    private suspend fun deletePushNotificationsForCredential(credentialId: String): Int = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            val whereClause = "$NOTIFICATION_COLUMN_CREDENTIAL_ID = ?"
            val whereArgs = arrayOf(credentialId)
            
            val deletedRows = database.delete(
                NOTIFICATION_TABLE,
                whereClause,
                whereArgs
            )
            
            logger.d("Deleted $deletedRows Push notifications for credential ID: $credentialId")
            
            return@withContext deletedRows
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to delete Push notifications for credential ID $credentialId: ${e.message}", e)
            throw MfaStorageException("Failed to delete Push notifications for credential ID $credentialId", e)
        }
    }
    
    /**
     * Clear all Push notifications from the storage.
     *
     * @throws MfaStorageException if the notifications cannot be cleared.
     */
    override suspend fun clearPushNotifications() {
        try {
            clearAllPushNotifications()
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to clear Push notifications", e)
        }
    }
    
    /**
     * Clear all Push notifications from the database.
     *
     * @throws MfaStorageException if the notifications cannot be cleared.
     */
    private suspend fun clearAllPushNotifications() = withContext(Dispatchers.IO) {
        checkDatabase()
        
        try {
            database.delete(NOTIFICATION_TABLE, null, null)
            logger.d("Cleared all Push notifications")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to clear Push notifications: ${e.message}", e)
            throw MfaStorageException("Failed to clear Push notifications", e)
        }
    }
    
    /**
     * Count the number of push notifications.
     *
     * @param credentialId Optional ID of a specific credential to count notifications for.
     * @return The count of push notifications.
     * @throws MfaStorageException if the count cannot be retrieved.
     */
    override suspend fun countPushNotifications(credentialId: String?): Int = withContext(Dispatchers.IO) {
        checkDatabase()

        try {
            val sql = if (credentialId != null) {
                "SELECT COUNT(*) FROM $NOTIFICATION_TABLE WHERE $NOTIFICATION_COLUMN_CREDENTIAL_ID = ?"
            } else {
                "SELECT COUNT(*) FROM $NOTIFICATION_TABLE"
            }

            val args = if (credentialId != null) arrayOf(credentialId) else null

            // Use the query method from the parent class to execute the count query
            val results = query(sql, args) { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }

            val count = results.firstOrNull() ?: 0

            logger.d("Counted $count push notifications${if (credentialId != null) " for credential $credentialId" else ""}")
            return@withContext count
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to count push notifications: ${e.message}", e)
            throw MfaStorageException("Failed to count push notifications", e)
        }
    }

    /**
     * Retrieve the oldest push notifications.
     *
     * @param limit The maximum number of notifications to retrieve.
     * @param credentialId Optional ID of a specific credential to retrieve notifications for.
     * @return A list of the oldest push notifications.
     * @throws MfaStorageException if the notifications cannot be retrieved.
     */
    override suspend fun getOldestPushNotifications(limit: Int, credentialId: String?): List<PushNotification> = withContext(Dispatchers.IO) {
        checkDatabase()

        try {
            val limitClause = if (limit > 0) " LIMIT $limit" else ""

            val sql = if (credentialId != null) {
                "SELECT * FROM $NOTIFICATION_TABLE WHERE $NOTIFICATION_COLUMN_CREDENTIAL_ID = ? ORDER BY $NOTIFICATION_COLUMN_CREATED_AT ASC$limitClause"
            } else {
                "SELECT * FROM $NOTIFICATION_TABLE ORDER BY $NOTIFICATION_COLUMN_CREATED_AT ASC$limitClause"
            }

            val args = if (credentialId != null) arrayOf(credentialId) else null

            val results = query(sql, args) { cursor ->
                extractDataFromCursor(cursor)
            }

            val notifications = results.mapNotNull { createPushNotificationFromData(it) }

            logger.d("Retrieved ${notifications.size} oldest push notifications${if (credentialId != null) " for credential $credentialId" else ""}")
            return@withContext notifications
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to retrieve oldest push notifications: ${e.message}", e)
            throw MfaStorageException("Failed to retrieve oldest push notifications", e)
        }
    }

    /**
     * Purge push notifications by age.
     *
     * @param maxAgeDays The maximum age in days for notifications to keep.
     * @param credentialId Optional ID of a specific credential to purge notifications for.
     * @return The number of notifications removed.
     * @throws MfaStorageException if the notifications cannot be purged.
     */
    override suspend fun purgePushNotificationsByAge(maxAgeDays: Int, credentialId: String?): Int = withContext(Dispatchers.IO) {
        checkDatabase()

        try {
            val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L) // Convert days to milliseconds

            val whereClause = if (credentialId != null) {
                "$NOTIFICATION_COLUMN_CREATED_AT < ? AND $NOTIFICATION_COLUMN_CREDENTIAL_ID = ?"
            } else {
                "$NOTIFICATION_COLUMN_CREATED_AT < ?"
            }

            val whereArgs = if (credentialId != null) {
                arrayOf(cutoffTime.toString(), credentialId)
            } else {
                arrayOf(cutoffTime.toString())
            }

            val deletedRows = database.delete(
                NOTIFICATION_TABLE,
                whereClause,
                whereArgs
            )

            logger.d("Purged $deletedRows push notifications older than $maxAgeDays days${if (credentialId != null) " for credential $credentialId" else ""}")
            return@withContext deletedRows
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to purge push notifications by age: ${e.message}", e)
            throw MfaStorageException("Failed to purge push notifications by age", e)
        }
    }

    /**
     * Purge push notifications by count (removes oldest notifications when count exceeds the limit).
     *
     * @param maxCount The maximum number of notifications to keep.
     * @param credentialId Optional ID of a specific credential to purge notifications for.
     * @return The number of notifications removed.
     * @throws MfaStorageException if the notifications cannot be purged.
     */
    override suspend fun purgePushNotificationsByCount(maxCount: Int, credentialId: String?): Int = withContext(Dispatchers.IO) {
        checkDatabase()

        try {
            if (maxCount < 0) {
                logger.w("Invalid maxCount value ($maxCount), must be non-negative")
                return@withContext 0
            }

            // Get the current count
            val currentCount = countPushNotifications(credentialId)

            // If count is already below or equal to maxCount, no need to purge
            if (currentCount <= maxCount) {
                logger.d("No need to purge, current count ($currentCount) <= max count ($maxCount)")
                return@withContext 0
            }

            // Calculate how many to delete
            val deleteCount = currentCount - maxCount

            // Get the cutoff timestamp (the timestamp of the Nth oldest notification)
            val cutoffTimestamp = if (deleteCount < currentCount) {
                // Find the timestamp of the Nth notification when ordered by created_at
                val sql = if (credentialId != null) {
                    "SELECT $NOTIFICATION_COLUMN_CREATED_AT FROM $NOTIFICATION_TABLE " +
                    "WHERE $NOTIFICATION_COLUMN_CREDENTIAL_ID = ? " +
                    "ORDER BY $NOTIFICATION_COLUMN_CREATED_AT ASC " +
                    "LIMIT 1 OFFSET $deleteCount"
                } else {
                    "SELECT $NOTIFICATION_COLUMN_CREATED_AT FROM $NOTIFICATION_TABLE " +
                    "ORDER BY $NOTIFICATION_COLUMN_CREATED_AT ASC " +
                    "LIMIT 1 OFFSET $deleteCount"
                }

                val args = if (credentialId != null) arrayOf(credentialId) else null
                var timestamp = 0L

                // Use the query method from the parent class to execute the count query
                query(sql, args) { cursor ->
                    if (cursor.moveToFirst()) {
                        timestamp = cursor.getLong(0)
                    }
                }

                timestamp
            } else {
                // If we're deleting all notifications, use current time as cutoff
                System.currentTimeMillis()
            }

            // Delete notifications older than the cutoff timestamp
            val whereClause = if (credentialId != null) {
                "$NOTIFICATION_COLUMN_CREATED_AT < ? AND $NOTIFICATION_COLUMN_CREDENTIAL_ID = ?"
            } else {
                "$NOTIFICATION_COLUMN_CREATED_AT < ?"
            }

            val whereArgs = if (credentialId != null) {
                arrayOf(cutoffTimestamp.toString(), credentialId)
            } else {
                arrayOf(cutoffTimestamp.toString())
            }

            val deletedRows = database.delete(
                NOTIFICATION_TABLE,
                whereClause,
                whereArgs
            )

            logger.d("Purged $deletedRows push notifications to maintain max count of $maxCount${if (credentialId != null) " for credential $credentialId" else ""}")
            return@withContext deletedRows
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to purge push notifications by count: ${e.message}", e)
            throw MfaStorageException("Failed to purge push notifications by count", e)
        }
    }

    /**
     * Extract data from a cursor into a map.
     *
     * @param cursor The cursor to extract data from.
     * @return A map of column names to values.
     */
    private fun extractDataFromCursor(cursor: Cursor): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>()
        
        // Extract column names
        val columnNames = cursor.columnNames
        
        // Extract data for each column
        for (i in 0 until cursor.columnCount) {
            val columnName = columnNames[i]
            val columnType = cursor.getType(i)
            
            // Extract the value based on column type
            val value: Any? = when (columnType) {
                Cursor.FIELD_TYPE_NULL -> null
                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                else -> cursor.getString(i)
            }
            
            data[columnName] = value
        }
        
        return data
    }
    
    /**
     * Create a Push credential from a data map.
     *
     * @param data The data map to create the credential from.
     * @return The created Push credential.
     */
    private fun createPushCredentialFromData(data: Map<String, Any?>): PushCredential? {
        try {
            val id = data[PUSH_COLUMN_ID] as String
            val userId = data[PUSH_COLUMN_USER_ID] as String? ?: ""
            val resourceId = data[PUSH_COLUMN_RESOURCE_ID] as String? ?: ""
            val issuer = data[PUSH_COLUMN_ISSUER] as String
            val displayIssuer = data[PUSH_COLUMN_DISPLAY_ISSUER] as String
            val accountName = data[PUSH_COLUMN_ACCOUNT_NAME] as String
            val displayAccountName = data[PUSH_COLUMN_DISPLAY_ACCOUNT_NAME] as String
            val serverEndpoint = data[PUSH_COLUMN_SERVER_ENDPOINT] as String
            val sharedSecret = data[PUSH_COLUMN_SHARED_SECRET] as String
            val platform = data[PUSH_COLUMN_PLATFORM] as String
            val createdAtMillis = data[PUSH_COLUMN_CREATED_AT] as Long
            val imageURL = data[PUSH_COLUMN_IMAGE_URL] as String?
            val backgroundColor = data[PUSH_COLUMN_BACKGROUND_COLOR] as String?
            val policies = data[PUSH_COLUMN_POLICIES] as String?
            val lockingPolicy = data[PUSH_COLUMN_LOCKING_POLICY] as String?
            val isLockedInt = data[PUSH_COLUMN_IS_LOCKED] as Long?
            
            // Convert long timestamp to Date
            val createdAt = Date(createdAtMillis)
            
            // Convert integer to boolean for isLocked
            val isLocked = isLockedInt == 1L
            
            return PushCredential(
                id = id,
                userId = userId,
                resourceId = resourceId,
                issuer = issuer,
                displayIssuer = displayIssuer,
                accountName = accountName,
                displayAccountName = displayAccountName,
                serverEndpoint = serverEndpoint,
                sharedSecret = sharedSecret,
                platform = platform,
                createdAt = createdAt,
                imageURL = imageURL,
                backgroundColor = backgroundColor,
                policies = policies,
                lockingPolicy = lockingPolicy,
                isLocked = isLocked
            )
        } catch (e: Exception) {
            logger.e("Failed to create Push credential from data: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Create a Push notification from a data map.
     *
     * @param data The data map to create the notification from.
     * @return The created Push notification.
     */
    private fun createPushNotificationFromData(data: Map<String, Any?>): PushNotification? {
        try {
            // Extract the data
            val id = data[NOTIFICATION_COLUMN_ID] as String
            val credentialId = data[NOTIFICATION_COLUMN_CREDENTIAL_ID] as String
            val messageId = data[NOTIFICATION_COLUMN_MESSAGE_ID] as String? ?: ""
            val messageText = data[NOTIFICATION_COLUMN_MESSAGE_TEXT] as String?
            val customPayload = data[NOTIFICATION_COLUMN_CUSTOM_PAYLOAD] as String?
            val challenge = data[NOTIFICATION_COLUMN_CHALLENGE] as String?
            val numbersChallenge = data[NOTIFICATION_COLUMN_NUMBERS_CHALLENGE] as String?
            val amlbCookie = data[NOTIFICATION_COLUMN_AMLB_COOKIE] as String?
            val contextInfo = data[NOTIFICATION_COLUMN_CONTEXT_INFO] as String?
            val pushTypeStr = data[NOTIFICATION_COLUMN_PUSH_TYPE] as String? ?: PushType.DEFAULT.name
            val ttl = (data[NOTIFICATION_COLUMN_TTL] as Long?)?.toInt() ?: 300
            val pending = data[NOTIFICATION_COLUMN_PENDING] as Long? == 1L
            val approved = data[NOTIFICATION_COLUMN_APPROVED] as Long? == 1L
            val createdAtMillis = data[NOTIFICATION_COLUMN_CREATED_AT] as Long
            val sentAtMillis = data[NOTIFICATION_COLUMN_SENT_AT] as Long?
            val respondedAtMillis = data[NOTIFICATION_COLUMN_RESPONDED_AT] as Long?
            val additionalDataJson = data[NOTIFICATION_COLUMN_ADDITIONAL_DATA] as String?

            // Convert long timestamps to Dates
            val createdAt = Date(createdAtMillis)
            val sentAt = sentAtMillis?.let { Date(it) }
            val respondedAt = respondedAtMillis?.let { Date(it) }
            
            // Parse pushType from string
            val pushType = try {
                PushType.fromString(pushTypeStr)
            } catch (e: Exception) {
                PushType.DEFAULT
            }

            // Parse additional data if present
            val additionalData = if (additionalDataJson != null) {
                Json.decodeFromString(PushNotification.AdditionalDataSerializer, additionalDataJson)
            } else {
                null
            }
            
            return PushNotification(
                id = id,
                credentialId = credentialId,
                ttl = ttl,
                messageId = messageId,
                messageText = messageText,
                customPayload = customPayload,
                challenge = challenge,
                numbersChallenge = numbersChallenge,
                loadBalancer = amlbCookie,
                contextInfo = contextInfo,
                pushType = pushType,
                createdAt = createdAt,
                sentAt = sentAt,
                respondedAt = respondedAt,
                additionalData = additionalData,
                approved = approved,
                pending = pending
            )
        } catch (e: Exception) {
            logger.e("Failed to create Push notification from data: ${e.message}", e)
            return null
        }
    }

    /**
     * Extension function to map a key-value map to ContentValues
     */
    private fun mapToContentValues(data: Map<String, Any?>): ContentValues {
        val contentValues = ContentValues()

        data.forEach { (key, value) ->
            when (value) {
                null -> contentValues.putNull(key)
                is String -> contentValues.put(key, value)
                is Int -> contentValues.put(key, value)
                is Long -> contentValues.put(key, value)
                is Double -> contentValues.put(key, value)
                is Float -> contentValues.put(key, value)
                is Boolean -> contentValues.put(key, value)
                is ByteArray -> contentValues.put(key, value)
                else -> contentValues.put(key, value.toString())
            }
        }

        return contentValues
    }
    
    /**
     * Store a push device token.
     *
     * @param token The Push device token to be stored.
     * @throws MfaStorageException if the token cannot be stored.
     */
    override suspend fun storePushDeviceToken(token: PushDeviceToken) {
        try {
            // Mark all existing tokens as not current and store the new one
            updateDeviceTokenStatus()

            // Create data map for the new token
            val data = mapOf(
                TOKEN_COLUMN_ID to token.id,
                TOKEN_COLUMN_TOKEN_ID to token.tokenId,
                TOKEN_COLUMN_CREATED_AT to token.createdAt.time,
                TOKEN_COLUMN_IS_CURRENT to 1
            )

            storeDeviceTokenData(token.id, data)
            logger.d("Push device token stored: ${token.tokenId}")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to store push device token", e)
        }
    }

    /**
     * Update all existing device tokens to not be current.
     *
     * @throws MfaStorageException if the tokens cannot be updated.
     */
    private suspend fun updateDeviceTokenStatus() = withContext(Dispatchers.IO) {
        checkDatabase()

        try {
            // Begin a transaction to ensure data consistency
            beginTransaction()

            try {
                // Mark all existing tokens as not current
                val values = ContentValues().apply {
                    put(TOKEN_COLUMN_IS_CURRENT, 0)
                }
                database.update(DEVICE_TOKEN_TABLE, values, null, null)

                // Mark the transaction as successful
                setTransactionSuccessful()
            } finally {
                // End the transaction
                endTransaction()
            }

            logger.d("Updated device token status")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to update device token status: ${e.message}", e)
            throw MfaStorageException("Failed to update device token status", e)
        }
    }

    /**
     * Retrieve the current push device token.
     *
     * @return The current Push device token, or null if not found.
     * @throws MfaStorageException if the token cannot be retrieved.
     */
    override suspend fun getCurrentPushDeviceToken(): PushDeviceToken? {
        try {
            val data = retrieveCurrentDeviceTokenData() ?: return null
            return createDeviceTokenFromData(data)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to retrieve current push device token", e)
        }
    }

    /**
     * Retrieve the current device token data from the database.
     *
     * @return The token data, or null if not found.
     * @throws MfaStorageException if the token cannot be retrieved.
     */
    private suspend fun retrieveCurrentDeviceTokenData(): Map<String, Any?>? = withContext(Dispatchers.IO) {
        checkDatabase()

        try {
            // Query for the current token
            val sql = "SELECT * FROM $DEVICE_TOKEN_TABLE WHERE $TOKEN_COLUMN_IS_CURRENT = ? LIMIT 1"
            val args = arrayOf("1") // 1 for true in SQLite boolean representation
            val results = query(sql, args) { cursor ->
                extractDataFromCursor(cursor)
            }

            return@withContext results.firstOrNull()
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to retrieve current device token: ${e.message}", e)
            throw MfaStorageException("Failed to retrieve current device token", e)
        }
    }
    
    /**
     * Retrieve all stored push device tokens.
     *
     * @return A list of all Push device tokens.
     * @throws MfaStorageException if the tokens cannot be retrieved.
     */
    override suspend fun getAllPushDeviceTokens(): List<PushDeviceToken> {
        try {
            val dataList = retrieveAllDeviceTokensData()
            return dataList.mapNotNull { createDeviceTokenFromData(it) }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to retrieve all push device tokens", e)
        }
    }

    /**
     * Retrieve all device token data from the database.
     *
     * @return A list of token data.
     * @throws MfaStorageException if the tokens cannot be retrieved.
     */
    private suspend fun retrieveAllDeviceTokensData(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        checkDatabase()

        try {
            // Use the query method from the parent class with the correct format
            val sql = "SELECT * FROM $DEVICE_TOKEN_TABLE ORDER BY $TOKEN_COLUMN_CREATED_AT DESC"
            return@withContext query(sql, null) { cursor ->
                extractDataFromCursor(cursor)
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to retrieve all device tokens: ${e.message}", e)
            throw MfaStorageException("Failed to retrieve all device tokens", e)
        }
    }

    /**
     * Clear all Push device tokens from the storage.
     *
     * @throws MfaStorageException if the tokens cannot be cleared.
     */
    override suspend fun clearPushDeviceTokens() {
        try {
            clearAllDeviceTokens()
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            throw MfaStorageException("Failed to clear push device tokens", e)
        }
    }

    /**
     * Clear all device tokens from the database.
     *
     * @throws MfaStorageException if the tokens cannot be cleared.
     */
    private suspend fun clearAllDeviceTokens() = withContext(Dispatchers.IO) {
        checkDatabase()

        try {
            database.delete(DEVICE_TOKEN_TABLE, null, null)
            logger.d("Cleared all push device tokens")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to clear push device tokens: ${e.message}", e)
            throw MfaStorageException("Failed to clear push device tokens", e)
        }
    }

    /**
     * Store push device token data in the database.
     *
     * @param tokenId The ID of the token.
     * @param data The token data.
     * @throws MfaStorageException if the token cannot be stored.
     */
    private suspend fun storeDeviceTokenData(tokenId: String, data: Map<String, Any?>) = withContext(Dispatchers.IO) {
        checkDatabase()

        try {
            // Begin a transaction to ensure data consistency
            beginTransaction()

            try {
                // Insert or update the token
                val existingToken = retrieveDeviceTokenData(tokenId)
                if (existingToken != null) {
                    // Update existing token
                    val whereClause = "$TOKEN_COLUMN_ID = ?"
                    val whereArgs = arrayOf(tokenId)
                    database.update(DEVICE_TOKEN_TABLE, mapToContentValues(data), whereClause, whereArgs)
                    logger.d("Updated push device token with ID: $tokenId")
                } else {
                    // Insert new token
                    database.insert(DEVICE_TOKEN_TABLE, null, mapToContentValues(data))
                    logger.d("Inserted new push device token with ID: $tokenId")
                }

                // Mark the transaction as successful
                setTransactionSuccessful()
            } finally {
                // End the transaction
                endTransaction()
            }

            logger.d("Stored push device token with ID: $tokenId")
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to store push device token with ID $tokenId: ${e.message}", e)
            throw MfaStorageException("Failed to store push device token with ID $tokenId", e)
        }
    }

    /**
     * Retrieve device token data from the database.
     *
     * @param tokenId The ID of the token to retrieve.
     * @return The token data, or null if not found.
     * @throws MfaStorageException if the token cannot be retrieved.
     */
    private suspend fun retrieveDeviceTokenData(tokenId: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        checkDatabase()

        try {
            // Use the query method from the parent class with the correct format
            val sql = "SELECT * FROM $DEVICE_TOKEN_TABLE WHERE $TOKEN_COLUMN_ID = ?"
            val args = arrayOf(tokenId)
            val results = query(sql, args) { cursor ->
                extractDataFromCursor(cursor)
            }

            return@withContext results.firstOrNull()
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            logger.e("Failed to retrieve push device token with ID $tokenId: ${e.message}", e)
            throw MfaStorageException("Failed to retrieve push device token with ID $tokenId", e)
        }
    }

    /**
     * Create a device token from a data map.
     *
     * @param data The data map to create the device token from.
     * @return The created device token.
     */
    private fun createDeviceTokenFromData(data: Map<String, Any?>): PushDeviceToken? {
        try {
            val id = data[TOKEN_COLUMN_ID] as String
            val tokenId = data[TOKEN_COLUMN_TOKEN_ID] as String
            val createdAtMillis = data[TOKEN_COLUMN_CREATED_AT] as Long

            // Convert long timestamp to Date
            val createdAt = Date(createdAtMillis)

            return PushDeviceToken(
                id = id,
                tokenId = tokenId,
                createdAt = createdAt
            )
        } catch (e: Exception) {
            logger.e("Failed to create push device token from data: ${e.message}", e)
            return null
        }
    }
}
