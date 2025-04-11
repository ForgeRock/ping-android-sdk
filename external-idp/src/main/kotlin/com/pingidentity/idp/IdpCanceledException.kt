/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp

/**
 * An exception that is thrown when the Identity Provider authentication is canceled.
 */
class IdpCanceledException: RuntimeException("Identity Provider authentication was canceled")