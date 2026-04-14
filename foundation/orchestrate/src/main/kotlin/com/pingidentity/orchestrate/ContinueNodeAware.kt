/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.orchestrate

/**
 * An interface that should be implemented by classes that need to be aware of the ContinueNode.
 * The continueNode will be injected to the classes that implement this interface.
 */
interface ContinueNodeAware {
    var continueNode: ContinueNode
}