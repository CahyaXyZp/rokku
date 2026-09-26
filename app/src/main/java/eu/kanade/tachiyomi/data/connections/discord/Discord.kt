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
     * null if the token is invalid or the request fails. Only one account can be active at a
     * time, so the newly added account always becomes the active one.
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
                    isActive = true,
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

    /**
     * Updates [accountId]'s Rich Presence activity name/app-icon badge/incognito behavior.
     * Restarts RPC only when the edited account is the active one - editing an inactive
     * account's settings shouldn't interrupt whatever's currently running.
     */
    fun updateAccountSettings(
        accountId: String,
        customActivityName: String,
        showAppIcon: Boolean,
        respectIncognito: Boolean,
    ) {
        val accounts = getAccounts().toMutableList()
        val index = accounts.indexOfFirst { it.id == accountId }
        if (index < 0) return

        val updated = accounts[index].copy(
            customActivityName = customActivityName,
            showAppIcon = showAppIcon,
            respectIncognito = respectIncognito,
        )
        accounts[index] = updated
        saveAccounts(accounts)

        if (updated.isActive) restartRichPresence()
    }

    /**
     * One-time upgrade path from when the activity name/app-icon/respect-incognito settings
     * were global instead of per-account: copies the old global values onto every saved account
     * so nobody's existing setup silently changes, then marks itself done so a per-account
     * change made afterwards is never overwritten.
     */
    fun migrateLegacyActivitySettingsIfNeeded() {
        if (connectionsPreferences.discordAccountSettingsMigrated().get()) return

        val legacyName = connectionsPreferences.discordCustomActivityName().get()
        val legacyShowAppIcon = connectionsPreferences.discordShowAppIcon().get()
        val legacyRespectIncognito = connectionsPreferences.discordRespectIncognito().get()
        val accounts = getAccounts()
        if (accounts.isNotEmpty()) {
            saveAccounts(
                accounts.map {
                    it.copy(
                        customActivityName = legacyName,
                        showAppIcon = legacyShowAppIcon,
                        respectIncognito = legacyRespectIncognito,
                    )
                },
            )
        }

        connectionsPreferences.discordAccountSettingsMigrated().set(true)
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
