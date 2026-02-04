/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.pingonemfa.commons
/*
 * Wrapper for error message into exception class
 */
class PingOneMFAException(message: String?) : Exception(message)