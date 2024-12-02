/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.utils

import kotlin.coroutines.cancellation.CancellationException

/**
 * Sealed class representing a Result, which can be either a Success or a Failure.
 *
 * @param Success The type of the value contained in a Success.
 * @param Failure The type of the value contained in a Failure.
 */
sealed interface Result<out Success, out Failure> {

    /**
     * Data class representing a Failure.
     *
     * @property value The value contained in the Failure.
     */
    data class Failure<Failure> (val value: Failure) : Result<Nothing, Failure>

    /**
     * Data class representing a Success.
     *
     * @property value The value contained in the Success.
     */
    data class Success<Success> (val value: Success) : Result<Success, Nothing>

    companion object {
        /**
         * Inline function to catch exceptions from a block of code and return a Result.
         *
         * @param block The block of code to execute.
         * @return A Result, which is a Success containing the return value of the block if it completes normally,
         * or a Failure containing the exception if an exception is thrown.
         */
        inline fun <Success> catch(block: () -> Success): Result<Success, Throwable> {
            return try {
                Success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Error) {
                throw e
            } catch (e: Throwable) {
                Failure(e)
            }
        }
    }
}