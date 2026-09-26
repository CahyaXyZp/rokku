package yokai.domain.connections.service

import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.data.connections.ConnectionsService

class ConnectionsPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun connectionsUsername(sync: ConnectionsService) = preferenceStore.getString(
        connectionsUsername(sync.id),
        "",
    )

    fun connectionsPassword(sync: ConnectionsService) = preferenceStore.getString(
        connectionsPassword(sync.id),
        "",
    )

    fun setConnectionsCredentials(sync: ConnectionsService, username: String, password: String) {
        connectionsUsername(sync).set(username)
        connectionsPassword(sync).set(password)
    }

    fun connectionsToken(sync: ConnectionsService) = preferenceStore.getString(connectionsToken(sync.id), "")

    fun enableDiscordRPC() = preferenceStore.getBoolean("pref_enable_discord_rpc", false)

    fun discordAccounts() = preferenceStore.getString(Preference.privateKey("discord_accounts"), "")

    // True once Discord.migrateLegacyActivitySettingsIfNeeded() has copied the two legacy prefs
    // below onto every saved account, so it only ever runs once.
    fun discordAccountSettingsMigrated() = preferenceStore.getBoolean("pref_discord_account_settings_migrated", false)

    // Legacy: activity name/app-icon used to be global instead of per-account. Kept only so
    // migrateLegacyActivitySettingsIfNeeded() has something to read; nothing writes to these
    // anymore.
    fun discordCustomActivityName() = preferenceStore.getString("pref_discord_custom_activity_name", "")

    fun discordShowAppIcon() = preferenceStore.getBoolean("pref_discord_show_app_icon", true)

    // Default true matches the old hardcoded behavior - Rich Presence has always been skipped
    // under Incognito Mode, this just makes that toggleable instead of forced.
    fun discordRespectIncognito() = preferenceStore.getBoolean("pref_discord_respect_incognito", true)

    companion object {
        fun connectionsUsername(syncId: Long) = Preference.privateKey("pref_connections_username_$syncId")

        private fun connectionsPassword(syncId: Long) = Preference.privateKey("pref_connections_password_$syncId")

        private fun connectionsToken(syncId: Long) = Preference.privateKey("connection_token_$syncId")
    }
}
