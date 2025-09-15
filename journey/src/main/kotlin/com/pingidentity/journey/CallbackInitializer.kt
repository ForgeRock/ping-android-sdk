/*
 * Copyright (c) 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.journey

import android.content.Context
import androidx.startup.Initializer
import com.pingidentity.journey.callback.BooleanAttributeInputCallback
import com.pingidentity.journey.callback.ChoiceCallback
import com.pingidentity.journey.callback.ConfirmationCallback
import com.pingidentity.journey.callback.ConsentMappingCallback
import com.pingidentity.journey.callback.HiddenValueCallback
import com.pingidentity.journey.callback.KbaCreateCallback
import com.pingidentity.journey.callback.MetadataCallback
import com.pingidentity.journey.callback.NameCallback
import com.pingidentity.journey.callback.NumberAttributeInputCallback
import com.pingidentity.journey.callback.PasswordCallback
import com.pingidentity.journey.callback.PollingWaitCallback
import com.pingidentity.journey.callback.StringAttributeInputCallback
import com.pingidentity.journey.callback.SuspendedTextOutputCallback
import com.pingidentity.journey.callback.TermsAndConditionsCallback
import com.pingidentity.journey.callback.TextInputCallback
import com.pingidentity.journey.callback.TextOutputCallback
import com.pingidentity.journey.callback.ValidatedPasswordCallback
import com.pingidentity.journey.callback.ValidatedUsernameCallback
import com.pingidentity.journey.plugin.CallbackRegistry

/**
 * This class is responsible for registering callbacks in the application.
 * It extends the Initializer interface, which means it is part of the initialization process of the application.
 */
class CallbackInitializer : Initializer<CallbackRegistry> {

    override fun create(context: Context): CallbackRegistry {
        CallbackRegistry.register("BooleanAttributeInputCallback", ::BooleanAttributeInputCallback)
        CallbackRegistry.register("ChoiceCallback", ::ChoiceCallback)
        CallbackRegistry.register("ConfirmationCallback", ::ConfirmationCallback)
        CallbackRegistry.register("ConsentMappingCallback", ::ConsentMappingCallback)
        CallbackRegistry.register("HiddenValueCallback", ::HiddenValueCallback)
        CallbackRegistry.register("KbaCreateCallback", ::KbaCreateCallback)
        CallbackRegistry.register("MetadataCallback", ::MetadataCallback)
        CallbackRegistry.register("NameCallback", ::NameCallback)
        CallbackRegistry.register("NumberAttributeInputCallback", ::NumberAttributeInputCallback)
        CallbackRegistry.register("PasswordCallback", ::PasswordCallback)
        CallbackRegistry.register("PollingWaitCallback", ::PollingWaitCallback)
        CallbackRegistry.register("StringAttributeInputCallback", ::StringAttributeInputCallback)
        CallbackRegistry.register("SuspendedTextOutputCallback", ::SuspendedTextOutputCallback)
        CallbackRegistry.register("TermsAndConditionsCallback", ::TermsAndConditionsCallback)
        CallbackRegistry.register("TextInputCallback", ::TextInputCallback)
        CallbackRegistry.register("TextOutputCallback", ::TextOutputCallback)
        CallbackRegistry.register("ValidatedCreatePasswordCallback", ::ValidatedPasswordCallback)
        CallbackRegistry.register("ValidatedCreateUsernameCallback", ::ValidatedUsernameCallback)
        return CallbackRegistry
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}