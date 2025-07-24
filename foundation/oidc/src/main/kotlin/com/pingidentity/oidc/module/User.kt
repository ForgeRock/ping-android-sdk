/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.oidc.module

import com.pingidentity.oidc.OidcUser
import com.pingidentity.oidc.User
import com.pingidentity.orchestrate.EmptySession
import com.pingidentity.orchestrate.Session
import com.pingidentity.orchestrate.SuccessNode
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

private const val USER = "com.pingidentity.oidc.User"

/**
 * Function to retrieve the user.
 *
 * @param lookup A suspend function that takes an OidcFlow and returns a [User]
 * @return The user if found, otherwise null.
 */
suspend fun OidcFlow.oidcUser(lookup: suspend OidcFlow.() -> User? = { prepareUser() }): User? {
    try {
        init()
    } catch (e: Exception) {
        coroutineContext.ensureActive()
        config.logger.e("Failed to initialize the OidcFlow Engine", e)
    }

    // Retrieve the cached user from the context
    sharedContext.getValue<User>(USER)?.let {
        return it
    }

    return lookup(this)
}

internal fun OidcFlow.oidcUser(): User {
    return OidcUser(this.oidcClientConfig())
}

/**
 * Extension property for Success to cast the [SuccessNode.session] to a User.
 */
val SuccessNode.user: User
    get() = session as User

/**
 * Function to prepare the user.
 *
 * This function creates a new UserDelegate instance and caches it in the context.
 *
 * @param user The user.
 * @param session The session.
 * @return The prepared user.
 */
internal fun OidcFlow.prepareUser(
    user: User = OidcUser(this.oidcClientConfig()),
    session: Session = EmptySession
): UserDelegate {
    return UserDelegate(this, user, session).also {
        // Cache the user in the context
        this.sharedContext[USER] = it
    }
}

/**
 * Class representing a UserDelegate.
 *
 * This class is a delegate for the User and Session interfaces.
 * It overrides the logout function to remove the cached user from the context and sign off the user.
 *
 * @property oidcFlow The Workflow instance.
 * @property user The user.
 * @property session The session.
 */
class UserDelegate(
    private val oidcFlow: OidcFlow,
    private val user: User,
    private val session: Session,
) : User by user, Session by session {

    /**
     * Function to log out the user.
     *
     * This function removes the cached user from the context and signs off the user.
     */
    override suspend fun logout() {
        // remove the cached user from the context
        oidcFlow.sharedContext.remove(USER)
        // instead of calling [OidcClient.endSession] directly, we call [OidcFlow.signOff] to signoff the user
        oidcFlow.signOff()
    }
}
