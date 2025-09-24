/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.mfa.commons.exception

/**
 * Base exception class for all MFA-related exceptions.
 *
 * @param message The error message.
 * @param cause The underlying cause of the exception.
 */
open class MfaException(
    message: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception thrown when an MFA client initialization fails.
 *
 * @param message The error message.
 * @param cause The underlying cause of the exception.
 */
class MfaInitializationException(
    message: String? = null,
    cause: Throwable? = null
) : MfaException(message, cause)

/**
 * Exception thrown when an MFA operation is performed on an uninitialized client.
 *
 * @param message The error message.
 * @param cause The underlying cause of the exception.
 */
class MfaClientNotInitializedException(
    message: String? = "MFA client not initialized",
    cause: Throwable? = null
) : MfaException(message, cause)

/**
 * Exception thrown when an MFA storage operation fails.
 *
 * @param message The error message.
 * @param cause The underlying cause of the exception.
 */
class MfaStorageException(
    message: String? = null,
    cause: Throwable? = null
) : MfaException(message, cause)

/**
 * Exception thrown when attempting to perform operations on a locked credential.
 * 
 * This exception is thrown when a credential is locked due to policy violations
 * and the user attempts to perform operations that require an unlocked credential.
 * 
 * @param policyName The name of the policy that caused the credential to be locked.
 * @param reason Optional human-readable reason for the lock.
 * @param cause The underlying cause of the exception.
 */
class CredentialLockedException(
    val policyName: String,
    val reason: String? = null,
    cause: Throwable? = null
) : MfaException(
    message = buildErrorMessage(policyName, reason),
    cause = cause
) {
    companion object {
        private fun buildErrorMessage(policyName: String, reason: String?): String {
            return if (reason != null) {
                "Credential is locked due to policy '$policyName': $reason"
            } else {
                "Credential is locked due to policy '$policyName'"
            }
        }
    }
}

/**
 * Exception thrown when a credential violates authenticator policies during registration or evaluation.
 * 
 * This exception is thrown when policies are evaluated and found to be non-compliant,
 * preventing credential registration or operation.
 * 
 * @param message The error message.
 * @param policy The policy that caused the violation, if available.
 * @param cause The underlying cause of the exception.
 */
class MfaPolicyViolationException(
    message: String? = null,
    val policy: com.pingidentity.mfa.commons.policy.MfaPolicy? = null,
    cause: Throwable? = null
) : MfaException(message, cause)
