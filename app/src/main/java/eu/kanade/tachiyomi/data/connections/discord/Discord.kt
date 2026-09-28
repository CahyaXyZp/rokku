package eu.kanade.tachiyomi.data.connections.discord

import android.app.Application
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

    // Accounts saved by older builds still carry fields that no longer exist (authMethod, isActive).
    private val accountJson = Json(json) { ignoreUnknownKeys = true }

    override fun nameRes() = MR.strings.connections_discord

    override fun getLogo() = R.drawable.ic_discord_24dp

    override fun getLogoColor() = Color.rgb(88, 101, 242)

    /** Removes the saved account and stops Rich Presence. */
    override fun logout() {
        super.logout()
        connectionsPreferences.discordAccounts().delete()
        DiscordRPCService.stop(Injekt.get<Application>())
    }

    override suspend fun login(username: String, password: String) {
        // Not needed, Discord RPC signs in through the Social SDK instead
    }

    /**
     * The saved account, or null when logged out. Older builds could save several; the last one
     * saved is used.
     */
    fun getAccount(): DiscordAccount? {
        val accountsJson = connectionsPreferences.discordAccounts().get()
        if (accountsJson.isBlank()) return null
        return try {
            accountJson.decodeFromString<List<DiscordAccount>>(accountsJson).lastOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun saveAccount(account: DiscordAccount) {
        try {
            connectionsPreferences.discordAccounts().set(json.encodeToString(listOf(account)))
        } catch (e: Exception) {
            Logger.e(e) { "Failed to save Discord account" }
        }
    }

    /**
     * Applies [transform] to the saved account's settings, persists the result and restarts RPC
     * so it picks the change up. Used by the account settings screen so each setting only has to
     * describe the fields it owns.
     */
    fun updateAccount(transform: (DiscordAccount) -> DiscordAccount) {
        val account = getAccount() ?: return
        saveAccount(transform(account))
        restartRichPresence()
    }

    /**
     * One-time upgrade path from when the activity name/app-icon/respect-incognito settings
     * were global instead of stored on the account: copies the old global values onto the saved
     * account so nobody's existing setup silently changes, then marks itself done so a change
     * made afterwards is never overwritten.
     */
    fun migrateLegacyActivitySettingsIfNeeded() {
        if (connectionsPreferences.discordAccountSettingsMigrated().get()) return

        val legacyName = connectionsPreferences.discordCustomActivityName().get()
        val legacyShowAppIcon = connectionsPreferences.discordShowAppIcon().get()
        val legacyRespectIncognito = connectionsPreferences.discordRespectIncognito().get()
        getAccount()?.let {
            saveAccount(
                it.copy(
                    customActivityName = legacyName,
                    showAppIcon = legacyShowAppIcon,
                    respectIncognito = legacyRespectIncognito,
                ),
            )
        }

        connectionsPreferences.discordAccountSettingsMigrated().set(true)
    }

    /**
     * Restarts the RPC connection so it picks up the current account's token. No-ops when Rich
     * Presence is currently disabled or no account is saved - handled inside
     * [DiscordRPCService.restart] itself.
     */
    fun restartRichPresence() {
        DiscordRPCService.restart(Injekt.get<Application>())
    }
}
