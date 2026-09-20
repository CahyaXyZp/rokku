package eu.kanade.tachiyomi.data.connections.discord

import android.app.Application
import android.graphics.Color
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.i18n.MR

class Discord(id: Long) : ConnectionsService(id) {

    private val json = Injekt.get<Json>()

    override fun nameRes() = MR.strings.connections_discord

    override fun getLogo() = R.drawable.ic_discord_24dp

    override fun getLogoColor() = Color.rgb(88, 101, 242)

    override fun logout() {
        super.logout()
        connectionsPreferences.connectionsToken(this).delete()
    }

    override suspend fun login(username: String, password: String) {
        // Not needed, Discord RPC authenticates via an account token instead
    }

    /**
     * Validates [token] against the Discord API and returns the associated account profile, or
     * null if the token is invalid or the request fails. The first account ever added is marked
     * active by default.
     */
    suspend fun fetchProfile(token: String): DiscordAccount? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://discord.com/api/v10/users/@me")
                .addHeader("Authorization", token)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val user = json.parseToJsonElement(body).jsonObject
                val id = user["id"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
                val username = user["username"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
                val avatar = user["avatar"]?.jsonPrimitive?.contentOrNull
                DiscordAccount(
                    id = id,
                    username = username,
                    avatarUrl = avatar?.let { "https://cdn.discordapp.com/avatars/$id/$it.png" },
                    token = token,
                    isActive = getAccounts().isEmpty(),
                )
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to fetch Discord profile" }
            null
        }
    }

    fun getAccounts(): List<DiscordAccount> {
        val accountsJson = connectionsPreferences.discordAccounts().get()
        return try {
            if (accountsJson.isNotBlank()) {
                json.decodeFromString<List<DiscordAccount>>(accountsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addAccount(account: DiscordAccount) {
        val accounts = getAccounts().toMutableList()

        if (account.isActive) {
            accounts.replaceAll { it.copy(isActive = false) }
            connectionsPreferences.connectionsToken(this).set(account.token)
        }

        val index = accounts.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            accounts[index] = account
        } else {
            accounts.add(account)
        }

        saveAccounts(accounts)
    }

    /**
     * Removes the account, clearing the stored token and stopping the RPC service if it was the
     * active one - otherwise the stale token would be left behind and RPC would keep failing to
     * reconnect with credentials that no longer correspond to any saved account.
     */
    fun removeAccount(accountId: String) {
        val accounts = getAccounts().toMutableList()
        val removed = accounts.find { it.id == accountId }
        accounts.removeAll { it.id == accountId }
        saveAccounts(accounts)

        if (removed?.isActive == true) {
            connectionsPreferences.connectionsToken(this).delete()
            DiscordRPCService.stop(Injekt.get<Application>())
        }
    }

    fun setActiveAccount(accountId: String) {
        val accounts = getAccounts().toMutableList()
        accounts.replaceAll { it.copy(isActive = it.id == accountId) }
        saveAccounts(accounts)
        accounts.find { it.id == accountId }?.let { account ->
            connectionsPreferences.connectionsToken(this).set(account.token)
            restartRichPresence()
        }
    }

    /**
     * Restarts the RPC service so it picks up the newly active account's token. No-ops when
     * Rich Presence is currently disabled or no token is set - handled inside
     * [DiscordRPCService.restart] itself.
     */
    fun restartRichPresence() {
        DiscordRPCService.restart(Injekt.get<Application>())
    }

    private fun saveAccounts(accounts: List<DiscordAccount>) {
        try {
            val accountsJson = json.encodeToString(accounts)
            connectionsPreferences.discordAccounts().set(accountsJson)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to save Discord accounts" }
        }
    }
}
