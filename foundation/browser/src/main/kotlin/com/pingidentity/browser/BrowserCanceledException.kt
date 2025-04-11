/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.browser

/**
 * An exception that is thrown when the browser is canceled.
 */
class BrowserCanceledException: RuntimeException("Browser was canceled")