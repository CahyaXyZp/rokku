package eu.kanade.tachiyomi.data.connections.discord

import android.graphics.Color
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    fun removeAccount(accountId: String) {
        val accounts = getAccounts().toMutableList()
        accounts.removeAll { it.id == accountId }
        saveAccounts(accounts)
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

    fun restartRichPresence() {
        connectionsPreferences.enableDiscordRPC().set(false)
        connectionsPreferences.enableDiscordRPC().set(true)
    }

    private fun saveAccounts(accounts: List<DiscordAccount>) {
        try {
            val accountsJson = json.encodeToString(accounts)
            connectionsPreferences.discordAccounts().set(accountsJson)
        } catch (e: Exception) {
            Logger.e("Discord") { "Failed to save Discord accounts: ${e.message}" }
        }
    }
}
