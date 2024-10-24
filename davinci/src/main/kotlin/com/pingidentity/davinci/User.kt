/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.davinci

import com.pingidentity.davinci.module.oidcClientConfig
import com.pingidentity.davinci.plugin.DaVinci
import com.pingidentity.oidc.OidcUser
import com.pingidentity.oidc.User
import com.pingidentity.orchestrate.EmptySession
import com.pingidentity.orchestrate.Session
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.orchestrate.module.hasCookies

private const val USER = "com.pingidentity.davinci.User"

/**
 * Function to retrieve the user.
 *
 * If cookies are available, it prepares a new user and returns it.
 * If no user is found and no cookies are available, it returns null.
 *
 * @return The user if found, otherwise null.
 */
suspend fun DaVinci.user(): User? {
    init()

    // Retrieve the cached user from the context
    sharedContext.getValue<User>(USER)?.let {
        return it
    }

    if (hasCookies()) {
        return prepareUser(this, OidcUser(oidcClientConfig()))
    }

    return null
}

/**
 * Alias for the DaVinci.user() function.
 * @return The user if found, otherwise null.
 */
suspend fun DaVinci.davinciUser(): User? = this.user()

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
 * @param daVinci The DaVinci instance.
 * @param user The user.
 * @param session The session.
 * @return The prepared user.
 */
internal fun prepareUser(
    daVinci: DaVinci,
    user: User,
    session: Session = EmptySession
): UserDelegate {
    return UserDelegate(daVinci, user, session).also {
        // Cache the user in the context
        daVinci.sharedContext[USER] = it
    }
}

/**
 * Class representing a UserDelegate.
 *
 * This class is a delegate for the User and Session interfaces.
 * It overrides the logout function to remove the cached user from the context and sign off the user.
 *
 * @property daVinci The DaVinci instance.
 * @property user The user.
 * @property session The session.
 */
internal class UserDelegate(
    private val daVinci: DaVinci,
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
        daVinci.sharedContext.remove(USER)
        // instead of calling [OidcClient.endSession] directly, we call [DaVinci.signOff] to signoff the user
        daVinci.signOff()
    }
}