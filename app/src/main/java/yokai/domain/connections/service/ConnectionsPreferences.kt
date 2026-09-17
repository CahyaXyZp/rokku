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

    companion object {
        fun connectionsUsername(syncId: Long) = Preference.privateKey("pref_connections_username_$syncId")

        private fun connectionsPassword(syncId: Long) = Preference.privateKey("pref_connections_password_$syncId")

        private fun connectionsToken(syncId: Long) = Preference.privateKey("connection_token_$syncId")
    }
}
