package com.pingidentity.pingonemfa.commons

import com.google.gson.JsonObject

data class PingOneMfaAccount(
    val region: String,
    val id: String,
    val deviceId: String,
    val environment: String,
    val name: String,
    val family: String
)

class AccountParser {
    fun parseAccounts(json: JsonObject): List<PingOneMfaAccount> {
        val result = mutableListOf<PingOneMfaAccount>()

        json.entrySet().forEach { (region, regionElement) ->
            val users = regionElement.asJsonObject
                .getAsJsonArray("users")
                ?.mapNotNull { it.asJsonObject }
                ?: emptyList()

            users.forEach { user ->
                result += PingOneMfaAccount(
                    region = region,
                    id = user.get("id")?.asString ?: "",
                    environment = user.getAsJsonObject("environment")?.get("id")?.asString ?: "",
                    deviceId = user.getAsJsonObject("device")?.get("id")?.asString ?: "",
                    name = user.getAsJsonObject("name")?.get("given")?.asString ?: "",
                    family = user.getAsJsonObject("name")?.get("family")?.asString ?: ""
                )
            }
        }
        return result
    }
}