/*
 * Copyright (c) 2024. PingIdentity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import com.pingidentity.exception.catchOrNull
import com.pingidentity.journey.module.oidcClientConfig
import com.pingidentity.journey.module.session
import com.pingidentity.oidc.OidcUser
import com.pingidentity.oidc.User

private const val USER = "com.pingidentity.journey.User"

/**
 * Function to retrieve the user.
 *
 * If cookies are available, it prepares a new user and returns it.
 * If no user is found and no cookies are available, it returns null.
 *
 * @return The user if found, otherwise null.
 */
suspend fun Journey.user(): User? {
    return catchOrNull {
        init()
        // Retrieve the cached user from the context
        sharedContext.getValue<User>(USER)?.let {
            return it
        }

        session()?.let {
            return prepareUser(this, OidcUser(oidcClientConfig()), it)
        } ?: return null
    }
}

fun User.session(): SSOToken {
    return (this as SSOToken)
}

suspend fun Journey.session(): SSOToken? {
    return session()
}


/**
 * Function to prepare the user.
 *
 * This function creates a new UserDelegate instance and caches it in the context.
 *
 * @param journey The Journey instance.
 * @param user The user.
 * @param session The session.
 * @return The prepared user.
 */
internal fun prepareUser(
    journey: Journey,
    user: User,
    session: SSOToken
): UserDelegate {
    return UserDelegate(journey, user, session).also {
        // Cache the user in the context
        journey.sharedContext[USER] = it
    }
}

/**
 * Class representing a UserDelegate.
 *
 * This class is a delegate for the User and Session interfaces.
 * It overrides the logout function to remove the cached user from the context and sign off the user.
 *
 * @property journey The Journey instance.
 * @property user The user.
 * @property session The session.
 */
internal class UserDelegate(
    private val journey: Journey,
    private val user: User,
    private val session: SSOToken,
) : User by user, SSOToken by session {

    /**
     * Function to log out the user.
     *
     * This function removes the cached user from the context and signs off the user.
     */
    override suspend fun logout() {
        // instead of calling [OidcClient.endSession] directly, we call [DaVinci.signOff] to signoff the user
        journey.sharedContext.remove(USER)
        journey.signOff()
    }
}