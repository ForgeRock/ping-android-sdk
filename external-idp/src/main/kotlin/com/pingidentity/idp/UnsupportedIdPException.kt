/*
 * Copyright (c) 2024 PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.idp

/**
 * Exception thrown when an unsupported Identity Provider (IdP) is encountered.
 *
 * @param msg The detail message for the exception.
 */
class UnsupportedIdPException(msg: String): Exception(msg)